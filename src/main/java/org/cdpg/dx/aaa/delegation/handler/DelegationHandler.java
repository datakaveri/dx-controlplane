package org.cdpg.dx.aaa.delegation.handler;

import static org.cdpg.dx.aaa.delegation.util.Constants.*;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import java.util.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.audit.util.AuditingHelper;
import org.cdpg.dx.aaa.delegation.DelegationHandlerValidator;
import org.cdpg.dx.aaa.delegation.UpdatedGrantResponse;
import org.cdpg.dx.aaa.delegation.service.DelegationService;
import org.cdpg.dx.aaa.email.util.EmailComposer;
import org.cdpg.dx.aaa.user.service.UserService;
import org.cdpg.dx.auditing.model.AuditLog;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.common.util.RoutingContextHelper;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

public class DelegationHandler {

  private static final Logger LOGGER = LogManager.getLogger(DelegationHandler.class);
  private final DelegationService delegationService;
  private final EmailComposer emailComposer;
  private final URNGenerator urnGenerator;
  private final UserService userService;
  private final KeycloakUserService keycloakUserService;
  private final DelegationHandlerValidator delegationHandlerValidator;

  public DelegationHandler(
      DelegationService delegationService,
      EmailComposer emailComposer,
      UserService userService,
      URNGenerator urnGenerator,
      KeycloakUserService keycloakUserService) {
    this.delegationService = delegationService;
    this.emailComposer = emailComposer;
    this.userService = userService;
    this.urnGenerator = urnGenerator;
    this.keycloakUserService = keycloakUserService;
    this.delegationHandlerValidator = new DelegationHandlerValidator();
  }

  public void getDelegationGrant(RoutingContext ctx) {
    String delegationId = ctx.pathParam("id");

    delegationService
        .getDelegationGrantById(delegationId)
        .onSuccess(
            grant -> {
              if (grant == null) {
                ctx.fail(
                    new DxNotFoundException("Delegation grant not found with id: " + delegationId));
                return;
              }

              delegationService
                  .getDelegationScopeConstraints(delegationId)
                  .compose(constraints -> addKeycloakUserInfo(grant, constraints))
                  .onSuccess(
                      updatedGrant -> {
                        AuditLog auditLog =
                            AuditingHelper.createAuditLog(
                                ctx.user(),
                                RoutingContextHelper.getRequestPath(ctx),
                                "GET",
                                "Get Delegation Grant By ID");

                        RoutingContextHelper.setAuditingLog(ctx, auditLog);
                        ResponseBuilder.sendSuccess(ctx, updatedGrant.toJson(), urnGenerator);
                      })
                  .onFailure(ctx::fail);
            })
        .onFailure(ctx::fail);
  }

  private Future<UpdatedGrantResponse> addKeycloakUserInfo(
      JsonObject grant, List<JsonObject> constraints) {

    UUID delegatorId = UUID.fromString(grant.getString("delegatorId"));
    UUID delegateId = UUID.fromString(grant.getString("delegateId"));

    return keycloakUserService
        .getUserById(delegatorId)
        .compose(
            delegatorUser ->
                keycloakUserService
                    .getUserById(delegateId)
                    .map(
                        delegateUser -> {
                          JsonObject delegator =
                              new JsonObject()
                                  .put("delegatorId", delegatorUser.sub().toString())
                                  .put("delegatorFirstName", delegatorUser.givenName())
                                  .put("delegatorLastName", delegatorUser.familyName())
                                  .put("delegatorEmail", delegatorUser.email())
                                  .put("delegatorOrganization", delegatorUser.organisationName());

                          JsonObject delegate =
                              new JsonObject()
                                  .put("delegateId", delegateUser.sub().toString())
                                  .put("delegateFirstName", delegateUser.givenName())
                                  .put("delegateLastName", delegateUser.familyName())
                                  .put("delegateEmail", delegateUser.email())
                                  .put("delegateOrganization", delegateUser.organisationName());

                          return new UpdatedGrantResponse(grant, delegator, delegate, constraints);
                        }));
  }

  public void getAllDelegationsOfDelegate(RoutingContext ctx) {

    User user = ctx.user();
    UUID userId = UUID.fromString(user.subject());

    delegationService
        .getAllDelegationsOfDelegate(userId.toString())
        .onSuccess(
            res -> {
              AuditLog auditLog =
                  AuditingHelper.createAuditLog(
                      ctx.user(),
                      RoutingContextHelper.getRequestPath(ctx),
                      "GET",
                      "Get All Delegations of the delegate");

              RoutingContextHelper.setAuditingLog(ctx, auditLog);
              if (res == null || res.isEmpty()) {
                ResponseBuilder.sendSuccess(ctx, "No delegation found for this user", urnGenerator);
                return;
              }

              ResponseBuilder.sendSuccess(ctx, res, urnGenerator);
            })
        .onFailure(ctx::fail);
  }

