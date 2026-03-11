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
import org.cdpg.dx.aaa.delegation.service.DelegationService;
import org.cdpg.dx.aaa.email.util.EmailComposer;
import org.cdpg.dx.common.util.resolver.DelegatorStrategyFactory;
import org.cdpg.dx.aaa.user.service.UserService;
import org.cdpg.dx.auditing.model.AuditLog;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.common.util.RoutingContextHelper;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

import java.util.*;

import static org.cdpg.dx.aaa.delegation.util.Constants.*;

public class DelegationHandler {

  private static final Logger LOGGER = LogManager.getLogger(DelegationHandler.class);
  private final DelegationService delegationService;
  private final EmailComposer emailComposer;
  private final URNGenerator urnGenerator;
  private final UserService userService;
  private final KeycloakUserService keycloakUserService;
  private final DelegationHandlerValidator delegationHandlerValidator;
  private final DelegatorStrategyFactory delegatorStrategyFactory;
//  private final UpdatedGrantResponse updatedGrantResponse;


  public DelegationHandler(DelegationService delegationService, EmailComposer emailComposer, UserService userService, DelegatorStrategyFactory delegatorStrategyFactory,URNGenerator urnGenerator,KeycloakUserService keycloakUserService)
  {
    this.delegationService = delegationService;
    this.emailComposer = emailComposer;
    this.userService = userService;
    this.urnGenerator = urnGenerator;
    this.keycloakUserService = keycloakUserService;
    this.delegationHandlerValidator = new DelegationHandlerValidator();
    this.delegatorStrategyFactory = delegatorStrategyFactory;
  }

  public void getDelegationGrant(RoutingContext ctx) {
    String delegationId = ctx.pathParam( "id");


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

  public void getAllDelegationsOfDelegate(RoutingContext ctx) {

    User user = ctx.user();
    UUID userId = UUID.fromString(user.subject());

    delegationService.getAllDelegationsOfDelegate(userId.toString())
      .onSuccess(res -> {
        AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
          RoutingContextHelper.getRequestPath(ctx), "GET", "Get All Delegations of the delegate");

        RoutingContextHelper.setAuditingLog(ctx, auditLog);
        if(res==null || res.isEmpty())
        {
          ResponseBuilder.sendSuccess(ctx,"No delegation found for this user" ,urnGenerator);
        }

        ResponseBuilder.sendSuccess(ctx, res ,urnGenerator);

      })
      .onFailure(ctx::fail);

  }

