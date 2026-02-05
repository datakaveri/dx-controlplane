package org.cdpg.dx.aaa.item.controller;

import static org.cdpg.dx.aaa.apiserver.config.ApiConstants.CONTEXT;
import static org.cdpg.dx.aaa.apiserver.config.ApiConstants.CREATE_ITEM;
import static org.cdpg.dx.aaa.apiserver.config.ApiConstants.DELETE_ITEM;
import static org.cdpg.dx.aaa.apiserver.config.ApiConstants.DID;
import static org.cdpg.dx.aaa.apiserver.config.ApiConstants.DOWNLOAD_SCRIPT;
import static org.cdpg.dx.aaa.apiserver.config.ApiConstants.GET_ITEM;
import static org.cdpg.dx.aaa.apiserver.config.ApiConstants.GET_ITEM_WITH_ACCESS;
import static org.cdpg.dx.aaa.apiserver.config.ApiConstants.IS_DELEGATOR;
import static org.cdpg.dx.aaa.apiserver.config.ApiConstants.PATCH_ITEM;
import static org.cdpg.dx.aaa.apiserver.config.ApiConstants.RESULT;
import static org.cdpg.dx.aaa.apiserver.config.ApiConstants.UPDATE_ITEM;
import static org.cdpg.dx.aaa.common.Constants.*;
import static org.cdpg.dx.auth.authorization.model.DxScope.COS_ADMIN_ACCESS;
import static org.cdpg.dx.auth.authorization.model.DxScope.ORG_ADMIN_ACCESS;
import static org.cdpg.dx.database.elastic.util.Constants.DATA_UPLOAD_STATUS;
import static org.cdpg.dx.database.elastic.util.Constants.VERIFIED_BY;
import static org.cdpg.dx.keycloak.config.KeycloakConstants.SCOPES;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.apiserver.ApiController;
import org.cdpg.dx.aaa.common.VerifyItemTypeAndRole;
import org.cdpg.dx.aaa.delegation.ItemOwnershipValidator;
import org.cdpg.dx.aaa.delegation.service.DelegationService;
import org.cdpg.dx.aaa.item.enums.ItemAuditOperation;
import org.cdpg.dx.aaa.item.model.Item;
import org.cdpg.dx.aaa.item.service.ItemFetchService;
import org.cdpg.dx.aaa.item.service.ItemRegistryService;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.item.service.ScriptGenerationService;
import org.cdpg.dx.aaa.item.util.*;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.auditing.v2.model.UserActivityAuditLogBuilder;
import org.cdpg.dx.auth.authentication.util.AccessValidator;
import org.cdpg.dx.auth.authorization.handler.AuthorizationHandler;
import org.cdpg.dx.auth.authorization.model.DxRole;
import org.cdpg.dx.auth.authorization.model.DxScope;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.exception.DxConflictException;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.exception.DxInternalServerErrorException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.exception.DxUnauthorizedException;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.common.util.RoutingContextHelper;

public class ItemController implements ApiController {
  private static final Logger LOGGER = LogManager.getLogger(ItemController.class);
  private final AuditingHandler auditingHandler;
  private final ItemService itemService;
  private final ItemService centralItemService;
  private final ItemFetchService itemFetchService;
  private final DelegationService delegationService;
  private final String vocContext;
  private final String uploadedBy;
  private final boolean isCentralCatEnabled;
  private final URNGenerator urnGenerator;

  private final ItemExistenceValidator itemExistenceValidator;
  private final ItemRegistryService itemRegistryService;
  private final ScriptGenerationService scriptGenerationService;
  private final ItemOwnershipValidator itemOwnershipValidator;
  private final VerifyItemTypeAndRole verifyItemTypeAndRole = new VerifyItemTypeAndRole();
  Handler<RoutingContext> patchItemAccessHandler =
      AuthorizationHandler.forRoles(DxRole.COS_ADMIN, DxRole.ORG_ADMIN, DxRole.PROVIDER);