  public void getAllDelegationsByDelegator(RoutingContext ctx) {

    User user = ctx.user();
    UUID userId = UUID.fromString(user.subject());

    delegationService
        .getAllDelegationsByDelegator(userId.toString())
        .onSuccess(
            res -> {
              AuditLog auditLog =
                  AuditingHelper.createAuditLog(
                      ctx.user(),
                      RoutingContextHelper.getRequestPath(ctx),
                      "GET",
                      "Get All Delegations made by the user who is the delegator");

              RoutingContextHelper.setAuditingLog(ctx, auditLog);
              if (res == null || res.isEmpty()) {
                ResponseBuilder.sendSuccess(ctx, "No delegation found for this user", urnGenerator);
                return;
              }

              ResponseBuilder.sendSuccess(ctx, res, urnGenerator);
            })
        .onFailure(ctx::fail);
  }

  public void deleteDelegationGrant(RoutingContext ctx) {

    User user = ctx.user();
    UUID userId = UUID.fromString(user.subject());

    UUID delegationId = UUID.fromString(ctx.pathParam("id"));

    delegationService
        .deleteDelegation(delegationId.toString(), userId.toString())
        .onSuccess(
            res -> {
              AuditLog auditLog =
                  AuditingHelper.createAuditLog(
                      ctx.user(),
                      RoutingContextHelper.getRequestPath(ctx),
                      "DELETE",
                      "Delete delegation");

              RoutingContextHelper.setAuditingLog(ctx, auditLog);

              JsonObject response =
                  new JsonObject()
                      .put("delegationId", delegationId.toString())
                      .put("status", "deleted");

              ResponseBuilder.sendSuccess(ctx, response, urnGenerator);
            })
        .onFailure(ctx::fail);
  }

  public void createDelegationGrant(RoutingContext ctx) {
    LOGGER.info("Handler: createDelegationGrant");

    DxUser user = RoutingContextHelper.fromPrincipal(ctx);
    LOGGER.info("user: {}", user.toJson());

    UUID delegatorId = user.sub();

    keycloakUserService
        .getUserById(delegatorId)
        .onSuccess(
            keycloakUser -> {
              String organizationId = keycloakUser.organisationId();

              if (organizationId == null || organizationId.isBlank()) {
                LOGGER.info("User organization information is not available");
              }

              JsonObject body = ctx.body().asJsonObject();

              body.put(DELEGATOR_ID, delegatorId.toString());
              body.put("delegatorId", delegatorId.toString());

              List<String> delegatorRoles = delegationHandlerValidator.extractRoles(user);

              try {
                delegationHandlerValidator.validateCreateDelegationGrantBody(
                    delegatorId, delegatorRoles, body);
              } catch (DxBadRequestException | DxForbiddenException e) {
                ctx.fail(e);
                return;
              }

              JsonArray rolesConstraints = body.getJsonArray("roles");

              delegationService
                  .createDelegationGrant(body, delegatorRoles, rolesConstraints, organizationId)
                  .onSuccess(
                      createdGrant -> {
                        AuditLog auditLog =
                            AuditingHelper.createAuditLog(
                                ctx.user(),
                                RoutingContextHelper.getRequestPath(ctx),
                                "POST",
                                "Delegation Grant Created");

                        RoutingContextHelper.setAuditingLog(ctx, auditLog);

                        UUID delegateId = UUID.fromString(createdGrant.getString("delegateId"));

                        emailComposer
                            .sendEmailForDelegationCreated(delegateId, delegatorId)
                            .onFailure(
                                err ->
                                    LOGGER.error(
                                        "Failed to send delegation creation email for delegation {}",
                                        createdGrant.getString("delegationId"),
                                        err));

                        ResponseBuilder.sendSuccess(ctx, createdGrant, urnGenerator);
                      })
                  .onFailure(ctx::fail);
            })
        .onFailure(
            err -> {
              LOGGER.error("Failed to fetch user {} from Keycloak", delegatorId, err);

              ctx.fail(new DxForbiddenException("Invalid user"));
            });
  }

  public void createUpdateDelegationRequest(RoutingContext ctx) {
    // TODO: Implementation pending — see git history for previous draft
  }

  public void updateDelegationRequest(RoutingContext ctx) {
    // TODO: Implementation pending — see git history for previous draft
  }

  public void getDelegationRequest(RoutingContext ctx) {
    UUID delegationId = UUID.fromString(ctx.pathParam("id"));

    delegationService
        .getDelegationRequestsByDelegationId(delegationId.toString())
        .onSuccess(
            requests -> {
              ResponseBuilder.sendSuccess(ctx, requests, urnGenerator);
            })
        .onFailure(ctx::fail);
  }

  public void getDelegatorRoles(RoutingContext ctx) {

    User user = ctx.user();
    UUID userId = UUID.fromString(user.subject());

    UUID delegatorId = UUID.fromString(ctx.queryParams().get("delegatorId"));

    delegationService
        .getDelegatorRoles(userId.toString(), delegatorId.toString())
        .onSuccess(
            res -> {
              AuditLog auditLog =
                  AuditingHelper.createAuditLog(
                      ctx.user(),
                      RoutingContextHelper.getRequestPath(ctx),
                      "GET",
                      "Get All Delegations made by the user who is the delegator");

              RoutingContextHelper.setAuditingLog(ctx, auditLog);
              if (res == null || res.isEmpty()) {
                ResponseBuilder.sendSuccess(ctx, "No delegation found for this user", urnGenerator);
              }

              ResponseBuilder.sendSuccess(ctx, res, urnGenerator);
            })
        .onFailure(ctx::fail);
  }

