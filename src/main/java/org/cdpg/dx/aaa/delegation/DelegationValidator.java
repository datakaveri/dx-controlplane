package org.cdpg.dx.aaa.delegation;
import io.vertx.core.*;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.delegation.util.RoleScopeMapping;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.item.util.GetItemRequest;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static io.vertx.core.Future.succeededFuture;

public class DelegationValidator {

  private static final Logger LOGGER = LoggerFactory.getLogger(DelegationValidator.class);

  private final OrganizationService organizationService;
  private final ItemService itemService;

  public DelegationValidator(OrganizationService organizationService, ItemService itemService) {
    this.organizationService = organizationService;
    this.itemService = itemService;
  }

  /**
   * Validate all constraints against the delegator’s roles.
   */
  public Future<Void> validateAllConstraints(Set<String> delegatorRoles, List<JsonObject> constraintsJson) {
    LOGGER.info("Validating constraints: {}", constraintsJson);

    if (delegatorRoles.contains("cos_admin")) {
      return succeededFuture();
    }

    String delegatorRole = getHighestRole(delegatorRoles);

    List<String> requestedScopes = constraintsJson.stream()
      .map(c -> c.getString("scope"))
      .map(String::toLowerCase)
      .toList();

    RoleScopeMapping delegatorMapping = RoleScopeMapping.fromString(delegatorRole);
    List<String> allowedScopes = delegatorMapping.getAllowedScopes();

    for (String scope : requestedScopes) {
      if (!allowedScopes.contains(scope)) {
        return Future.failedFuture(
          new DxForbiddenException(
            String.format("Delegator with role '%s' cannot delegate unauthorized or higher scope '%s'",
              delegatorRole, scope)
          )
        );
      }
    }

    LOGGER.info("Scope validation successful!");
    return succeededFuture();
  }

  /**
   * Validate ownership of entities based on delegator’s role.
   */
  public Future<Void> validateEntityOwnership(
    UUID delegatorId,
    Set<String> delegatorRoles,
    List<JsonObject> constraintsJson) {

    if (delegatorRoles.contains("cos_admin")) {
      LOGGER.info("Delegator is cos_admin — skipping entity ownership validation");
      return succeededFuture();
    }

    String delegatorRole = getHighestRole(delegatorRoles);
    LOGGER.info("Entity ownership validation started for role: {}", delegatorRole);

    List<Future> validationFutures = new ArrayList<>();

    for (JsonObject constraint : constraintsJson) {
      String scope = constraint.getString("scope");
      List<String> entityIds = constraint.getJsonArray("entity_id")
        .stream()
        .map(Object::toString)
        .toList();

       if (scope.equalsIgnoreCase("credit_management")) {
        continue;
      }

      switch (delegatorRole.toLowerCase()) {
        case "org_admin" -> {
          if (scope.equalsIgnoreCase("org_management")) {
            validationFutures.add(validateOrgOwnership(delegatorId, entityIds));
          } else if (scope.equalsIgnoreCase("provider_management")) {
            validationFutures.add(validateProviderRequestOwnership(delegatorId, entityIds));
          }
          else if(scope.equalsIgnoreCase("asset_management"))
          {
            validationFutures.add(validateAssetRequestOwnership(delegatorId,entityIds));
          }
          else if(scope.equalsIgnoreCase("data_access"))
          {
            validationFutures.add(validateItemIdOwnership(delegatorId,entityIds));
          }
        }
        case "consumer" -> {
          if (scope.equalsIgnoreCase("data_access")) {
            validationFutures.add(validateItemIdOwnership(delegatorId, entityIds));
          }
        }
        default -> {
          return Future.failedFuture(
            new DxForbiddenException("Delegator role '" + delegatorRole +
              "' not authorized to delegate scope '" + scope + "'"));
        }
      }
    }

    if (validationFutures.isEmpty()) {
      LOGGER.info("No validations required — all scopes bypassed or validated");
      return succeededFuture();
    }

    return CompositeFuture.all(validationFutures).mapEmpty();
  }


  private Future<Void> validateAssetRequestOwnership(UUID delegatorId, List<String> itemIds) {

    LOGGER.info("Validating every asset ownership!");
    if (itemIds == null || itemIds.isEmpty()) {
      return Future.failedFuture(new DxForbiddenException("No asset IDs provided"));
    }

    //  get organization info for delegator
    return organizationService.getOrganizationUserInfo(delegatorId).compose(orgInfo -> {
      UUID orgDelId = orgInfo.organizationId();
      String orgDelIdStr = orgDelId.toString();
      if (orgDelId == null) {
        return Future.failedFuture(new DxForbiddenException("User does not belong to any organization!"));
      }

      String delegatorIdStr = delegatorId.toString();
      List<Future> validations = new ArrayList<>();

      for (String itemId : itemIds) {
        GetItemRequest itemRequest = new GetItemRequest(itemId, delegatorIdStr);
        Future<Void> validationFuture = itemService.getItem(itemRequest).compose(response -> {

          if (response == null) {
            return Future.failedFuture(new DxBadRequestException("Response is empty for item: " + itemId));
          }

          List<JsonObject> validResponses = response.getElasticsearchResponses()
            .stream()
            .filter(Objects::nonNull)
            .toList();

          LOGGER.info("List of valid responses: {}",validResponses);

          JsonObject res = validResponses.getFirst();

          String ownerId = res.getString("ownerUserId");
          String itemOrgId = res.getString("organizationId");

          if(ownerId.equalsIgnoreCase(delegatorIdStr))
            return Future.succeededFuture();

          if(!orgDelIdStr.equalsIgnoreCase(itemOrgId))
            return Future.failedFuture(new DxBadRequestException(
              "User " + delegatorId + " is not authorized to delegate the item " + itemId
            ));

          return Future.succeededFuture();
        });

        validations.add(validationFuture);
      }

      return CompositeFuture.all(validations).mapEmpty();
    });
  }