  public ItemController(
      AuditingHandler auditingHandler,
      ItemService itemService,
      ItemOwnershipValidator itemOwnershipValidator,
      ItemService centralItemService,
      String vocContext,
      String uploadedBy,
      boolean isCentralCatEnabled,
      URNGenerator urnGenerator,
      ItemRegistryService itemRegistryService,
      DelegationService delegationService) {
    this.auditingHandler = auditingHandler;
    this.itemService = itemService;
    this.centralItemService = centralItemService;
    this.vocContext = vocContext;
    this.itemOwnershipValidator = itemOwnershipValidator;
    this.uploadedBy = uploadedBy;
    this.isCentralCatEnabled = isCentralCatEnabled;
    this.urnGenerator = urnGenerator;
    this.itemExistenceValidator =
        new ItemExistenceValidator(itemService, centralItemService, isCentralCatEnabled);
    this.itemRegistryService = itemRegistryService;
    this.scriptGenerationService = new ScriptGenerationService();
    this.itemFetchService =
        new ItemFetchService(itemService, centralItemService, isCentralCatEnabled);
    this.delegationService = delegationService;
  }

  @Override
  public void register(RouterBuilder builder) {
    builder
        .operation(CREATE_ITEM)
        .handler(auditingHandler::handleApiAudit)
        .handler(verifyItemTypeAndRole)
        .handler(this::handleCreateOrUpdateItem);

    builder
        .operation(GET_ITEM)
        .handler(auditingHandler::handleApiAudit)
        .handler(this::handleGetItem);

    builder
        .operation(DELETE_ITEM)
        .handler(auditingHandler::handleApiAudit)
        .handler(this::handleDeleteItem);

    builder
        .operation(UPDATE_ITEM)
        .handler(auditingHandler::handleApiAudit)
        .handler(verifyItemTypeAndRole)
        .handler(this::handleCreateOrUpdateItem);

    builder
        .operation(PATCH_ITEM)
        .handler(auditingHandler::handleApiAudit)
        .handler(patchItemAccessHandler)
        .handler(this::handlePatchItem);

    builder
        .operation(GET_ITEM_WITH_ACCESS)
        .handler(auditingHandler::handleApiAudit)
        .handler(this::handleGetItemWithAccess);

    builder
        .operation(DOWNLOAD_SCRIPT)
        .handler(auditingHandler::handleApiAudit)
        .handler(this::handleDownloadScript);

    LOGGER.debug("Item Controller registered");
  }

