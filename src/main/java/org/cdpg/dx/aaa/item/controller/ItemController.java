package org.cdpg.dx.aaa.item.controller;

import static org.cdpg.dx.aaa.apiserver.config.ApiConstants.*;
import static org.cdpg.dx.aaa.apiserver.config.ApiConstants.ID;
import static org.cdpg.dx.aaa.common.Constants.*;

import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import java.util.HashSet;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.apiserver.ApiController;
import org.cdpg.dx.aaa.common.CheckIfTokenPresent;
import org.cdpg.dx.aaa.common.VerifyItemTypeAndRole;
import org.cdpg.dx.aaa.item.model.Item;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.item.util.*;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.auth.authorization.handler.AuthorizationHandler;
import org.cdpg.dx.auth.authorization.model.DxRole;
import org.cdpg.dx.common.response.ResponseBuilder;

public class ItemController implements ApiController {
  private static final Logger LOGGER = LogManager.getLogger(ItemController.class);

  private final AuditingHandler auditingHandler;
  private final ItemService itemService;
  private final String vocContext;

  private final ItemExistenceValidator itemExistenceValidator;
  private final CheckIfTokenPresent checkIfTokenPresent = new CheckIfTokenPresent();
  private final VerifyItemTypeAndRole verifyItemTypeAndRole = new VerifyItemTypeAndRole();
  Handler<RoutingContext> orgAdminAccessHandler = AuthorizationHandler.forRoles(DxRole.ORG_ADMIN);

  public ItemController(
      AuditingHandler auditingHandler, ItemService itemService, String vocContext) {
    this.auditingHandler = auditingHandler;
    this.itemService = itemService;
    this.vocContext = vocContext;
    this.itemExistenceValidator = new ItemExistenceValidator(itemService);
  }

  @Override
  public void register(RouterBuilder builder) {
    builder
        .operation(CREATE_ITEM)
        .handler(checkIfTokenPresent)
        .handler(verifyItemTypeAndRole)
        .handler(this::handleCreateOrUpdateItem)
        .handler(auditingHandler::handleApiAudit);

    builder
        .operation(GET_ITEM)
        .handler(this::handleGetItem)
        .handler(auditingHandler::handleApiAudit);

    builder
        .operation(DELETE_ITEM)
        .handler(checkIfTokenPresent)
        .handler(this::handleDeleteItem)
        .handler(auditingHandler::handleApiAudit);

    builder
        .operation(UPDATE_ITEM)
        .handler(checkIfTokenPresent)
        .handler(verifyItemTypeAndRole)
        .handler(this::handleCreateOrUpdateItem)
        .handler(auditingHandler::handleApiAudit);

    builder.operation(PATCH_ITEM)
            .handler(checkIfTokenPresent)
            .handler(orgAdminAccessHandler)
            .handler(this::handlePatchItem)
            .handler(auditingHandler::handleApiAudit);

    LOGGER.debug("Item Controller registered");
  }

  private void handleCreateOrUpdateItem(RoutingContext ctx) {
    LOGGER.debug("Handling create/update item");
    HttpServerResponse response = ctx.response();

    JsonObject body = ctx.body().asJsonObject();

    String itemType = extractAndValidateItemType(ctx, body, response);
    if (itemType == null) return;

    JsonObject doc = injectKeycloakInfoIfApplicable(ctx, body, itemType);

    String method = ctx.request().method().toString();
    doc.put(CONTEXT, vocContext);

    Promise<JsonObject> validationPromise = Promise.promise();
    validateItemExistence(response, itemType, doc, method, validationPromise);

    doc.remove(HTTP_METHOD);
    validationPromise
        .future()
        .onComplete(
            result -> {
              if (result.failed()) {
                handleValidationFailure(response, result.cause());
                return;
              }
              processItemCreationOrUpdate(response, method, result.result());
            });
  }

