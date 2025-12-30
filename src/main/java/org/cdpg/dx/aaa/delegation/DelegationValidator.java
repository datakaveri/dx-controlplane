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
import java.util.stream.Collectors;
import static org.cdpg.dx.aaa.delegation.util.Constants.DELEGATOR_ID;


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
  public Future<Void> validateAllConstraints(
    JsonObject delegationGrant,Set<String> delegatorRoles
  ) {

    LOGGER.info("Validating delegation constraints");

    // cos_admin bypass
    if (delegatorRoles.contains("cos_admin")) {
      return Future.succeededFuture();
    }

    JsonArray rolesArray = delegationGrant.getJsonArray("roles");

    // Full delegation → nothing to validate
    if (rolesArray == null || rolesArray.isEmpty()) {
      LOGGER.info("Full delegation detected — skipping scope validation");
      return Future.succeededFuture();
    }

    String delegatorRole = getHighestRole(delegatorRoles);
    RoleScopeMapping delegatorMapping =
      RoleScopeMapping.fromString(delegatorRole);

    Set<String> allowedScopes =
      delegatorMapping.getAllowedScopes()
        .stream()
        .map(String::toLowerCase)
        .collect(Collectors.toSet());

    for (int i = 0; i < rolesArray.size(); i++) {
      JsonObject roleObj = rolesArray.getJsonObject(i);
      JsonArray constraints = roleObj.getJsonArray("constraints");

      if (constraints.isEmpty()) {
        throw new DxBadRequestException("Constraints cannot be empty for role: " + delegatorRole);
      }

      for (int j = 0; j < constraints.size(); j++) {
        String scope =
          constraints.getJsonObject(j).getString("scope");

        if (!allowedScopes.contains(scope.toLowerCase())) {
          return Future.failedFuture(
            new DxForbiddenException(
              "Delegator with role '" + delegatorRole +
                "' cannot delegate scope '" + scope + "'"
            )
          );
        }
      }
    }

    LOGGER.info("Scope validation successful");
    return Future.succeededFuture();
  }


  /**
   * Validate ownership of entities based on delegator’s role.
   */
  public Future<Void> validateEntityOwnership(
    JsonObject delegationGrant, Set<String> delegatorRoles
  ) {

    UUID delegatorId = UUID.fromString(delegationGrant.getString(DELEGATOR_ID));

    //  cos_admin bypass
    if (delegatorRoles.contains("cos_admin")) {
      LOGGER.info("cos_admin detected — skipping entity ownership validation");
      return Future.succeededFuture();
    }

    JsonArray rolesArray = delegationGrant.getJsonArray("roles");

    //  Full delegation → skip
    if (rolesArray == null || rolesArray.isEmpty()) {
      LOGGER.info("Full delegation — skipping entity ownership validation");
      return Future.succeededFuture();
    }

    String delegatorRole = getHighestRole(delegatorRoles);
    List<Future> validations = new ArrayList<>();

    for (int i = 0; i < rolesArray.size(); i++) {
      JsonArray constraints =
        rolesArray.getJsonObject(i).getJsonArray("constraints");

      for (int j = 0; j < constraints.size(); j++) {

        JsonObject constraint = constraints.getJsonObject(j);
        String scope = constraint.getString("scope");

        String entityId = constraint.getString("entity_id");
        String entityType = constraint.getString("entity_type");

        // 🔹 Wildcard entity → skip ownership check
        if (entityId == null && entityType == null) {
          continue;
        }

        switch (delegatorRole) {

          case "org_admin" -> {
            if ("org_management".equalsIgnoreCase(scope)) {
              validations.add(
                validateOrgOwnership(delegatorId, List.of(entityId))
              );
            } else if ("asset_management".equalsIgnoreCase(scope)) {
              validations.add(
                validateAssetRequestOwnership(delegatorId, List.of(entityId))
              );
            } else if ("data_access".equalsIgnoreCase(scope)) {
              validations.add(
                validateItemIdOwnership(delegatorId, List.of(entityId))
              );
            }
          }

          case "provider" -> {
            if ("asset_management".equalsIgnoreCase(scope)) {
              validations.add(
                validateAssetRequestOwnership(delegatorId, List.of(entityId))
              );
            } else if ("data_access".equalsIgnoreCase(scope)) {
              validations.add(
                validateItemIdOwnership(delegatorId, List.of(entityId))
              );
            }
          }

          case "consumer" -> {
            if ("data_access".equalsIgnoreCase(scope)) {
              validations.add(
                validateItemIdOwnership(delegatorId, List.of(entityId))
              );
            }
          }

          default -> {
            return Future.failedFuture(
              new DxForbiddenException(
                "Delegator role '" + delegatorRole +
                  "' cannot delegate scope '" + scope + "'"
              )
            );
          }
        }
      }
    }

    if (validations.isEmpty()) {
      return Future.succeededFuture();
    }

    return CompositeFuture.all(validations).mapEmpty();
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

          //checking if the delegator is the owner of the item id
          if(ownerId.equalsIgnoreCase(delegatorIdStr))
            return Future.succeededFuture();

          // org id of the user doesn't match org id fetched from item info
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
   * Helper: Determine highest role from a role set.
   */
  private String getHighestRole(Set<String> roles) {
    if (roles.contains("cos_admin")) return "cos_admin";
    if (roles.contains("org_admin")) return "org_admin";
    if (roles.contains("provider")) return "provider";
    return "consumer";
  }


}