  private Future<Void> validateItemIdOwnership(UUID delegatorId, List<String> itemIds) {

    LOGGER.info("Validate Item Id ownership !");
    if (itemIds == null || itemIds.isEmpty()) {
      return Future.failedFuture(new DxForbiddenException("No asset IDs provided"));
    }

      String delegatorIdStr = delegatorId.toString();
      List<Future> validations = new ArrayList<>();

      // validate each asset's organization in a loop
      for (String itemId : itemIds) {
        GetItemRequest itemRequest = new GetItemRequest(itemId, delegatorIdStr);

        Future<Void> validationFuture = itemService.getItem(itemRequest).compose(response -> {

          if (response == null) {
            return Future.failedFuture(new DxBadRequestException("Response is empty for item: " + itemId));
          }

          List<JsonObject> validResponses = response.getElasticsearchResponses()
            .stream()
            .filter(Objects::nonNull)
            .toList();

          LOGGER.info("List of valid responses: {}",validResponses);

          JsonObject res = validResponses.getFirst();

          String ownerId = res.getString("ownerUserId");

          if(ownerId.equalsIgnoreCase(delegatorIdStr))
            return Future.succeededFuture();

          return Future.succeededFuture();
        });

        validations.add(validationFuture);
      }

      // combine all asset validations
      return CompositeFuture.all(validations).mapEmpty();
  }

  private Future<Void> validateOrgOwnership(UUID delegatorId, List<String> orgIds) {
    if (orgIds == null || orgIds.isEmpty()) {
      return Future.failedFuture(new DxForbiddenException("No organization ID provided"));
    }

    UUID orgId = UUID.fromString(orgIds.get(0));

    return organizationService.getOrganisationAdminId(orgId)
      .compose(res -> {
        UUID adminId = res.get(0).userId();
        if (adminId.equals(delegatorId)) {
          return succeededFuture();
        } else {
          return Future.failedFuture(new DxForbiddenException("Delegator is not an admin of this organization"));
        }
      });
  }

  /**
   * Validate ownership of provider requests made to same organization as delegator’s.
   */
  private Future<Void> validateProviderRequestOwnership(UUID delegatorId, List<String> providerReqIds) {
    if (providerReqIds == null || providerReqIds.isEmpty()) {
      return Future.failedFuture(new DxBadRequestException("No provider request IDs provided"));
    }

    return organizationService.getOrganizationUserInfo(delegatorId)
      .compose(userInfo -> {
        if (userInfo == null || userInfo.organizationId() == null) {
          return Future.failedFuture(new DxForbiddenException("Delegator is not associated with any organization"));
        }

        UUID delegatorOrgId = userInfo.organizationId();
        LOGGER.info("Delegator {} belongs to org {}", delegatorId, delegatorOrgId);

        List<Future<Void>> futures = new ArrayList<>();

        for (String reqId : providerReqIds) {
          UUID prId;
          try {
            prId = UUID.fromString(reqId);
          } catch (IllegalArgumentException e) {
            LOGGER.error("Invalid provider request ID format: {}", reqId);
            futures.add(Future.failedFuture(new DxBadRequestException("Invalid provider request ID: " + reqId)));
            continue;
          }

          Future<Void> validationFuture = organizationService.getProviderRequestById(prId)
            .compose(pr -> {

              if (pr == null) {
                return Future.failedFuture(
                  new DxNotFoundException("Provider request not found: " + reqId)
                );
              }

              UUID requestOrgId = pr.orgId();
              if (!delegatorOrgId.equals(requestOrgId)) {
                LOGGER.error("Delegator org {} does not own provider request {}", delegatorOrgId, reqId);
                return Future.failedFuture(
                  new DxForbiddenException("Delegator not authorized for provider request " + reqId)
                );
              }
              return succeededFuture();
            });

          futures.add(validationFuture);
        }

        return CompositeFuture.all(new ArrayList<>(futures)).mapEmpty();
      });
  }

  /**
   * Helper: Determine highest role from a role set.
   */
  private String getHighestRole(Set<String> roles) {
    if (roles.contains("cos_admin")) return "cos_admin";
    if (roles.contains("org_admin")) return "org_admin";
    if (roles.contains("provider")) return "provider";
    return "user";
  }


}