  public void getAllDelegationsByDelegator(RoutingContext ctx) {

    User user = ctx.user();
    UUID userId = UUID.fromString(user.subject());

    delegationService.getAllDelegationsByDelegator(userId.toString())
      .onSuccess(res -> {
        AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
          RoutingContextHelper.getRequestPath(ctx), "GET", "Get All Delegations made by the user who is the delegator");

        RoutingContextHelper.setAuditingLog(ctx, auditLog);
        if(res==null || res.isEmpty())
        {
          ResponseBuilder.sendSuccess(ctx,"No delegation found for this user" ,urnGenerator);
        }

        ResponseBuilder.sendSuccess(ctx, res ,urnGenerator);


      })
      .onFailure(ctx::fail);

  }

  public void getDelegatorRoles(RoutingContext ctx) {

    User user = ctx.user();
    UUID userId = UUID.fromString(user.subject());

    UUID delegatorId = UUID.fromString(ctx.queryParams().get("delegatorId"));

    delegationService.getDelegatorRoles(userId.toString(),delegatorId.toString())
      .onSuccess(res -> {
        AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
          RoutingContextHelper.getRequestPath(ctx), "GET", "Get All Delegations made by the user who is the delegator");

        RoutingContextHelper.setAuditingLog(ctx, auditLog);
        if(res==null || res.isEmpty())
        {
          ResponseBuilder.sendSuccess(ctx,"No delegation found for this user" ,urnGenerator);
        }

        ResponseBuilder.sendSuccess(ctx, res ,urnGenerator);


      })
      .onFailure(ctx::fail);

  }



  public void deleteDelegationGrant(RoutingContext ctx)
  {

    User user = ctx.user();
    UUID userId = UUID.fromString(user.subject());

    UUID delegationId = UUID.fromString(ctx.pathParam("id"));

    delegationService.deleteDelegation(delegationId.toString(),userId.toString())
      .onSuccess(res -> {
        AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
          RoutingContextHelper.getRequestPath(ctx), "DELETE", "Delete delegation");

        RoutingContextHelper.setAuditingLog(ctx, auditLog);

        JsonObject response = new JsonObject()
          .put("delegation_id", delegationId.toString())
          .put("status", "deleted");


        ResponseBuilder.sendSuccess(ctx, response ,urnGenerator);

      })
      .onFailure(ctx::fail);
  }


  public void createDelegationGrant(RoutingContext ctx) {
    LOGGER.info("Handler: createDelegationGrant");

    User user = ctx.user();
    UUID delegatorId = UUID.fromString(user.subject());
    JsonObject body = ctx.body().asJsonObject();

    body.put(DELEGATOR_ID,delegatorId.toString());

    Set<String> delegatorRoles = delegationHandlerValidator.extractRoles(user);
    body.put("delegator_id", delegatorId);


    try {
      delegationHandlerValidator.validateCreateDelegationGrantBody(delegatorId, delegatorRoles,body);
    } catch (DxBadRequestException | DxForbiddenException e) {
      ctx.fail(e);
      return;
    }

//    DelegationGrant delegationGrant = DelegationGrant.fromJson(body);
    JsonArray rolesConstraints = body.getJsonArray("roles");


    delegationService.createDelegationGrant(body, delegatorRoles,rolesConstraints)
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
//    JsonObject body = ctx.body().asJsonObject();
//    UUID requesterId = UUID.fromString(ctx.user().subject());
//
//    UUID delegationId = UUID.fromString(body.getString("delegation_id"));
//
//    LOGGER.info("Incoming request body: {}", body.encodePrettily());
//
//    try {
//      delegationHandlerValidator.validateCreateUpdateDelegationRequestBody(body);
//    } catch (DxBadRequestException | DxForbiddenException e) {
//      ctx.fail(e);
//      return;
//    }
//
//    List<JsonObject> constraintsJson = delegationHandlerValidator.extractConstraintsforDelegationRequests(body);
//    body.put("delegate_id",requesterId);
//
//
//    // Fetch delegator from existing grant
//    delegationService.getDelegationGrantById(delegationId)
//      .compose(grant -> {
//        UUID delegatorId = grant.delegatorId();
//        DelegationUpdateRequest delegationRequest = DelegationUpdateRequest.fromJson(body);
//
//        return keycloakUserService.getUserById(delegatorId)
//          .compose(delegator -> {
//            Set<String> userRoles = new HashSet<>(delegator.roles());
//            return delegationService.createDelegationRequest(delegationRequest, userRoles, constraintsJson,delegatorId);
//          });
//      })
//      .onSuccess(request -> {
//        AuditLog auditLog = AuditingHelper.createAuditLog(
//          ctx.user(),
//          RoutingContextHelper.getRequestPath(ctx),
//          "POST",
//          "Create Delegation Request"
//        );
//        RoutingContextHelper.setAuditingLog(ctx, auditLog);
//        ResponseBuilder.sendSuccess(ctx, request, urnGenerator);
//      })
//      .onFailure(ctx::fail);
  }

  public void updateDelegationRequest(RoutingContext ctx) {
//    LOGGER.info("Handler: createUpdateDelegationRequest");
//
//    JsonObject body = ctx.body().asJsonObject();
//      if (body == null)
//      {   throw new DxBadRequestException("Request body is missing");
//      }
//
//      String status = body.getString("status");
//
//      UUID requestId = UUID.fromString(ctx.pathParam("id"));
//
//      if (status == null || status.isBlank()) {
//      throw new DxBadRequestException("Status is required");
//      }
//
//    // Only delegators should be able to update the status
//    User user = ctx.user();
//    UUID delegatorId = UUID.fromString(user.subject());
//
//    delegationService.updateDelegationRequestStatus(requestId, status, delegatorId)
//      .onSuccess(updatedRequest -> {
//        AuditLog auditLog = AuditingHelper.createAuditLog(
//          user,
//          RoutingContextHelper.getRequestPath(ctx),
//          "PUT",
//          "Delegation Request status updated to " + status
//        );
//        RoutingContextHelper.setAuditingLog(ctx, auditLog);
//        ResponseBuilder.sendSuccess(ctx, updatedRequest.toJson(), urnGenerator);
//        // emailComposer.sendDelegationRequestStatusEmail(requestId, status);
//      })
//      .onFailure(ctx::fail);
  }


  public void getDelegationRequest(RoutingContext ctx) {
    UUID delegationId = UUID.fromString(ctx.pathParam("id"));

    delegationService.getDelegationRequestsByDelegationId(delegationId.toString())
      .onSuccess(requests -> {
        ResponseBuilder.sendSuccess(ctx, requests, urnGenerator);
      })
      .onFailure(ctx::fail);
  }

  public void resolveUserId(RoutingContext ctx) {
    delegatorStrategyFactory
      .create(ctx)
      .resolveUserId(ctx)
      .onSuccess(resolvedUserId -> {
        ctx.put("resolvedUserId", resolvedUserId);   // stash for next handler
        ctx.next();                                   // proceed in chain
      })
      .onFailure(ctx::fail);
  }

  public void resolveOrgId(RoutingContext ctx) {
    String orgIdParam = ctx.pathParam("id");
    if(orgIdParam==null)
    {
      orgIdParam = ctx.pathParam("org_id");
    }
    delegatorStrategyFactory
      .create(ctx)
      .resolveOrgId(ctx, orgIdParam)
      .onSuccess(resolvedOrgId -> {
        ctx.put("resolvedOrgId", resolvedOrgId);
        ctx.next();
      })
      .onFailure(ctx::fail);
  }




}
