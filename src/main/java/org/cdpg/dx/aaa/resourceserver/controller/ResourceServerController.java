package org.cdpg.dx.aaa.resourceserver.controller;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.apiserver.ApiController;
import org.cdpg.dx.aaa.apiserver.config.ApiConstants;
import org.cdpg.dx.aaa.resourceserver.models.ResourceServer;
import org.cdpg.dx.aaa.resourceserver.service.ResourceServerService;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.auth.authorization.handler.AuthorizationHandler;
import org.cdpg.dx.auth.model.DxRole;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.exception.DxUnauthorizedException;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.common.util.RoutingContextHelper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.cdpg.dx.aaa.resourceserver.config.Constants.OWNER_ID;
import static org.cdpg.dx.aaa.resourceserver.config.Constants.ROLES;

public class ResourceServerController implements ApiController {
  private static final Logger LOGGER = LogManager.getLogger(ResourceServerController.class);

  private final ResourceServerService service;
  private final AuditingHandler auditingHandler;
  private final URNGenerator urnGenerator;
  Handler<RoutingContext> cosAdminAccessHandler = AuthorizationHandler.forRoles(DxRole.COS_ADMIN);
  Handler<RoutingContext> orgAndCosAdminAccessHandler =
      AuthorizationHandler.forRoles(DxRole.ORG_ADMIN, DxRole.COS_ADMIN);

  public ResourceServerController(
      ResourceServerService service, AuditingHandler auditingHandler, URNGenerator urnGenerator) {
    this.service = service;
    this.auditingHandler = auditingHandler;
    this.urnGenerator = urnGenerator;
  }

  @Override
  public void register(RouterBuilder builder) {
    builder
        .operation(ApiConstants.CREATE_RESOURCE_SERVER)
        .handler(auditingHandler::handleApiAudit)
        .handler(orgAndCosAdminAccessHandler)
        .handler(this::handleCreate);

    builder
        .operation(ApiConstants.LIST_RESOURCE_SERVERS)
        .handler(auditingHandler::handleApiAudit)
        .handler(orgAndCosAdminAccessHandler)
        .handler(this::handleList);

    builder
        .operation(ApiConstants.GET_RESOURCE_SERVER)
        .handler(auditingHandler::handleApiAudit)
        .handler(orgAndCosAdminAccessHandler)
        .handler(this::handleGet);

    //    builder.operation(ApiConstants.UPDATE_RESOURCE_SERVER)
    //      .handler(auditingHandler::handleApiAudit)
    //            .handler(orgAndCosAdminAccessHandler)
    //
    //            .handler(this::handleUpdate);

    builder
        .operation(ApiConstants.DELETE_RESOURCE_SERVER)
        .handler(auditingHandler::handleApiAudit)
        .handler(orgAndCosAdminAccessHandler)
        .handler(this::handleDelete);
  }

  private void handleCreate(RoutingContext ctx) {
    JsonObject requestBody = ctx.body().asJsonObject();
    DxUser user = RoutingContextHelper.fromPrincipal(ctx);
    JsonArray roles = new JsonArray(user.roles());
    LOGGER.debug("ROLES in controller {}", roles);

    requestBody.put(OWNER_ID, user.sub());

    requestBody.put(ROLES, roles);

    ResourceServer rs = ResourceServer.fromJson(requestBody);
    service
        .create(rs)
        .onSuccess(created -> ResponseBuilder.sendSuccess(ctx, created.toJson(), urnGenerator))
        .onFailure(
            err ->
                ctx.response()
                    .setStatusCode(400)
                    .end(new JsonObject().put("error", err.getMessage()).encode()));
  }

  private void handleList(RoutingContext ctx) {
    DxUser user = RoutingContextHelper.fromPrincipal(ctx);
    List<String> userRoles = user.roles();

    LOGGER.debug("User roles for resource server listing: {}", userRoles);

    String userRole = null;
    UUID userId = user.sub();

    if (userRoles.contains(DxRole.COS_ADMIN.value())) {
      userRole = DxRole.COS_ADMIN.value();
    } else if (userRoles.contains(DxRole.ORG_ADMIN.value())) {
      userRole = DxRole.ORG_ADMIN.value();
    }

    if (userRole == null) {
      LOGGER.warn("User does not have required role for resource server listing");
      ctx.fail(new DxUnauthorizedException("Not Authorized, should be a cos_admin or org_admin"));
      return;
    }

    service
        .getAllByRole(userRole, userId)
        .onSuccess(
            resourceServers -> {
              JsonArray responseArray = new JsonArray();
              resourceServers.forEach(rs -> responseArray.add(rs.toJson()));
              ResponseBuilder.sendSuccess(ctx, responseArray, urnGenerator);
            })
        .onFailure(
            err -> {
              LOGGER.error("Error fetching resource servers: {}", err.getMessage());
              ctx.fail(err);
            });
  }

  private void handleGet(RoutingContext ctx) {
    String id = ctx.request().getParam("id");
    service
        .get(UUID.fromString(id))
        .onSuccess(rsFound -> ResponseBuilder.sendSuccess(ctx, rsFound.toJson(), urnGenerator))
        .onFailure(err -> ctx.fail(err));
  }

  private void handleUpdate(RoutingContext ctx) {
    String id = ctx.request().getParam("id");
    JsonObject payload = ctx.body().asJsonObject();
    Map<String, Object> updates = payload.getMap();
    service
        .update(UUID.fromString(id), updates)
        .onSuccess(updatedResourceServer -> ResponseBuilder.sendSuccess(ctx, "", urnGenerator))
        .onFailure(
            err ->
                ctx.response()
                    .setStatusCode(400)
                    .end(new JsonObject().put("error", err.getMessage()).encode()));
  }

  private void handleDelete(RoutingContext ctx) {
    String id = ctx.request().getParam("id");
    DxUser user = RoutingContextHelper.fromPrincipal(ctx);
    List<String> userRoles = user.roles();
    UUID userId = user.sub();

    LOGGER.debug("User roles for resource server deletion: {}", userRoles);

    // Determine the user's role
    String userRole = null;
    if (userRoles.contains(DxRole.COS_ADMIN.value())) {
      userRole = DxRole.COS_ADMIN.value();
    } else if (userRoles.contains(DxRole.ORG_ADMIN.value())) {
      userRole = DxRole.ORG_ADMIN.value();
    }

    if (userRole == null) {
      ctx.fail(new DxUnauthorizedException("Not Authorized, should be a cos_admin or org_admin"));
      return;
    }

    service
        .deleteByRole(UUID.fromString(id), userRole, userId)
        .onSuccess(ok -> ResponseBuilder.sendNoContent(ctx, urnGenerator))
        .onFailure(ctx::fail);
  }
}
