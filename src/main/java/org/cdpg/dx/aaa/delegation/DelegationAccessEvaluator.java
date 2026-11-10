package org.cdpg.dx.aaa.delegation;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.delegation.service.DelegationService;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.item.util.GetItemRequest;
import org.cdpg.dx.aaa.token.model.DelegationValidationResult;
import org.cdpg.dx.aaa.token.model.ItemInfo;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.cdpg.dx.common.util.DateTimeHelper.parseDateTime;

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

    UUID userId = user.sub();
    UUID itemId = UUID.fromString(itemIdStr);
    LocalDateTime now = LocalDateTime.now();

    return delegationService
      .getAllDelegationsOfDelegate(userId.toString())
      .compose(grants -> {

        if (grants == null || grants.isEmpty()) {
          return Future.failedFuture("No delegation grants");
        }

        Future<DelegationValidationResult> chain =
          Future.failedFuture("No valid delegation");

        for (JsonObject grant : grants) {
          chain = chain.recover(err ->
            validateGrant(grant, userId, itemId, now)
          );
        }

        return chain;
      });
  }

  // ------------------ Grant Validation ------------------
  private Future<DelegationValidationResult> validateGrant(
    JsonObject grant,
    UUID delegateId,
    UUID itemId,
    LocalDateTime now
  ) {

    LOGGER.info("Inside validateGrant!");
    String delegateIdStr = grant.getString("delegate_id");
    if (!delegateId.toString().equals(delegateIdStr)) {
      return Future.failedFuture("Delegate mismatch");
    }

    String expiryStr = grant.getString("expiry_at");
    if (expiryStr != null) {
      LocalDateTime expiry = parseDateTime(expiryStr);
      if (expiry.isBefore(now)) {
        return Future.failedFuture("Delegation expired");
      }
    }

    return delegationService
      .getDelegationScopeConstraints(grant.getString("delegation_id"))
      .compose(constraints -> {

        if (constraints == null || constraints.isEmpty()) {
          return Future.failedFuture("No delegation constraints");
        }

        Future<DelegationValidationResult> chain =
          Future.failedFuture("No valid delegation");

        for (JsonObject constraint : constraints) {
          chain = chain.recover(err ->
            validateConstraint(grant, constraint, itemId)
          );
        }

        return chain;
      });
  }

  // ------------------ Constraint Validation ------------------
  private Future<DelegationValidationResult> validateConstraint(
    JsonObject grant,
    JsonObject constraint,
    UUID itemId
  ) {

    LOGGER.info("Inside validateConstraint!");
    UUID delegatorId = UUID.fromString(grant.getString("delegator_id"));
    UUID delegationId = UUID.fromString(grant.getString("delegation_id"));
    UUID delegateId = UUID.fromString(grant.getString("delegate_id"));

    String expiryStr = grant.getString("expiry_at");
    if (expiryStr != null &&
      parseDateTime(expiryStr).isBefore(LocalDateTime.now())) {
      return Future.failedFuture("Delegation expired");
    }

    String entityId = constraint.getString("entity_id");
    String scope = constraint.getString("scope");

    if ("*".equals(scope) || "data-access".equals(scope)) {
      return fetchItemAsDelegator(delegatorId, itemId, delegationId, delegateId);
    }

    if (entityId != null) {
      if (!entityId.equals("*") && !entityId.equals(itemId.toString())) {
        return Future.failedFuture("Entity ID mismatch");
      }
      return fetchItemAsDelegator(delegatorId, itemId, delegationId, delegateId);
    }


    return Future.failedFuture("Constraint does not allow access");
  }

  // ------------------ Final Access Check ------------------
  private Future<DelegationValidationResult> fetchItemAsDelegator(
    UUID delegatorId,
    UUID itemId,
    UUID delegationId,
    UUID delegateId
  ) {

    return keycloakUserService
      .getUserById(delegatorId)
      .compose(delegatorUser -> {

        GetItemRequest request =
          new GetItemRequest(itemId.toString(), delegatorId.toString());
        request.setRoles(delegatorUser.roles());

        return itemService.getItemWithAccessChecks(request)
          .compose(response -> {

            if (response == null || response.getResponse() == null) {
              return Future.failedFuture("Delegator has no access");
            }

            JsonObject item =
              response.getResponse()
                .getJsonArray("results")
                .getJsonObject(0);

            ItemInfo info = ItemInfo.fromJson(item);

            return Future.succeededFuture(
              new DelegationValidationResult(
                delegationId,
                delegatorId,
                delegateId,
                info
              )
            );
          });
      });
  }
}