  private void handleCreateOrUpdateItem(RoutingContext ctx) {
    LOGGER.debug("Handling create/update item");

    User user = ctx.user();
    JsonObject userJson = user.principal();

    AccessValidator.validate(
        userJson,
        List.of( // primary roles (no scope check)
            DxRole.PROVIDER.getRole(), DxRole.COS_ADMIN.getRole()),
        List.of(
            DxScope.ASSET_MANAGEMENT.getScope(),
            COS_ADMIN_ACCESS.getScope(),
            DxScope.ORG_ADMIN_ACCESS.getScope()));

    JsonObject body = ctx.body().asJsonObject();

    String itemType = extractAndValidateItemType(ctx, body);
    if (itemType == null) {
      return;
    }

    JsonObject doc = injectKeycloakInfoIfApplicable(ctx, body, itemType);

    String method = ctx.request().method().toString();

    // Add @context only if user hasn't provided one
    if (!doc.containsKey(CONTEXT) || doc.getString(CONTEXT).isBlank()) {
      doc.put(CONTEXT, vocContext);
    }

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
    String id = ctx.queryParams().get(ID);

    if (id == null || id.isBlank()) {
      ctx.fail(new DxBadRequestException(DETAIL_ID_NOT_FOUND));
      return;
    }

    User user1 = ctx.user();
    JsonObject userJson = user1.principal();
    JsonArray scopes = userJson.getJsonArray(SCOPES);

    AccessValidator.validate(
        userJson,
        List.of( // primary roles (no scope check)
            DxRole.PROVIDER.getRole(), DxRole.COS_ADMIN.getRole()),
        List.of(
            DxScope.ASSET_MANAGEMENT.getScope(),
            COS_ADMIN_ACCESS.getScope(),
            DxScope.ORG_ADMIN_ACCESS.getScope()));

    DxUser user = RoutingContextHelper.fromPrincipal(ctx);
    String orgId = "";
    orgId = user.organisationId();
    String userId = "";
    userId = user.sub().toString();
    LOGGER.debug("Keycloak ID: {},12aa: {}", orgId, id);
    List<String> allowedRoles;
    allowedRoles = ctx.get("allowedRoles");
    JsonObject body = ctx.body().asJsonObject();
    LOGGER.debug("Patch item request body: {}", body);

    if (!allowedRoles.contains(DxRole.ORG_ADMIN.getRole())
        && !allowedRoles.contains(DxRole.COS_ADMIN.getRole())
        && !(allowedRoles.contains(DxRole.DELEGATE.getRole())
            && (scopes.contains(COS_ADMIN_ACCESS) || scopes.contains(ORG_ADMIN_ACCESS)))
        && (allowedRoles.contains(DxRole.PROVIDER.getRole())
            || allowedRoles.contains(DxRole.DELEGATE.getRole()))) {
      if (body.size() != 1 || !body.containsKey(DATA_UPLOAD_STATUS)) {
        ctx.fail(new DxForbiddenException("Providers can only patch dataUploadStatus field"));
        return;
      }
    }

    PatchItemRequest patchItemRequest = new PatchItemRequest(id, orgId, userId, body, allowedRoles);
    itemService
        .patchItem(patchItemRequest)
        .onSuccess(
            elasticsearchResponse -> {
              JsonObject itemJson = elasticsearchResponse.getSource();
              UserActivityAuditLogBuilder auditLogBuilder =
                  ItemAuditLogHelper.buildItemAudit(ctx, itemJson, ItemAuditOperation.UPDATE);
              RoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);
              ResponseBuilder.sendSuccess(
                  ctx,
                  "Success: Item patched successfully",
                  new JsonArray().add(new JsonObject().put(ID, id)),
                  this.urnGenerator);
            })
        .onFailure(
            err -> {
              LOGGER.error("Patch item failed", err);
              ctx.fail(err);
            });
  }

  private String extractAndValidateItemType(RoutingContext ctx, JsonObject body) {
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
      String name = ctx.user().principal().getString(NAME);
      String orgId = ctx.user().principal().getString(ORGANISATION_ID);
      body.put(PROVIDER_USER_ID, kcId);
      // Only set organizationId if it exists in token and not already provided in payload
      if (orgId != null && !orgId.isBlank()) {
        body.put(ORGANIZATION_ID, orgId);
      }
      if (orgName != null && !orgName.isBlank()) {
        body.put(ORGANIZATION, orgName);
      }
      if (name != null && !name.isBlank()) {
        body.put(VERIFIED_BY, name);
      }
      // Add uploadedBy only if user hasn't provided one
      if (!body.containsKey(UPLOADED_BY) || body.getString(UPLOADED_BY).isBlank()) {
        body.put(UPLOADED_BY, uploadedBy);
      }
      body.put(ROLES, ctx.user().principal().getJsonObject(REALM_ACCESS).getJsonArray(ROLES));
    }
    body.put(
        METRICS, new JsonObject().put(VIEWS, 0).put(DOWNLOADS, 0).put(LIKES, 0).put(DISLIKES, 0));

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
    if (cause instanceof DxConflictException || cause instanceof DxNotFoundException) {
      ctx.fail(cause);
      return;
    }

    if ("validation failed. Incorrect id".equalsIgnoreCase(msg)) {
      ctx.fail(new DxBadRequestException("Syntax of the UUID is incorrect"));
    } else {
      ctx.fail(new DxBadRequestException(msg));
    }
  }

  private void processItemCreationOrUpdate(RoutingContext ctx, String method, JsonObject body) {
    try {
      body.remove(ROLES);
      Item item = ItemFactory.parse(body);
      if (REQUEST_POST.equalsIgnoreCase(method)) {
        if (ITEM_TYPE_DATA_BANK.equals(ctx.get(ITEM_TYPE))) {
          handleDataBankCreate(ctx, item, body);
        } else {
          executeWithCentralCatalogue(
              isCentralCatEnabled,

              // Central create
              () -> centralItemService.createItem(item),

              // Local create
              () -> itemService.createItem(item),

              // Central rollback
              () -> centralItemService.deleteItem(item.getId()),
              ctx,
              res -> {
                UserActivityAuditLogBuilder auditLogBuilder =
                    ItemAuditLogHelper.buildItemAudit(
                        ctx, item.toJson(), ItemAuditOperation.UPLOAD);
                RoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

                ResponseBuilder.sendCreated(
                    ctx, "Success: Item created", item.toJson(), this.urnGenerator);
              });
        }
      } else {
        String subId = "";
        List<String> roles = new ArrayList<>();
        if (ctx.user() != null) {
          subId = ctx.user().principal().getString(SUB);

          JsonObject realmAccess = ctx.user().principal().getJsonObject(REALM_ACCESS);
          if (realmAccess != null && realmAccess.containsKey(ROLES)) {
            JsonArray rolesJson = realmAccess.getJsonArray(ROLES);
            roles = rolesJson.stream().map(Object::toString).collect(Collectors.toList());
          }
        }

        GetItemRequest request = new GetItemRequest(item.getId(), subId);
        request.setRoles(roles);

        itemFetchService
            .fetchForWrite(request)
            .onSuccess(
                existingItemSnapshot -> {
                  executeWithCentralCatalogue(
                      isCentralCatEnabled,

                      // Central update
                      () -> centralItemService.updateItem(item),

                      // Local update
                      () -> itemService.updateItem(item),

                      // rollback
                      () -> centralItemService.updateItem(existingItemSnapshot),
                      ctx,
                      res -> {
                        UserActivityAuditLogBuilder auditLogBuilder =
                            ItemAuditLogHelper.buildItemAudit(
                                ctx, item.toJson(), ItemAuditOperation.UPDATE);
                        RoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

                        ResponseBuilder.sendSuccess(ctx, item.toJson(), urnGenerator);
                      });
                })
            .onFailure(ctx::fail);
      }
    } catch (Exception e) {
      LOGGER.error("Failed to parse item into model", e);
      ctx.fail(new DxBadRequestException(e.getMessage()));
    }
  }

  private void handleDataBankCreate(RoutingContext ctx, Item item, JsonObject originalBody) {
    LOGGER.debug("Handling DataBank item creation with integrations");

    // Extract required data from RoutingContext
    String userId = ctx.user().principal().getString(SUB);
    String token = RoutingContextHelper.getToken(ctx);
    JsonObject dataDescriptor =
        ctx.getBodyAsJson().getJsonObject("dataDescriptor", new JsonObject());

    // Create DTO
    DataBankCreationRequest dataBankCreationRequest =
        new DataBankCreationRequest(userId, token, dataDescriptor, originalBody);

    itemRegistryService
        .createDataBankWithIntegrations(dataBankCreationRequest, item)
        .onSuccess(
            response -> {
              LOGGER.debug("DataBank item created successfully with integrations");
              UserActivityAuditLogBuilder auditLogBuilder =
                  ItemAuditLogHelper.buildItemAudit(ctx, item.toJson(), ItemAuditOperation.UPLOAD);
              RoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);
              ResponseBuilder.sendSuccess(ctx, response.toJson(), this.urnGenerator);
            })
        .onFailure(err -> ctx.fail(err));
  }

  private <T> void executeWithCentralCatalogue(
      boolean isCentralCatEnabled,
      Supplier<Future<T>> centralOp,
      Supplier<Future<T>> localOp,
      Runnable centralRollback,
      RoutingContext ctx,
      Handler<T> onSuccess) {
    if (!isCentralCatEnabled) {
      localOp.get().onSuccess(onSuccess).onFailure(err -> handleOperationError(ctx, err));
      return;
    }

    // Central (with one retry)
    centralOp
        .get()
        .recover(
            err -> {
              LOGGER.warn("Central catalogue failed, retrying once", err);
              return centralOp.get();
            })
        .onFailure(
            err -> {
              LOGGER.error("Central catalogue failed after retry");
              handleOperationError(ctx, err);
            })
        .onSuccess(
            centralRes -> {
              // Local
              localOp
                  .get()
                  .onSuccess(onSuccess)
                  .onFailure(
                      localErr -> {
                        LOGGER.error(
                            "Local operation failed, rolling back central catalogue", localErr);

                        // Rollback central
                        try {
                          centralRollback.run();
                        } catch (Exception rollbackErr) {
                          LOGGER.error("Central rollback failed", rollbackErr);
                        }

                        handleOperationError(ctx, localErr);
                      });
            });
  }

  private void handleOperationError(RoutingContext ctx, Throwable cause) {
    LOGGER.error("Item operation failed", cause);
    String msg = cause.getMessage();
    if (cause instanceof DxConflictException
        || cause instanceof DxNotFoundException
        || cause instanceof DxForbiddenException) {
      ctx.fail(cause);
      return;
    }
    ctx.fail(new DxBadRequestException(msg));
  }

  private void handleDeleteItem(RoutingContext ctx) {
    String id = ctx.queryParams().get(ID);

    if (id == null || id.isBlank()) {
      ctx.fail(new DxBadRequestException("Item ID is required"));
      return;
    }

    String subId = "";
    List<String> roles = new ArrayList<>();
    if (ctx.user() != null) {
      subId = ctx.user().principal().getString(SUB);

      JsonObject realmAccess = ctx.user().principal().getJsonObject(REALM_ACCESS);
      if (realmAccess != null && realmAccess.containsKey(ROLES)) {
        JsonArray rolesJson = realmAccess.getJsonArray(ROLES);
        roles = rolesJson.stream().map(Object::toString).collect(Collectors.toList());
      }
    }

    GetItemRequest request = new GetItemRequest(id, subId);
    request.setRoles(roles);
    // Fetch item once(for audit and rollback)
    itemService
        .getItem(request)
        .onFailure(
            err -> {
              LOGGER.error("Delete item failed", err);
              ctx.fail(err);
            })
        .onSuccess(
            getRes -> {
              // handle local not found
              if (getRes.getElasticsearchResponses() == null
                  || getRes.getElasticsearchResponses().isEmpty()
                  || getRes.getElasticsearchResponses().getFirst() == null
                  || getRes.getElasticsearchResponses().getFirst().isEmpty()) {

                ctx.fail(new DxNotFoundException("Item not found for deletion"));
                return;
              }
              JsonObject itemJson = getRes.getElasticsearchResponses().getFirst();
              Item itemSnapshot = ItemFactory.parse(itemJson);

              executeWithCentralCatalogue(
                  isCentralCatEnabled,

                  // Central delete
                  () -> centralItemService.deleteItem(id),

                  // Local delete
                  () -> itemService.deleteItem(id),

                  // Central rollback → re-create item
                  () -> centralItemService.createItem(itemSnapshot),
                  ctx,
                  res -> {
                    UserActivityAuditLogBuilder auditLogBuilder =
                        ItemAuditLogHelper.buildItemAudit(ctx, itemJson, ItemAuditOperation.DELETE);
                    RoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);
                    ResponseBuilder.sendSuccess(
                        ctx, "Success: Item deleted successfully", this.urnGenerator);
                  });
            });
  }

  private void handleGetItem(RoutingContext ctx) {
    String itemId = ctx.queryParams().get("id");
    LOGGER.debug("Received GET request for item with ID '{}'", itemId);

    if (itemId == null || itemId.isBlank()) {
      ctx.fail(new DxBadRequestException("Item ID is required"));
      return;
    }

    String subId = "";
    List<String> roles = new ArrayList<>();
    if (ctx.user() != null) {
      subId = ctx.user().principal().getString(SUB);

      JsonObject realmAccess = ctx.user().principal().getJsonObject(REALM_ACCESS);
      if (realmAccess != null && realmAccess.containsKey(ROLES)) {
        JsonArray rolesJson = realmAccess.getJsonArray(ROLES);
        roles = rolesJson.stream().map(Object::toString).collect(Collectors.toList());
      }
    }

    GetItemRequest request = new GetItemRequest(itemId, subId);
    request.setRoles(roles);
    itemService
        .getItem(request)
        .onSuccess(
            responseModel -> {
              if (responseModel.getTotalHits() == 0) {
                LOGGER.error("Fail: Item not found");
                ctx.fail(new DxNotFoundException("doc doesn't exist"));
              } else {
                LOGGER.debug("Item retrieved successfully for ID '{}'", itemId);
                JsonObject itemJson =
                    responseModel.getResponse().getJsonArray(RESULTS).getJsonObject(0);
                if (ctx.user() != null) {
                  UserActivityAuditLogBuilder auditLogBuilder =
                      ItemAuditLogHelper.buildItemAudit(ctx, itemJson, ItemAuditOperation.VIEW);
                  RoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);
                }

                ResponseBuilder.sendSuccess(
                    ctx,
                    "Success: Item fetched successfully",
                    responseModel.getResponse().getJsonArray(RESULTS),
                    responseModel.getPaginationInfo(),
                    this.urnGenerator);
              }
            })
        .onFailure(
            err -> {
              if (err instanceof DxForbiddenException) {
                ctx.fail(err); // failure handler should map to 403
              } else {
                LOGGER.error("Error retrieving item with ID '{}': {}", itemId, err.getMessage());
                ctx.fail(new DxInternalServerErrorException(err.getMessage()));
              }
            });
  }

  private void handleGetItemWithAccess(RoutingContext routingContext) {
    String token = null;
    try {
      token = RoutingContextHelper.getToken(routingContext);
    } catch (Exception e) {
      LOGGER.warn("No token present or invalid token, may be anonymous access");
    }

    String itemId = routingContext.queryParams().get(ID);
    LOGGER.debug("Received GET request for item with ID '{}'", itemId);
    if (itemId == null || itemId.isBlank()) {
      routingContext.fail(new DxBadRequestException("Item ID is required"));
      return;
    }

    DxUser dxUser = null;
    String subId = null;
    List<String> roles = Collections.emptyList();
    String did = null;

    if (token != null) {
      dxUser = RoutingContextHelper.fromPrincipal(routingContext);
      subId = dxUser.sub().toString();
      roles = dxUser.roles();
      did = dxUser.did(); // default: from token (access-token case)
    }

    GetItemRequest request = new GetItemRequest(itemId, subId);
    request.setRoles(roles);
    request.setToken(token);
    request.setDid(did);

    boolean isDelegator = Boolean.parseBoolean(routingContext.queryParams().get(IS_DELEGATOR));
    String didFromParam = routingContext.queryParams().get(DID);

    // ---------------- Delegator flow ----------------
    if (isDelegator) {
      if (didFromParam == null || didFromParam.isBlank()) {
        routingContext.fail(new DxBadRequestException("did is mandatory when isDelegator is true"));
        return;
      }

      if (dxUser == null) {
        routingContext.fail(
            new DxUnauthorizedException("Identity token required for delegator access"));
        return;
      }

      delegationService
          .checkItemAccess(subId, didFromParam)
          .onSuccess(
              response -> {
                JsonArray result = response.getJsonArray(RESULT);

                // Defensive check
                if (result == null) {
                  routingContext.fail(new DxForbiddenException("Invalid delegation response"));
                  return;
                }

                // "*" means all items allowed
                if (!result.contains("*") && !result.contains(itemId)) {
                  routingContext.fail(
                      new DxForbiddenException("Delegator not authorized for this item"));
                  return;
                }

                // Delegation validated — override did
                request.setDid(didFromParam);
                executeGetItem(request, routingContext);
              })
          .onFailure(
              err -> {
                LOGGER.debug("Delegation access check failed", err);
                routingContext.fail(new DxForbiddenException(err.getMessage()));
              });
      return;
    }
    // ---------------- Normal flow ----------------
    executeGetItem(request, routingContext);
  }

  private void executeGetItem(GetItemRequest request, RoutingContext routingContext) {
    String itemId = routingContext.queryParams().get(ID);
    itemService
        .getItemWithAccessChecks(request)
        .onSuccess(
            responseModel -> {
              if (responseModel.getTotalHits() == 0) {
                LOGGER.error("Fail: Item not found");
                routingContext.fail(new DxNotFoundException("doc doesn't exist"));
              } else {
                LOGGER.debug("Item retrieved successfully for ID '{}'", itemId);
                ResponseBuilder.sendSuccess(
                    routingContext,
                    "Success: Item fetched successfully",
                    responseModel.getResponse().getJsonArray(RESULTS),
                    responseModel.getPaginationInfo(),
                    this.urnGenerator);
              }
            })
        .onFailure(
            err -> {
              if (err instanceof DxForbiddenException) {
                routingContext.fail(err); // failure handler should map to 403
              } else {
                LOGGER.error("Error retrieving item with ID '{}': {}", itemId, err.getMessage());
                routingContext.fail(err);
              }
            });
  }

  private void handleDownloadScript(RoutingContext ctx) {
    LOGGER.debug("Handling script download request");

    String filename = ctx.request().getParam("filename");
    if (filename == null || filename.isBlank()) {
      LOGGER.error("Missing filename parameter");
      ctx.fail(new DxBadRequestException("Filename parameter is required"));
      return;
    }

    // Security: Only allow .py files and prevent directory traversal
    if (!filename.endsWith(".py")
        || filename.contains("..")
        || filename.contains("/")
        || filename.contains("\\")) {
      LOGGER.error("Invalid filename: {}", filename);
      ctx.fail(new DxBadRequestException("Invalid filename"));
      return;
    }

    Path filePath = Paths.get("generated_scripts", filename);

    if (!Files.exists(filePath)) {
      LOGGER.error("Script file not found: {}", filePath);
      ctx.fail(new DxNotFoundException("Script file not found"));
      return;
    }

    HttpServerResponse response = ctx.response();
    prepareScriptDownloadResponse(response, filename);

    response
        .sendFile(filePath.toString())
        .onSuccess(v -> LOGGER.info("Script file downloaded successfully: {}", filename))
        .onFailure(
            err -> {
              LOGGER.error("Error reading script file: {}", filename, err);
              ctx.fail(new DxInternalServerErrorException("Error reading script file"));
            });
  }

  private void prepareScriptDownloadResponse(HttpServerResponse response, String filename) {
    response
        .putHeader("Access-Control-Allow-Origin", "*")
        .putHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
        .putHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
        .putHeader("Content-Type", "text/x-python")
        .putHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
  }
}