  private void handlePatchItem(RoutingContext ctx) {
    LOGGER.debug("Handling patch item");
    String id = ctx.queryParams().get("id");

    if (id == null || id.isBlank()) {
      ctx.response().setStatusCode(400).
              end(new RespBuilder().withType(TYPE_INVALID_SYNTAX).
                      withTitle(TITLE_INVALID_SYNTAX).withDetail(DETAIL_ID_NOT_FOUND).getResponse());
      return;
    }
    String kcId = "";
    kcId = ctx.user().principal().getString(SUB);
    LOGGER.debug("Keycloak ID: {},12aa: {}", kcId,id);
    JsonObject body = ctx.body().asJsonObject();
    LOGGER.debug("Patch item request body: {}", body);
    PatchItemRequest patchItemRequest =new PatchItemRequest(id,kcId,body);

    itemService.patchItem(patchItemRequest).onSuccess(v -> ctx.response().setStatusCode(200).
            end(new RespBuilder().withType(TYPE_SUCCESS)
                    .withTitle(TITLE_SUCCESS).withResult(new JsonArray().add(new JsonObject().put(ID, id)))
                    .withDetail("Success: Item patched successfully").getResponse())).onFailure(err -> {
      LOGGER.error("Patch item failed", err);
      ctx.fail(err);
    });
  }
  private String extractAndValidateItemType(
      RoutingContext ctx, JsonObject body, HttpServerResponse response) {
    try {
      JsonArray typeArray = body.getJsonArray(TYPE);
      if (typeArray == null || typeArray.isEmpty()) {
        throw new IllegalArgumentException("Missing or empty 'type' field");
      }
      Set<String> type = new HashSet<>(typeArray.getList());
      type.retainAll(ITEM_TYPES);
      if (type.isEmpty()) {
        throw new IllegalArgumentException("No valid types found in 'type' field");
      }

      String itemType = type.toString().replaceAll("\\[", "").replaceAll("\\]", "");
      ctx.put(ITEM_TYPE, itemType);
      LOGGER.debug("Info: itemType = {}", itemType);
      return itemType;
    } catch (Exception e) {
      LOGGER.error("Invalid 'type' field", e);
      sendError(
          response,
          400,
          TYPE_INVALID_SCHEMA,
          TITLE_INVALID_SCHEMA,
          "Invalid type for item/type not present");
      return null;
    }
  }

  private JsonObject injectKeycloakInfoIfApplicable(
      RoutingContext ctx, JsonObject body, String itemType) {
    if (ITEM_TYPE_AI_MODEL.equals(itemType)
        || ITEM_TYPE_DATA_BANK.equals(itemType)
        || ITEM_TYPE_APPS.equals(itemType)) {

      String kcId = ctx.user().principal().getString(SUB);
      String orgName = ctx.user().principal().getString(ORG_NAME);
      body.put(PROVIDER_USER_ID, kcId).put(DEPARTMENT, orgName).put(UPLOADED_BY, orgName);
      body.put("roles", ctx.user().principal().getJsonObject("realm_access").getJsonArray("roles").add("org_admin"));
    }
    return body;
  }

  private void validateItemExistence(
      HttpServerResponse response,
      String itemType,
      JsonObject body,
      String method,
      Promise<JsonObject> promise) {
    switch (itemType) {
      case ITEM_TYPE_AI_MODEL -> itemExistenceValidator.validateAiModel(body, method, promise);
      case ITEM_TYPE_DATA_BANK -> itemExistenceValidator.validateDataBank(body, method, promise);
      case ITEM_TYPE_APPS -> itemExistenceValidator.validateApps(body, method, promise);
      default ->
          sendError(
              response,
              400,
              TYPE_INVALID_SCHEMA,
              TITLE_INVALID_SCHEMA,
              "Unsupported item type: " + itemType);
    }
  }

  private void handleValidationFailure(HttpServerResponse response, Throwable cause) {
    String msg = cause.getMessage();
    LOGGER.error("Item validation failed: {}", msg);
    if ("validation failed. Incorrect id".equalsIgnoreCase(msg)) {
      sendError(
          response, 400, TYPE_INVALID_UUID, TITLE_INVALID_UUID, "Syntax of the UUID is incorrect");
    } else {
      sendError(response, 400, TYPE_LINK_VALIDATION_FAILED, TITLE_LINK_VALIDATION_FAILED, msg);
    }
  }