  public void appendDelegationConstraints(RoutingContext ctx) {

    String userId = ctx.user().subject();
    String delegationId = ctx.pathParam("id");

    JsonObject body = ctx.body().asJsonObject();
    JsonArray roles = body.getJsonArray("roles");

    if (roles == null || roles.isEmpty()) {
      ctx.fail(new DxBadRequestException("roles must not be empty"));
      return;
    }

    keycloakUserService
        .getUserById(UUID.fromString(userId))
        .onFailure(
            err -> {
              LOGGER.error("Failed to fetch user {} from Keycloak", userId, err);
              ctx.fail(new DxForbiddenException("Invalid user"));
            })
        .onSuccess(
            keycloakUser -> {
              String organizationId = keycloakUser.organisationId();

              if (organizationId == null || organizationId.isBlank()) {
                LOGGER.info("User organization is not available");
              }

              delegationService
                  .appendDelegationConstraints(delegationId, userId, roles, organizationId)
                  .onSuccess(
                      result -> {
                        UUID delegateId = UUID.fromString(result.getString("delegateId"));
                        UUID delegatorId = UUID.fromString(result.getString("delegatorId"));
                        result.remove("delegateId");
                        result.remove("delegatorId");

                        AuditLog auditLog =
                            AuditingHelper.createAuditLog(
                                ctx.user(),
                                RoutingContextHelper.getRequestPath(ctx),
                                "POST",
                                "Append Delegation Constraints");

                        RoutingContextHelper.setAuditingLog(ctx, auditLog);

                        emailComposer
                            .sendEmailForDelegationConstraintsAppended(delegateId, delegatorId)
                            .onFailure(
                                err ->
                                    LOGGER.error(
                                        "Failed to send delegation constraint update email for delegation {}",
                                        delegationId,
                                        err));

                        ResponseBuilder.sendSuccess(ctx, result, urnGenerator);
                      })
                  .onFailure(ctx::fail);
            });
  }

  public void removeDelegationConstraints(RoutingContext ctx) {

    String userId = ctx.user().subject();

    String delegationId = ctx.pathParam("id");

    JsonObject body = ctx.body().asJsonObject();

    JsonArray roles = body.getJsonArray("roles");

    if (roles == null || roles.isEmpty()) {
      ctx.fail(new DxBadRequestException("roles must not be empty"));
      return;
    }

    delegationService
        .removeDelegationConstraints(delegationId, userId, roles)
        .onSuccess(
            result -> {
              AuditLog auditLog =
                  AuditingHelper.createAuditLog(
                      ctx.user(),
                      RoutingContextHelper.getRequestPath(ctx),
                      "POST",
                      "Remove Delegation Constraints");

              RoutingContextHelper.setAuditingLog(ctx, auditLog);

              UUID delegateId = UUID.fromString(result.getString("delegateId"));
              UUID delegatorId = UUID.fromString(result.getString("delegatorId"));
              result.remove("delegateId");
              result.remove("delegatorId");
              emailComposer
                  .sendEmailForDelegationConstraintsRemoved(delegateId, delegatorId)
                  .onFailure(
                      err ->
                          LOGGER.error(
                              "Failed to send delegation constraint removal email for delegation {}",
                              delegationId,
                              err));

              ResponseBuilder.sendSuccess(ctx, result, urnGenerator);
            })
        .onFailure(ctx::fail);
  }

  public void rejectDelegationGrant(RoutingContext ctx) {

    User user = ctx.user();
    String delegateId = user.subject();

    String delegationId;

    try {
      delegationId = ctx.pathParam("id");
    } catch (IllegalArgumentException e) {
      ctx.fail(new DxBadRequestException("Invalid delegation ID"));
      return;
    }

    delegationService
        .rejectDelegation(delegationId, delegateId)
        .onSuccess(
            rejected -> {
              AuditLog auditLog =
                  AuditingHelper.createAuditLog(
                      ctx.user(),
                      RoutingContextHelper.getRequestPath(ctx),
                      "POST",
                      "Reject Delegation Grant");

              RoutingContextHelper.setAuditingLog(ctx, auditLog);

              // Send rejection notification to delegator
              emailComposer
                  .sendEmailForDelegationRejected(
                      UUID.fromString(rejected.getString("delegatorId")),
                      UUID.fromString(delegateId))
                  .onFailure(
                      err ->
                          LOGGER.error(
                              "Failed to send delegation rejection email for delegation {}",
                              delegationId,
                              err));

              JsonObject response =
                  new JsonObject().put("delegationId", delegationId).put("status", "rejected");

              ResponseBuilder.sendSuccess(ctx, response, urnGenerator);
            })
        .onFailure(ctx::fail);
  }
}
