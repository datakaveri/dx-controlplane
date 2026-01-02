package org.cdpg.dx.aaa.delegation;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.delegation.models.*;
import org.cdpg.dx.aaa.delegation.service.DelegationService;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.item.util.GetItemRequest;
import org.cdpg.dx.aaa.token.model.DelegationValidationResult;
import org.cdpg.dx.aaa.token.model.ItemInfo;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DelegationAccessEvaluator {

  private static final Logger LOGGER =
    LoggerFactory.getLogger(DelegationAccessEvaluator.class);

  private final DelegationService delegationService;
  private final ItemService itemService;
  private final KeycloakUserService keycloakUserService;

  public DelegationAccessEvaluator(
    DelegationService delegationService,
    ItemService itemService,
    KeycloakUserService keycloakUserService
  ) {
    this.delegationService = delegationService;
    this.itemService = itemService;
    this.keycloakUserService = keycloakUserService;
  }

  public Future<DelegationValidationResult> validateItemAccess(
    DxUser user,
    String itemIdStr
  ) {

    LOGGER.info("Inside validateItemAccess");

    UUID userId = user.sub();
    UUID itemId = UUID.fromString(itemIdStr);
    LocalDateTime now = LocalDateTime.now();

    return delegationService.getAllDelegationsOfDelegate(userId)
      .compose(grants -> {

        if (grants.isEmpty()) {
          return Future.failedFuture("No delegation grants");
        }

        Future<DelegationValidationResult> chain =
          Future.failedFuture("No valid delegation");

        for (DelegationGrant grant : grants) {
          chain = chain.recover(err ->
            validateGrant(grant, userId, itemId, now)
          );
        }

        return chain;
      });
  }


  // ------------------ Grant Validation ------------------
  private Future<DelegationValidationResult> validateGrant(
    DelegationGrant grant,
    UUID delegateId,
    UUID itemId,
    LocalDateTime now
  ) {

    LOGGER.info("Inside validateGrant");


    // 🔹 Delegate check
    if (!grant.delegateId().equals(delegateId)) {
      return Future.failedFuture("Delegate mismatch");
    }

    // 🔹 Expiry check
    if (grant.expiryAt() != null && grant.expiryAt().isBefore(now)) {
      return Future.failedFuture("Delegation expired");
    }

    return delegationService
      .getDelegationScopeConstraints(grant.delegationId())
      .compose(constraints -> {

        if (constraints == null || constraints.isEmpty()) {
          return Future.failedFuture("No delegation constraints found");
        }

          Future<DelegationValidationResult> chain =
            Future.failedFuture("No valid delegation");

        for (DelegationScopeConstraint constraint : constraints) {
          chain = chain.recover(err ->
            validateConstraint(grant, constraint, itemId)
          );
        }

        return chain;
      });

  }


  // ------------------ Constraint Validation ------------------
  private Future<DelegationValidationResult> validateConstraint(
    DelegationGrant grant,
    DelegationScopeConstraint constraint,
    UUID itemId
  ) {

    LOGGER.info("Inside validateConstraint");


    UUID delegatorId = grant.delegatorId();
    UUID delegateId = grant.delegateId();
    UUID delegationId = grant.delegationId();
    LocalDateTime now = LocalDateTime.now();

    // 🔹 Expiry check
    if (grant.expiryAt() != null && grant.expiryAt().isBefore(now)) {
      return Future.failedFuture("Delegation has expired");
    }

    // 🔹 CASE 1: Entity-specific delegation
    if (constraint.entityId() != null) {

      if (!constraint.entityId().equals(itemId)) {
        return Future.failedFuture("Entity ID mismatch");
      }

      return fetchItemAsDelegator(
        delegatorId,
        itemId,
        grant
      );
    }

    // 🔹 CASE 2: Wildcard or scope-only delegation
    if ("*".equals(constraint.scope())
      || "asset_management".equals(constraint.scope())) {

      return fetchItemAsDelegator(
        delegatorId,
        itemId,
        grant
      );
    }

    return Future.failedFuture("Constraint scope does not allow item access");
  }



  // ------------------ Final Access Check ------------------
  private Future<DelegationValidationResult> fetchItemAsDelegator(
    UUID delegatorId,
    UUID itemId,
    DelegationGrant grant
  ) {

    LOGGER.info("Inside fetchItemAsDelegator");

    return keycloakUserService.getUserById(delegatorId)
      .compose(delegatorUser -> {

        GetItemRequest itemRequest =
          new GetItemRequest(itemId.toString(), delegatorId.toString());
        itemRequest.setRoles(delegatorUser.roles());

        return itemService.getItemWithAccessChecks(itemRequest)
          .compose(response -> {

            if (response == null || response.getResponse() == null) {
              return Future.failedFuture(
                "Delegator does not have access to the item"
              );
            }

            JsonObject item =
              response.getResponse()
                .getJsonArray("results")
                .getJsonObject(0);

            ItemInfo info = ItemInfo.fromJson(item);

            return Future.succeededFuture(
              new DelegationValidationResult(
                grant.delegationId(),
                grant.delegatorId(),
                grant.delegateId(),
                info
              )
            );
          });
      });
  }

}
