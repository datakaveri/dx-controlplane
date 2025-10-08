package org.cdpg.dx.aaa.delegation.handler;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.audit.util.AuditingHelper;
import org.cdpg.dx.aaa.delegation.models.DelegationGrant;
import org.cdpg.dx.aaa.delegation.models.DelegationUpdateRequest;
import org.cdpg.dx.aaa.delegation.service.DelegationService;
import org.cdpg.dx.aaa.email.util.EmailComposer;
import org.cdpg.dx.aaa.user.service.UserService;
import org.cdpg.dx.auditing.model.AuditLog;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.common.util.RequestHelper;
import org.cdpg.dx.common.util.RoutingContextHelper;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.cdpg.dx.common.util.DateTimeHelper.FORMATTER;
import static org.cdpg.dx.common.util.DateTimeHelper.parseDateTime;

public class DelegationHandler {

  private static final Logger LOGGER = LogManager.getLogger(DelegationHandler.class);
  private final DelegationService delegationService;
  private final EmailComposer emailComposer;
  private final URNGenerator urnGenerator;
  private final UserService userService;
  private final KeycloakUserService keycloakUserService;


  public DelegationHandler(DelegationService delegationService, EmailComposer emailComposer, UserService userService, URNGenerator urnGenerator,KeycloakUserService keycloakUserService)
  {
    this.delegationService = delegationService;
    this.emailComposer = emailComposer;
    this.userService = userService;
    this.urnGenerator = urnGenerator;
    this.keycloakUserService = keycloakUserService;
  }

  public void getDelegationGrant(RoutingContext ctx) {
    UUID delegationId = RequestHelper.getPathParamAsUUID(ctx, "id");

    delegationService.getDelegationGrantById(delegationId)
      .onSuccess(grant -> {
        if (grant == null) {
          ctx.fail(new DxNotFoundException("Delegation grant not found with id: " + delegationId));
          return;
        }

        AuditLog auditLog = AuditingHelper.createAuditLog(
          ctx.user(),
          RoutingContextHelper.getRequestPath(ctx),
          "GET",
          "Get Delegation Grant By ID"
        );
        RoutingContextHelper.setAuditingLog(ctx, auditLog);
        ResponseBuilder.sendSuccess(ctx, grant, urnGenerator);
      })
      .onFailure(ctx::fail);
  }

  public void createDelegationGrant(RoutingContext routingContext) {
    User user = routingContext.user();
    UUID userId = UUID.fromString(user.subject());
    JsonObject principal = user.principal();

    JsonObject delegationGrantBody = routingContext.body().asJsonObject();
    delegationGrantBody.put("delegator_id",userId);

    Set<String> userRoles = new HashSet<>();
    if (principal.containsKey("realm_access")) {
      JsonObject realmAccess = principal.getJsonObject("realm_access");
      if (realmAccess.containsKey("roles")) {
        userRoles.addAll(realmAccess.getJsonArray("roles").getList());
      }
    }

    String expirationDate = null;
    expirationDate = delegationGrantBody.getString("expiration_at");
    if (expirationDate == null || expirationDate.isEmpty()) {
      throw new DxBadRequestException("Expiration date is required");
    }

    try {
      LocalDateTime.parse(expirationDate, FORMATTER);
    } catch (Exception e) {
      throw new DxBadRequestException("Invalid expiration date format. Expected format: " + FORMATTER);
    }

    if (parseDateTime(expirationDate).isBefore(java.time.LocalDateTime.now())) {
      throw new DxBadRequestException("Expiration date must be in the future");
    }

    DelegationGrant delegationGrant = DelegationGrant.fromJson(delegationGrantBody);

    delegationService.createDelegationGrant(delegationGrant,userRoles)
      .onSuccess(createdGrant -> {
        AuditLog auditLog = AuditingHelper.createAuditLog(
          routingContext.user(),
          RoutingContextHelper.getRequestPath(routingContext),
          "POST",
          "Delegation Grant Created"
        );
        RoutingContextHelper.setAuditingLog(routingContext, auditLog);
        ResponseBuilder.sendSuccess(routingContext, createdGrant, this.urnGenerator);
        // emailComposer.sendDelegationGrantNotification(user, createdGrant);
      })
      .onFailure(routingContext::fail);

  }


