package org.cdpg.dx.aaa.delegation.handler;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.audit.util.AuditingHelper;
import org.cdpg.dx.aaa.delegation.DelegationHandlerValidator;
import org.cdpg.dx.aaa.delegation.UpdatedGrantResponse;
import org.cdpg.dx.aaa.delegation.models.DelegationGrant;
import org.cdpg.dx.aaa.delegation.models.DelegationUpdateRequest;
import org.cdpg.dx.aaa.delegation.service.DelegationService;
import org.cdpg.dx.aaa.email.util.EmailComposer;
import org.cdpg.dx.aaa.user.service.UserService;
import org.cdpg.dx.auditing.model.AuditLog;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.common.util.RequestHelper;
import org.cdpg.dx.common.util.RoutingContextHelper;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
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
  private final DelegationHandlerValidator delegationHandlerValidator;
//  private final UpdatedGrantResponse updatedGrantResponse;


  public DelegationHandler(DelegationService delegationService, EmailComposer emailComposer, UserService userService, URNGenerator urnGenerator,KeycloakUserService keycloakUserService)
  {
    this.delegationService = delegationService;
    this.emailComposer = emailComposer;
    this.userService = userService;
    this.urnGenerator = urnGenerator;
    this.keycloakUserService = keycloakUserService;
    this.delegationHandlerValidator = new DelegationHandlerValidator();
  }

  public void getDelegationGrant(RoutingContext ctx) {
    UUID delegationId = RequestHelper.getPathParamAsUUID(ctx, "id");

    delegationService.getDelegationGrantById(delegationId)
      .onSuccess(grant -> {
        if (grant == null) {
          ctx.fail(new DxNotFoundException("Delegation grant not found with id: " + delegationId));
          return;
        }

        delegationService.getDelegationScopeConstraints(delegationId)
          .map(constraints -> new UpdatedGrantResponse(grant, constraints))
          .onSuccess(updatedGrant -> {
            AuditLog auditLog = AuditingHelper.createAuditLog(
              ctx.user(),
              RoutingContextHelper.getRequestPath(ctx),
              "GET",
              "Get Delegation Grant By ID"
            );
            RoutingContextHelper.setAuditingLog(ctx, auditLog);
            ResponseBuilder.sendSuccess(ctx, updatedGrant, urnGenerator);
          })
          .onFailure(ctx::fail);
      })
      .onFailure(ctx::fail);
  }


  public void createDelegationGrant(RoutingContext ctx) {
    LOGGER.info("Handler: createDelegationGrant");

    User user = ctx.user();
    UUID userId = UUID.fromString(user.subject());
    JsonObject body = ctx.body().asJsonObject();

    try {
      delegationHandlerValidator.validateCreateDelegationGrantBody(userId, body);
    } catch (DxBadRequestException | DxForbiddenException e) {
      ctx.fail(e);
      return;
    }

    DelegationGrant delegationGrant = DelegationGrant.fromJson(body);
    List<JsonObject> constraintsJson = delegationHandlerValidator.extractConstraintsforDelegationGrants(body);
    Set<String> userRoles = delegationHandlerValidator.extractRoles(user);

    LOGGER.info("constraintsJson:{}",constraintsJson);
    LOGGER.info("userRoles:{}",userRoles);

    delegationService.createDelegationGrant(delegationGrant, userRoles, constraintsJson)
      .onSuccess(createdGrant -> {
        AuditLog auditLog = AuditingHelper.createAuditLog(
          ctx.user(),
          RoutingContextHelper.getRequestPath(ctx),
          "POST",
          "Delegation Grant Created"
        );
        RoutingContextHelper.setAuditingLog(ctx, auditLog);
        ResponseBuilder.sendSuccess(ctx, createdGrant, urnGenerator);
      })
      .onFailure(ctx::fail);

  }


  public void createUpdateDelegationRequest(RoutingContext ctx) {
    JsonObject body = ctx.body().asJsonObject();
    UUID requesterId = UUID.fromString(ctx.user().subject());

    UUID delegationId = UUID.fromString(body.getString("delegation_id"));

    LOGGER.info("Incoming request body: {}", body.encodePrettily());

    try {
      delegationHandlerValidator.validateCreateUpdateDelegationRequestBody(body);
    } catch (DxBadRequestException | DxForbiddenException e) {
      ctx.fail(e);
      return;
    }

    List<JsonObject> constraintsJson = delegationHandlerValidator.extractConstraintsforDelegationRequests(body);
    body.put("delegate_id",requesterId);


    // Fetch delegator from existing grant
    delegationService.getDelegationGrantById(delegationId)
      .compose(grant -> {
        UUID delegatorId = grant.delegatorId();
        DelegationUpdateRequest delegationRequest = DelegationUpdateRequest.fromJson(body);

        return keycloakUserService.getUserById(delegatorId)
          .compose(delegator -> {
            Set<String> userRoles = new HashSet<>(delegator.roles());
            return delegationService.createDelegationRequest(delegationRequest, userRoles, constraintsJson,delegatorId);
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
      })
      .onFailure(ctx::fail);
  }

  public void updateDelegationRequest(RoutingContext ctx) {
    LOGGER.info("Handler: createUpdateDelegationRequest");

    JsonObject body = ctx.body().asJsonObject();
      if (body == null)
      {   throw new DxBadRequestException("Request body is missing");
      }

      String status = body.getString("status");

      UUID requestId = UUID.fromString(ctx.pathParam("id"));

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
    UUID delegationId = UUID.fromString(ctx.pathParam("delegation_id"));

    delegationService.getDelegationRequestsByDelegationId(delegationId)
      .onSuccess(requests -> {
        ResponseBuilder.sendSuccess(ctx, requests, urnGenerator);
      })
      .onFailure(ctx::fail);
  }

}