  private void processItemCreationOrUpdate(
      HttpServerResponse response, String method, JsonObject body) {
    try {
      body.remove("roles");
      Item item = ItemFactory.parse(body);
      if (REQUEST_POST.equalsIgnoreCase(method)) {
        itemService
            .createItem(item)
            .onSuccess(res -> sendSuccess(response, 201, "Success: Item created", item.toJson()))
            .onFailure(err -> handleOperationError(response, err));
      } else {
        itemService
            .updateItem(item)
            .onSuccess(
                res -> {
                  LOGGER.debug("Item updated successfully: {}", item);
                  sendSuccess(response, 200, "Success: Item updated successfully", item.toJson());
                })
            .onFailure(err -> handleOperationError(response, err));
      }
    } catch (Exception e) {
      LOGGER.error("Failed to parse item into model", e);
      sendError(response, 400, TYPE_INVALID_SYNTAX, TITLE_INVALID_SYNTAX, e.getMessage());
    }
  }

  private void sendError(
      HttpServerResponse res, int status, String type, String title, String detail) {
    res.setStatusCode(status)
        .end(new RespBuilder().withType(type).withTitle(title).withDetail(detail).getResponse());
  }

  private void sendSuccess(HttpServerResponse res, int status, String detail, JsonObject doc) {
    res.setStatusCode(status)
        .end(
            new RespBuilder()
                .withType(TYPE_SUCCESS)
                .withTitle(TITLE_SUCCESS)
                .withDetail(detail)
                .withResult(doc)
                .getResponse());
  }

  private void handleOperationError(HttpServerResponse res, Throwable err) {
    LOGGER.error("Item operation failed", err);
    sendError(res, 400, TYPE_OPERATION_NOT_ALLOWED, TITLE_OPERATION_NOT_ALLOWED, err.getMessage());
  }

  private void handleDeleteItem(RoutingContext ctx) {
    String id = ctx.queryParams().get("id");

    if (id == null || id.isBlank()) {
      ctx.response()
          .setStatusCode(400)
          .end(
              new RespBuilder()
                  .withType(TYPE_INVALID_SYNTAX)
                  .withTitle(TITLE_INVALID_SYNTAX)
                  .withDetail(DETAIL_ID_NOT_FOUND)
                  .getResponse());
      return;
    }

    itemService
        .deleteItem(id)
        .onSuccess(
            v ->
                ctx.response()
                    .setStatusCode(200)
                    .end(
                        new RespBuilder()
                            .withType(TYPE_SUCCESS)
                            .withTitle(TITLE_SUCCESS)
                            .withResult(new JsonArray().add(new JsonObject().put(ID, id)))
                            .withDetail("Success: Item deleted successfully")
                            .getResponse()))
        .onFailure(
            err -> {
              LOGGER.error("Delete item failed", err);
              ctx.fail(err);
            });
  }

  private void handleGetItem(RoutingContext routingContext) {
    String itemId = routingContext.queryParams().get("id");
    LOGGER.debug("Received GET request for item with ID '{}'", itemId);

    if (itemId == null || itemId.isBlank()) {
      routingContext.fail(400, new IllegalArgumentException("Item ID is required"));
      return;
    }

    String subId = "";
    if (routingContext.user() != null) {
      subId = routingContext.user().principal().getString("sub");
    }

    GetItemRequest request = new GetItemRequest(itemId, subId);
    itemService
        .getItem(request)
        .onSuccess(
            responseModel -> {
              if (responseModel.getTotalHits() == 0) {
                LOGGER.error("Fail: Item not found");
                routingContext
                    .response()
                    .setStatusCode(404)
                    .end(
                        new RespBuilder()
                            .withType(TYPE_ITEM_NOT_FOUND)
                            .withTitle("error")
                            .withDetail("doc doesn't exist")
                            .withResult()
                            .getResponse());
              } else {
                LOGGER.debug("Item retrieved successfully for ID '{}'", itemId);
                ResponseBuilder.sendSuccess(
                    routingContext,
                    responseModel.getResponse().getJsonArray(RESULTS),
                    responseModel.getPaginationInfo());
              }
            })
        .onFailure(
            err -> {
              LOGGER.error("Error retrieving item with ID '{}': {}", itemId, err.getMessage());
              routingContext.fail(500, err);
            });
  }
}
