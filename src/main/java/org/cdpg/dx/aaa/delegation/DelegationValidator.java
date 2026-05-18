package org.cdpg.dx.aaa.delegation;
import io.vertx.core.*;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
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
   * Validate ownership of entities based on delegator’s role.
   */
  public Future<Void> validateEntityOwnership(
    JsonObject delegationGrant,
    Set<String> delegatorRoles,
    JsonArray rolesArray
  ) {

    LOGGER.info("Inside validateEntityOwnership");

    UUID delegatorId =
      UUID.fromString(delegationGrant.getString(DELEGATOR_ID));

    return validateConstraints(delegatorId, rolesArray);
  }


  public Future<Void> validateConstraints(
    UUID actorId,
    JsonArray rolesArray
  ) {

    if (rolesArray == null || rolesArray.isEmpty()) {
      LOGGER.info("Wildcard access — skipping entity ownership validation");
      return Future.succeededFuture();
    }

    List<Future> validations = new ArrayList<>();

    for (int i = 0; i < rolesArray.size(); i++) {

      JsonArray constraints = rolesArray
        .getJsonObject(i)
        .getJsonArray("constraints");

      if (constraints == null || constraints.isEmpty()) {
        continue;
      }

      for (int j = 0; j < constraints.size(); j++) {

        JsonObject constraint = constraints.getJsonObject(j);
        String scope = constraint.getString("scope");
        String normalizedScope = scope.toLowerCase();

        JsonArray entityIds = constraint.getJsonArray("entity_id");
        String entityType = constraint.getString("entity_type");

        // wildcard entity
        if ((entityIds == null || entityIds.isEmpty()) && entityType == null) {
          continue;
        }

        List<String> entityIdList =
          entityIds != null ? entityIds.getList() : List.of();

        switch (normalizedScope) {

          // user/org ownership validation
          case "user-management", "org-user-management", "org-publisher-management" ->
            validations.add(validateOrgOwnership(actorId, entityIdList));

          // item access validation
          case "data-access" ->
            validations.add(validateItemIdOwnership(actorId, entityIdList));

          // asset ownership validation
          case "asset-management", "own-asset-management",
               "org-asset-management", "org-asset-publish", "asset-publish" ->
            validations.add(validateAssetRequestOwnership(actorId, entityIdList));

          // platform-level scopes — no entity ownership check needed
          case "compute-management", "credit-management",
               "org-management", "publisher-management", "role-management" -> {
            LOGGER.debug("Skipping ownership validation for scope {}", scope);
          }

          default -> {
            return Future.failedFuture(
              new DxForbiddenException("Unsupported scope: " + scope)
            );
          }
        }
      }
    }

    return validations.isEmpty()
      ? Future.succeededFuture()
      : CompositeFuture.all(validations).mapEmpty();
  }




  private Future<Void> validateAssetRequestOwnership(UUID delegatorId, List<String> itemIds) {

    LOGGER.info("Validating every asset ownership!");
    if (itemIds == null || itemIds.isEmpty()) {
      return Future.failedFuture(new DxForbiddenException("No asset IDs provided"));
    }

      String delegatorIdStr = delegatorId.toString();
      List<Future> validations = new ArrayList<>();

      for (String itemId : itemIds) {
        GetItemRequest itemRequest = new GetItemRequest(itemId, delegatorIdStr);
        Future<Void> validationFuture = itemService.getItemWithAccessChecks(itemRequest).compose(response -> {

          if (response == null) {
            return Future.failedFuture(new DxBadRequestException("Response is empty for item: " + itemId));
          }

          return Future.succeededFuture();
        });

        validations.add(validationFuture);
      }

      return CompositeFuture.all(validations).mapEmpty();

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

        Future<Void> validationFuture = itemService.getItemWithAccessChecks(itemRequest).compose(response -> {

          if (response == null) {
            return Future.failedFuture(new DxBadRequestException("Response is empty for item: " + itemId));
          }

          return Future.succeededFuture();
        });

        validations.add(validationFuture);
      }

      // combine all asset validations
      return CompositeFuture.all(validations).mapEmpty();
  }

  private Future<Void> validateOrgOwnership(UUID delegatorId, List<String> orgIds) {
    LOGGER.info("Validating org Ids ownership");
    LOGGER.info("orgids: {}",orgIds);

    if (orgIds == null || orgIds.isEmpty()) {
      return Future.failedFuture(new DxForbiddenException("No organization ID provided"));
    }

    UUID orgId = UUID.fromString(orgIds.get(0));

    return organizationService.getOrganisationAdminId(orgId)
      .compose(res -> {
        UUID adminId = res.getFirst().userId();
        LOGGER.info("admin: {}",adminId);
        LOGGER.info("delegator: {}",delegatorId);
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
