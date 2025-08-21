package org.cdpg.dx.aaa.item.controller;

import static org.cdpg.dx.aaa.apiserver.config.ApiConstants.CONTEXT;
import static org.cdpg.dx.aaa.apiserver.config.ApiConstants.CREATE_ITEM;
import static org.cdpg.dx.aaa.apiserver.config.ApiConstants.DELETE_ITEM;
import static org.cdpg.dx.aaa.apiserver.config.ApiConstants.GET_ITEM;
import static org.cdpg.dx.aaa.apiserver.config.ApiConstants.PATCH_ITEM;
import static org.cdpg.dx.aaa.apiserver.config.ApiConstants.UPDATE_ITEM;
import static org.cdpg.dx.aaa.common.Constants.DEPARTMENT;
import static org.cdpg.dx.aaa.common.Constants.DETAIL_ID_NOT_FOUND;
import static org.cdpg.dx.aaa.common.Constants.HTTP_METHOD;
import static org.cdpg.dx.aaa.common.Constants.ITEM_TYPE;
import static org.cdpg.dx.aaa.common.Constants.ITEM_TYPES;
import static org.cdpg.dx.aaa.common.Constants.ITEM_TYPE_AI_MODEL;
import static org.cdpg.dx.aaa.common.Constants.ITEM_TYPE_APPS;
import static org.cdpg.dx.aaa.common.Constants.ITEM_TYPE_DATA_BANK;
import static org.cdpg.dx.aaa.common.Constants.ORGANISATION_ID;
import static org.cdpg.dx.aaa.common.Constants.ORG_NAME;
import static org.cdpg.dx.aaa.common.Constants.PROVIDER_USER_ID;
import static org.cdpg.dx.aaa.common.Constants.REQUEST_POST;
import static org.cdpg.dx.aaa.common.Constants.RESULTS;
import static org.cdpg.dx.aaa.common.Constants.SUB;
import static org.cdpg.dx.aaa.common.Constants.TYPE;
import static org.cdpg.dx.aaa.common.Constants.UPLOADED_BY;