  public void createUpdateDelegationRequest(RoutingContext ctx) {
    User user = ctx.user();
    JsonObject principal = user.principal();
    JsonObject body = ctx.body().asJsonObject();

    UUID delegationId = UUID.fromString(ctx.pathParam("delegation_id"));
    body.put("requested_by", user.subject());

    String justification = body.getString("justification");
    if (justification == null || justification.isBlank()) {
      throw new DxBadRequestException("Justification is required");
    }

    String requestedExpiry = body.getString("requested_expiry");
    if (requestedExpiry == null || requestedExpiry.isEmpty()) {
      throw new DxBadRequestException("Expiration date is required");
    }

    try {
      LocalDateTime.parse(requestedExpiry, FORMATTER);
    } catch (Exception e) {
      throw new DxBadRequestException("Invalid expiration date format. Expected format: " + FORMATTER);
    }

    if (parseDateTime(requestedExpiry).isBefore(LocalDateTime.now())) {
      throw new DxBadRequestException("Expiration date must be in the future");
    }

    DelegationUpdateRequest delegationRequest = DelegationUpdateRequest.fromJson(body);

    //  Get delegator ID from existing delegation grant
    delegationService.getDelegationGrantById(delegationId)
      .compose(grant -> {
        UUID delegatorId = grant.delegatorId();

        // Fetch delegator roles from Keycloak
        return keycloakUserService.getUserById(delegatorId)
          .compose(delegator -> {
            Set<String> userRoles = new HashSet<>(delegator.roles());
            return delegationService.createDelegationRequest(delegationRequest, userRoles);
          });
      })
      .onSuccess(request -> {
        AuditLog auditLog = AuditingHelper.createAuditLog(
          ctx.user(),
          RoutingContextHelper.getRequestPath(ctx),
          "POST",
          "Create Delegation Request"
        );
        RoutingContextHelper.setAuditingLog(ctx, auditLog);
        ResponseBuilder.sendSuccess(ctx, request, urnGenerator);
        // emailComposer.sendDelegationRequestEmail(user, request);
      })
      .onFailure(ctx::fail);
  }


  public void updateDelegationRequest(RoutingContext ctx) {
    JsonObject body = ctx.body().asJsonObject();
    if(body==null)
      throw new DxBadRequestException("Request body is missing");

    UUID requestId = RequestHelper.getPathParamAsUUID(ctx, "id");
    String status = body.getString("status");

    if (status == null || status.isBlank()) {
      throw new DxBadRequestException("Status is required");
    }

    // Only delegators should be able to update the status
    User user = ctx.user();
    UUID delegatorId = UUID.fromString(user.subject());

    delegationService.updateDelegationRequestStatus(requestId, status, delegatorId)
      .onSuccess(updatedRequest -> {
        AuditLog auditLog = AuditingHelper.createAuditLog(
          user,
          RoutingContextHelper.getRequestPath(ctx),
          "PUT",
          "Delegation Request status updated to " + status
        );
        RoutingContextHelper.setAuditingLog(ctx, auditLog);
        ResponseBuilder.sendSuccess(ctx, updatedRequest.toJson(), urnGenerator);
        // emailComposer.sendDelegationRequestStatusEmail(requestId, status);
      })
      .onFailure(ctx::fail);
  }


  public void getDelegationRequest(RoutingContext ctx) {
    UUID userId = UUID.fromString(ctx.user().subject());


    //user should be a reviewer
    delegationService.getDelegationRequestsByUser(userId)
      .onSuccess(requests -> {
        ResponseBuilder.sendSuccess(ctx, requests, urnGenerator);
      })
      .onFailure(ctx::fail);
  }

}
