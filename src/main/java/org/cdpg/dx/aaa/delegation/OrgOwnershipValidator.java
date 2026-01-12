package org.cdpg.dx.aaa.delegation;
import io.vertx.core.*;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.item.util.GetItemRequest;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.cdpg.dx.aaa.delegation.util.Constants.DELEGATOR_ID;


import static io.vertx.core.Future.succeededFuture;

public class OrgOwnershipValidator {

  private static final Logger LOGGER = LoggerFactory.getLogger(OrgOwnershipValidator.class);

  private final OrganizationService organizationService;
  private final ItemService itemService;

  public OrgOwnershipValidator(OrganizationService organizationService, ItemService itemService) {
    this.organizationService = organizationService;
    this.itemService = itemService;
  }

  /**
   * Validate ownership of entities based on delegator’s role.
   */
  public Future<Void> validateEntityOwnership(
    JsonObject delegationGrant, Set<String> delegatorRoles , JsonArray rolesArray
  ) {

    LOGGER.info("Inside the validateEntityOwnership");

    UUID delegatorId = UUID.fromString(delegationGrant.getString(DELEGATOR_ID));

    //  cos_admin bypass
//    if (delegatorRoles.contains("cos_admin")) {
//      LOGGER.info("cos_admin detected — skipping entity ownership validation");
//      return Future.succeededFuture();
//    }

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

//      String role = rolesArray.getJsonObject(i).getString("role");
//
//      RoleScopeMapping allowedScopes = RoleScopeMapping.fromString(role);
      if(constraints==null)
      {
        continue;
      }

      for (int j = 0; j < constraints.size(); j++) {

        JsonObject constraint = constraints.getJsonObject(j);
        String scope = constraint.getString("scope");
        String normalizedScope = scope.toLowerCase();

        JsonArray entityId =
          constraint.containsKey("entity_id")
            ? constraint.getJsonArray("entity_id")
            : null;

        String entityType =
          constraint.containsKey("entity_type")
            ? constraint.getString("entity_type")
            : null;

        // Wildcard entity → skip ownership check
        if ((entityId == null || entityId.isEmpty()) && entityType == null) {
          continue;
        }

        List<String> entityIdList = entityId.getList();
        LOGGER.info("entityid: {}",entityId);
        LOGGER.info("entityidList: {}",entityIdList);

        switch (normalizedScope) {
          case "user_management" ->
              validations.add(
                validateOrgOwnership(delegatorId, entityIdList)
              );
          case "data_access" ->
            validations.add(
              validateItemIdOwnership(delegatorId, entityIdList)
            );

          case "asset_management" ->
            validations.add(
              validateAssetRequestOwnership(delegatorId, entityIdList)
            );
//          case "cos_admin_access" ->
//          {
//            LOGGER.debug("Skipping ownership validation for cos_admin_access");
//          }
          case "compute_management" ->
          {
            LOGGER.debug("Skipping ownership validation for compute_management access");
          }
          case "credit_management" ->
          {
            LOGGER.debug("Skipping ownership validation for credit_management access");
          }
          default -> {
            return Future.failedFuture(
              new DxForbiddenException("Unsupported scope: " + scope)
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
//    return organizationService.getOrganizationUserInfo(delegatorId).compose(orgInfo -> {
//      UUID orgDelId = orgInfo.organizationId();
//      String orgDelIdStr = orgDelId.toString();
//      if (orgDelId == null) {
//        return Future.failedFuture(new DxForbiddenException("User does not belong to any organization!"));
//      }
//
      String delegatorIdStr = delegatorId.toString();
      List<Future> validations = new ArrayList<>();

      for (String itemId : itemIds) {
        GetItemRequest itemRequest = new GetItemRequest(itemId, delegatorIdStr);
        Future<Void> validationFuture = itemService.getItemWithAccessChecks(itemRequest).compose(response -> {

          if (response == null) {
            return Future.failedFuture(new DxBadRequestException("Response is empty for item: " + itemId));
          }

//          List<JsonObject> validResponses = response.getElasticsearchResponses()
//            .stream()
//            .filter(Objects::nonNull)
//            .toList();
//
//          LOGGER.info("List of valid responses: {}",validResponses);
//
//          JsonObject res = validResponses.getFirst();
//
//          String ownerId = res.getString("ownerUserId");
//          String itemOrgId = res.getString("organizationId");
//
//          //checking if the delegator is the owner of the item id
//          if(ownerId.equalsIgnoreCase(delegatorIdStr))
//            return Future.succeededFuture();
//
//          // org id of the user doesn't match org id fetched from item info
//          if(!orgDelIdStr.equalsIgnoreCase(itemOrgId))
//            return Future.failedFuture(new DxBadRequestException(
//              "User " + delegatorId + " is not authorized to delegate the item " + itemId
//            ));

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

//          List<JsonObject> validResponses = response.getElasticsearchResponses()
//            .stream()
//            .filter(Objects::nonNull)
//            .toList();
//
//          LOGGER.info("List of valid responses: {}",validResponses);
//
//          JsonObject res = validResponses.getFirst();
//
//          String ownerId = res.getString("ownerUserId");
//
//          if(ownerId.equalsIgnoreCase(delegatorIdStr))
//            return Future.succeededFuture();

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