import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import java.util.HashSet;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.apiserver.ApiController;
import org.cdpg.dx.aaa.common.CatAuditHelper;
import org.cdpg.dx.aaa.common.CheckIfTokenPresent;
import org.cdpg.dx.aaa.common.VerifyItemTypeAndRole;
import org.cdpg.dx.aaa.item.model.Item;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.item.util.GetItemRequest;
import org.cdpg.dx.aaa.item.util.ItemExistenceValidator;
import org.cdpg.dx.aaa.item.util.ItemFactory;
import org.cdpg.dx.aaa.item.util.PatchItemRequest;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.auditing.model.AuditLog;
import org.cdpg.dx.auth.authorization.handler.AuthorizationHandler;
import org.cdpg.dx.auth.authorization.model.DxRole;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.common.util.RoutingContextHelper;

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
        .handler(auditingHandler::handleApiAudit)
        .handler(checkIfTokenPresent)
        .handler(verifyItemTypeAndRole)
        .handler(this::handleCreateOrUpdateItem);

    builder
        .operation(GET_ITEM)
        .handler(auditingHandler::handleApiAudit)
        .handler(this::handleGetItem);

    builder
        .operation(DELETE_ITEM)
        .handler(auditingHandler::handleApiAudit)
        .handler(checkIfTokenPresent)
        .handler(this::handleDeleteItem);

    builder
        .operation(UPDATE_ITEM)
        .handler(auditingHandler::handleApiAudit)
        .handler(checkIfTokenPresent)
        .handler(verifyItemTypeAndRole)
        .handler(this::handleCreateOrUpdateItem);

    builder.operation(PATCH_ITEM)
        .handler(auditingHandler::handleApiAudit)
        .handler(checkIfTokenPresent)
        .handler(orgAdminAccessHandler)
        .handler(this::handlePatchItem);


    LOGGER.debug("Item Controller registered");
  }

  private void handleCreateOrUpdateItem(RoutingContext ctx) {
    LOGGER.debug("Handling create/update item");

    JsonObject body = ctx.body().asJsonObject();

    String itemType = extractAndValidateItemType(ctx, body);
    if (itemType == null) {
      return;
    }

    JsonObject doc = injectKeycloakInfoIfApplicable(ctx, body, itemType);

    String method = ctx.request().method().toString();
    doc.put(CONTEXT, vocContext);

    Promise<JsonObject> validationPromise = Promise.promise();
    validateItemExistence(ctx, itemType, doc, method, validationPromise);

    doc.remove(HTTP_METHOD);
    validationPromise
        .future()
        .onComplete(
            result -> {
              if (result.failed()) {
                handleValidationFailure(ctx, result.cause());
                return;
              }
              processItemCreationOrUpdate(ctx, method, result.result());
            });
  }

  private void handlePatchItem(RoutingContext ctx) {
    LOGGER.debug("Handling patch item");
    String id = ctx.queryParams().get("id");

    if (id == null || id.isBlank()) {
      ctx.fail(new DxBadRequestException(DETAIL_ID_NOT_FOUND));
      return;
    }
    String orgId = "";
    orgId = ctx.user().principal().getString(ORGANISATION_ID);
    LOGGER.debug("Keycloak ID: {},12aa: {}", orgId, id);
    JsonObject body = ctx.body().asJsonObject();
    LOGGER.debug("Patch item request body: {}", body);
    PatchItemRequest patchItemRequest = new PatchItemRequest(id, orgId, body);

    itemService.patchItem(patchItemRequest).onSuccess(elasticsearchResponse -> {

      AuditLog auditLog =
          CatAuditHelper.createAuditLog(elasticsearchResponse.getSource(), ctx.user(),
              "PATCH", RoutingContextHelper.getRequestPath(ctx));
      RoutingContextHelper.setAuditingLog(ctx, auditLog);

      ResponseBuilder.sendSuccess(ctx, "Success: Item patched successfully",
          new JsonArray().add(new JsonObject().put("id", id)));


    }).onFailure(err -> {
      LOGGER.error("Patch item failed", err);
      ctx.fail(err);
    });
  }

  private String extractAndValidateItemType(
      RoutingContext ctx, JsonObject body) {
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
      ctx.fail(new DxBadRequestException("Invalid type for item/type not present"));
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
      body.put("roles", ctx.user().principal().getJsonObject("realm_access").getJsonArray("roles"));
    }
    return body;
  }

  private void validateItemExistence(
      RoutingContext ctx,
      String itemType,
      JsonObject body,
      String method,
      Promise<JsonObject> promise) {
    switch (itemType) {
      case ITEM_TYPE_AI_MODEL -> itemExistenceValidator.validateAiModel(body, method, promise);
      case ITEM_TYPE_DATA_BANK -> itemExistenceValidator.validateDataBank(body, method, promise);
      case ITEM_TYPE_APPS -> itemExistenceValidator.validateApps(body, method, promise);
      default -> ctx.fail(new DxBadRequestException("Unsupported item type: " + itemType));
    }
  }

  private void handleValidationFailure(RoutingContext ctx, Throwable cause) {
    String msg = cause.getMessage();
    LOGGER.error("Item validation failed: {}", msg);
    if ("validation failed. Incorrect id".equalsIgnoreCase(msg)) {
      ctx.fail(new DxBadRequestException("Syntax of the UUID is incorrect"));
    } else {
      ctx.fail(new DxBadRequestException(msg));
    }
  }

  private void processItemCreationOrUpdate(RoutingContext ctx, String method, JsonObject body) {
    try {
      body.remove("roles");
      Item item = ItemFactory.parse(body);
      if (REQUEST_POST.equalsIgnoreCase(method)) {
        itemService
            .createItem(item)
            .onSuccess(res -> {

              AuditLog auditLog = CatAuditHelper.createAuditLog(item.toJson(), ctx.user(), method,
                  RoutingContextHelper.getRequestPath(ctx));
              RoutingContextHelper.setAuditingLog(ctx, auditLog);

              ResponseBuilder.sendCreated(ctx, "Success: Item created", item.toJson());

            })
            .onFailure(err -> handleOperationError(ctx, err));
      } else {
        itemService
            .updateItem(item)
            .onSuccess(
                res -> {
                  LOGGER.debug("Item updated successfully: {}", item);

                  AuditLog auditLog =
                      CatAuditHelper.createAuditLog(item.toJson(), ctx.user(), method,
                          RoutingContextHelper.getRequestPath(ctx));
                  RoutingContextHelper.setAuditingLog(ctx, auditLog);
                  ResponseBuilder.sendSuccess(ctx, item.toJson());


                })
            .onFailure(err -> handleOperationError(ctx, err));
      }
    } catch (Exception e) {
      LOGGER.error("Failed to parse item into model", e);
      ctx.fail(new DxBadRequestException(e.getMessage()));
    }
  }

  private void handleOperationError(RoutingContext ctx, Throwable err) {
    LOGGER.error("Item operation failed", err);
    ctx.fail(new DxBadRequestException(err.getMessage()));
  }

  private void handleDeleteItem(RoutingContext ctx) {
    String id = ctx.queryParams().get("id");

    if (id == null || id.isBlank()) {
      ctx.fail(new DxBadRequestException("Item ID is required"));
      return;
    }

    itemService
        .deleteItem(id)
        .onSuccess(
            elasticsearchResponse -> {
              AuditLog auditLog =
                  CatAuditHelper.createAuditLog(elasticsearchResponse.getSource(), ctx.user(),
                      "DELETE", RoutingContextHelper.getRequestPath(ctx));
              RoutingContextHelper.setAuditingLog(ctx, auditLog);
              ResponseBuilder.sendSuccess(ctx, "Success: Item deleted successfully");
            })


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
      routingContext.fail(new DxBadRequestException("Item ID is required"));
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
                routingContext.fail(new DxNotFoundException("doc doesn't exist"));
              } else {
                LOGGER.debug("Item retrieved successfully for ID '{}'", itemId);


                if (routingContext.user() != null) {
                  AuditLog auditLog = CatAuditHelper.createAuditLog(
                      responseModel.getResponse().getJsonArray(RESULTS).getJsonObject(0),
                      routingContext.user(),
                      "GET", RoutingContextHelper.getRequestPath(routingContext));
                  LOGGER.debug("Audit log created: {}", auditLog.toJson());
                  RoutingContextHelper.setAuditingLog(routingContext, auditLog);
                }

                ResponseBuilder.sendSuccess(
                    routingContext,
                    responseModel.getResponse().getJsonArray(RESULTS),
                    responseModel.getPaginationInfo());
              }
            })
        .onFailure(
            err -> {
              LOGGER.error("Error retrieving item with ID '{}': {}", itemId,
                  err.getMessage());
              routingContext.fail(err);
            });
  }
}
