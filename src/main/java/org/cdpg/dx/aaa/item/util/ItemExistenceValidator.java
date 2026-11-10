package org.cdpg.dx.aaa.item.util;

import static org.cdpg.dx.aaa.common.Constants.ACTIVE;
import static org.cdpg.dx.aaa.common.Constants.ID;
import static org.cdpg.dx.aaa.common.Constants.CREATED_BY;
import static org.cdpg.dx.aaa.common.Constants.ITEM_CREATED_AT;
import static org.cdpg.dx.aaa.common.Constants.ITEM_STATUS;
import static org.cdpg.dx.aaa.common.Constants.LAST_UPDATED;
import static org.cdpg.dx.aaa.common.Constants.METRICS;
import static org.cdpg.dx.aaa.common.Constants.NAME;
import static org.cdpg.dx.aaa.common.Constants.PROVIDER_USER_ID;
import static org.cdpg.dx.aaa.common.Constants.REQUEST_POST;
import static org.cdpg.dx.aaa.common.Constants.REQUEST_PUT;
import static org.cdpg.dx.aaa.common.Constants.SCOPES;
import static org.cdpg.dx.aaa.common.Constants.TYPE;
import static org.cdpg.dx.aaa.common.Constants.UNVERIFIED;
import static org.cdpg.dx.aaa.common.Constants.UUID_PATTERN;
import static org.cdpg.dx.aaa.common.Constants.VALIDATION_FAILURE_MSG;
import static org.cdpg.dx.aaa.common.Constants.VERIFIED;
import static org.cdpg.dx.database.elastic.util.Constants.DATA_UPLOAD_STATUS;
import static org.cdpg.dx.database.elastic.util.Constants.DETAIL_ITEM_NOT_FOUND;
import static org.cdpg.dx.database.elastic.util.Constants.MEDIA_URL;
import static org.cdpg.dx.database.elastic.util.Constants.PENDING;
import static org.cdpg.dx.database.elastic.util.Constants.PUBLISH_STATUS;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.common.ItemType;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.auth.model.Scopes;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.exception.DxConflictException;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.exception.DxNotFoundException;

public class ItemExistenceValidator {

  private static final Logger LOGGER = LogManager.getLogger(ItemExistenceValidator.class);
  private final ItemService itemService;
  private final ItemService centralItemService;
  private final boolean isCentralCatEnabled;

  public ItemExistenceValidator(
      ItemService itemService, ItemService centralItemService, boolean isCentralCatEnabled) {
    this.itemService = itemService;
    this.centralItemService = centralItemService;
    this.isCentralCatEnabled = isCentralCatEnabled;
  }

  /** Generates timestamp with timezone +05:30. */
  public static String getUtcDatetimeAsString() {
    DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
    df.setTimeZone(TimeZone.getTimeZone("IST"));
    return df.format(new Date());
  }

  private Future<Boolean> itemExists(ItemService service, String itemType, String name) {
    return service
        .itemWithTheNameExists(itemType, name)
        .map(res -> isOfType(res.toJson(), itemType))
        .recover(
            err -> {
              if (DETAIL_ITEM_NOT_FOUND.equals(err.getMessage())) {
                return Future.succeededFuture(false);
              }
              return Future.failedFuture(new DxBadRequestException(VALIDATION_FAILURE_MSG));
            });
  }

  public void validateApps(String userId, JsonObject request, String method, Promise<JsonObject> promise) {
    validateAndAddId(request, promise);
    setCommonFields(request, method);

    if (!REQUEST_POST.equalsIgnoreCase(method)) {
      GetItemRequest getItemRequest = new GetItemRequest(request.getString(ID), userId);
      itemService
          .getItem(getItemRequest)
          .onFailure(promise::fail)
          .onSuccess(
              responseModel -> {
                if (responseModel.getTotalHits() == 0) {
                  promise.fail(
                      new DxNotFoundException(
                          "Apps item not found with id " + request.getString(ID)));
                  return;
                }
                preserveImmutableFields(
                    request, responseModel.getElasticsearchResponses().getFirst());

                promise.complete(request);
              });

      return;
    }

    String name = request.getString(NAME);

    Future<Boolean> localExists = itemExists(itemService, ItemType.APPS.getTypeValue(), name);

    Future<Boolean> centralExists =
        isCentralCatEnabled
            ? itemExists(centralItemService, ItemType.APPS.getTypeValue(), name)
            : Future.succeededFuture(false);

    Future.all(localExists, centralExists)
        .onFailure(promise::fail)
        .onSuccess(
            cf -> {
              boolean l = cf.resultAt(0);
              boolean c = cf.resultAt(1);

              if (l && c) {
                promise.fail(new DxConflictException("Apps item with this name already exists"));
                return;
              }
              if (l) {
                promise.fail(
                    new DxConflictException(
                        "Apps item with this name already exists in local catalogue"));
                return;
              }
              if (c) {
                promise.fail(
                    new DxConflictException(
                        "Apps item with this name already exists in central catalogue"));
                return;
              }

              setPublishStatus(request);
              promise.complete(request);
            });
  }

  private void setPublishStatus(JsonObject request) {
    JsonArray scopes = request.getJsonArray(SCOPES, new JsonArray());
    request.put(
        PUBLISH_STATUS,
        (scopes.contains(Scopes.ASSET_PUBLISH)) ? ACTIVE : PENDING);
  }

  public void validateAiModel(String userId, JsonObject request, String method,
                              Promise<JsonObject> promise) {

    validateAndAddId(request, promise);
    setCommonFields(request, method);

    if (!REQUEST_POST.equalsIgnoreCase(method)) {
      GetItemRequest getItemRequest = new GetItemRequest(request.getString(ID), userId);
      itemService
          .getItem(getItemRequest)
          .onFailure(promise::fail)
          .onSuccess(
              responseModel -> {
                if (responseModel.getTotalHits() == 0) {
                  promise.fail(
                      new DxNotFoundException(
                          "Apps item not found with id " + request.getString(ID)));
                  return;
                }
                preserveImmutableFields(
                    request, responseModel.getElasticsearchResponses().getFirst());

                promise.complete(request);
              });

      return;
    }

    String name = request.getString(NAME);

    // -------- Local existence check --------
    Future<Boolean> localExistsFuture =
        itemService
            .itemWithTheNameExists(ItemType.AI_MODEL.getTypeValue(), name)
            .map(
                res -> isOfType(res.toJson(), ItemType.AI_MODEL.getTypeValue()))
            .recover(
                err -> {
                  if (DETAIL_ITEM_NOT_FOUND.equals(err.getMessage())) {
                    return Future.succeededFuture(false);
                  }
                  LOGGER.debug("Fail: Local DB error: {}", err.getLocalizedMessage());
                  return Future.failedFuture(VALIDATION_FAILURE_MSG);
                });

    // -------- Central existence check --------
    Future<Boolean> centralExistsFuture =
        isCentralCatEnabled
            ? centralItemService
                .itemWithTheNameExists(ItemType.AI_MODEL.getTypeValue(), name)
                .map(
                    res -> isOfType(res.toJson(), ItemType.AI_MODEL.getTypeValue()))
                .recover(
                    err -> {
                      if (DETAIL_ITEM_NOT_FOUND.equals(err.getMessage())) {
                        return Future.succeededFuture(false);
                      }
                      LOGGER.debug("Fail: Central DB error: {}", err.getLocalizedMessage());
                      return Future.failedFuture(VALIDATION_FAILURE_MSG);
                    })
            : Future.succeededFuture(false);

    // -------- Combine results --------
    Future.all(localExistsFuture, centralExistsFuture)
        .onFailure(promise::fail)
        .onSuccess(
            cf -> {
              boolean localExists = cf.resultAt(0);
              boolean centralExists = cf.resultAt(1);

              // ================= POST =================
              if (REQUEST_POST.equalsIgnoreCase(method)) {

                if (localExists && centralExists) {
                  promise.fail(
                      new DxConflictException("AI Model item with this name already exists"));
                  return;
                }

                if (localExists) {
                  promise.fail(
                      new DxConflictException(
                          "AI Model item with this name already exists in local catalogue"));
                  return;
                }

                if (centralExists) {
                  promise.fail(
                      new DxConflictException(
                          "AI Model item with this name already exists in central catalogue"));
                  return;
                }

                // Not present anywhere → proceed
                boolean mediaUrlPresent =
                    request.containsKey(MEDIA_URL) && !request.getString(MEDIA_URL).isBlank();

                request.put(DATA_UPLOAD_STATUS, mediaUrlPresent);
                setPublishStatus(request);
                promise.complete(request);
                return;
              }

              // ================= PUT / PATCH =================
              boolean mediaUrlPresent =
                  request.containsKey(MEDIA_URL) && !request.getString(MEDIA_URL).isBlank();

              boolean wasPreviouslyUploaded = extractDataUploadStatusFromES(request);

              // Sticky behavior
              request.put(DATA_UPLOAD_STATUS, mediaUrlPresent || wasPreviouslyUploaded);

              // Preserve publish status
              request.put(PUBLISH_STATUS, extractPublishStatusFromES(request));

              promise.complete(request);
            });
  }

  public void validateDataBank(String userId, JsonObject request, String method,
                               Promise<JsonObject> promise) {
    validateAndAddId(request, promise);
    setCommonFields(request, method);

    if (!REQUEST_POST.equalsIgnoreCase(method)) {
      GetItemRequest getItemRequest = new GetItemRequest(request.getString(ID), userId);
      itemService
          .getItem(getItemRequest)
          .onFailure(promise::fail)
          .onSuccess(
              responseModel -> {
                if (responseModel.getTotalHits() == 0) {
                  promise.fail(
                      new DxNotFoundException(
                          "DataBank item not found with id " + request.getString(ID)));
                  return;
                }
                preserveImmutableFields(
                    request, responseModel.getElasticsearchResponses().getFirst());

                promise.complete(request);
              });

      return;
    }

    String name = request.getString(NAME);

    Future<Boolean> localExistsFuture =
        itemService
            .itemWithTheNameExists(ItemType.DATA_BANK.getTypeValue(), name)
            .map(
                res -> isOfType(res.toJson(), ItemType.DATA_BANK.getTypeValue()))
            .recover(
                err -> {
                  if (DETAIL_ITEM_NOT_FOUND.equals(err.getMessage())) {
                    return Future.succeededFuture(false);
                  }
                  LOGGER.debug("Fail: DB Error: {}", err.getLocalizedMessage());
                  return Future.failedFuture(VALIDATION_FAILURE_MSG);
                });

    Future<Boolean> centralExistsFuture =
        isCentralCatEnabled
            ? centralItemService
                .itemWithTheNameExists(ItemType.DATA_BANK.getTypeValue(), name)
                .map(
                    res -> isOfType(res.toJson(), ItemType.DATA_BANK.getTypeValue()))
                .recover(
                    err -> {
                      if (DETAIL_ITEM_NOT_FOUND.equals(err.getMessage())) {
                        return Future.succeededFuture(false);
                      }
                      LOGGER.debug("Fail: DB Error: {}", err.getLocalizedMessage());
                      return Future.failedFuture(VALIDATION_FAILURE_MSG);
                    })
            : Future.succeededFuture(false);

    Future.all(localExistsFuture, centralExistsFuture)
        .onFailure(promise::fail)
        .onSuccess(
            cf -> {
              boolean localExists = cf.resultAt(0);
              boolean centralExists = cf.resultAt(1);

              // POST: existence conflicts
              if (REQUEST_POST.equalsIgnoreCase(method)) {

                if (localExists && centralExists) {
                  LOGGER.error("Fail: DataBank item with the name {} already exists", name);
                  promise.fail(
                      new DxConflictException("DataBank item with this name already exists"));
                  return;
                }

                if (localExists) {
                  LOGGER.error(
                      "Fail: DataBank item with the name {} already exists in local cat", name);
                  promise.fail(
                      new DxConflictException(
                          "Item with this name already exists in local catalogue"));
                  return;
                }

                if (centralExists) {
                  LOGGER.error(
                      "Fail: DataBank item with the name {} already exists in central cat", name);
                  promise.fail(
                      new DxConflictException(
                          "Item with this name already exists in central catalogue"));
                  return;
                }

                // Not present anywhere → proceed
                boolean mediaUrlPresent =
                    request.containsKey(MEDIA_URL) && !request.getString(MEDIA_URL).isBlank();

                request.put(DATA_UPLOAD_STATUS, mediaUrlPresent);
                setPublishStatus(request);
                promise.complete(request);
                return;
              }

              // PUT / PATCH (update)
              boolean mediaUrlPresent =
                  request.containsKey(MEDIA_URL) && !request.getString(MEDIA_URL).isBlank();

              if (localExists) {
                request.put(
                    DATA_UPLOAD_STATUS, mediaUrlPresent || extractDataUploadStatusFromES(request));
                request.put(PUBLISH_STATUS, extractPublishStatusFromES(request));
              }

              promise.complete(request);
            });
  }

  private void setCommonFields(JsonObject request, String method) {
    boolean hasOrgAssetPublishScope = hasOrgAssetPublishScope(request);

    if (REQUEST_POST.equalsIgnoreCase(method)) {
      request.put(ITEM_STATUS, hasOrgAssetPublishScope ? VERIFIED : UNVERIFIED);
      request.put(ITEM_CREATED_AT, getUtcDatetimeAsString());
    } else if (REQUEST_PUT.equalsIgnoreCase(method)) {
      String itemStatus = request.getString(ITEM_STATUS);

      if (VERIFIED.equalsIgnoreCase(itemStatus) && !hasOrgAssetPublishScope) {
        throw new DxForbiddenException(
            "Only users with org_asset_publish scope can update itemStatus to VERIFIED");
      }
    }

    request.put(LAST_UPDATED, getUtcDatetimeAsString());
  }

  private boolean hasOrgAssetPublishScope(JsonObject request) {
    return request.getJsonArray(SCOPES, new JsonArray()).contains(Scopes.ORG_ASSET_PUBLISH);
  }

  private void preserveImmutableFields(JsonObject request, JsonObject existing) {

    // Preserve itemCreatedAt
    request.put(ITEM_CREATED_AT, existing.getString(ITEM_CREATED_AT));

    // Preserve providerUserId
    request.put(PROVIDER_USER_ID, existing.getString(PROVIDER_USER_ID));

    // Preserve createdBy (absent on items created before it was tracked)
    String createdBy = existing.getString(CREATED_BY);
    if (createdBy != null) {
      request.put(CREATED_BY, createdBy);
    } else {
      request.remove(CREATED_BY);
    }

    // Preserve metrics
    request.put(METRICS, existing.getJsonObject(METRICS));

    // Preserve/update itemStatus
    if (hasOrgAssetPublishScope(request)) {
      request.put(ITEM_STATUS, VERIFIED);
    } else {
      request.put(ITEM_STATUS, existing.getString(ITEM_STATUS, UNVERIFIED));
    }

    // Preserve publishStatus
    request.put(PUBLISH_STATUS, existing.getString(PUBLISH_STATUS, PENDING));

    // Sticky dataUploadStatus
    boolean mediaUrlPresent =
        request.containsKey(MEDIA_URL) && !request.getString(MEDIA_URL).isBlank();

    boolean previouslyUploaded = existing.getBoolean(DATA_UPLOAD_STATUS, false);

    request.put(DATA_UPLOAD_STATUS, mediaUrlPresent || previouslyUploaded);
  }

  private void validateAndAddId(JsonObject request, Promise<JsonObject> promise) {
    validateId(request, promise);
    if (!request.containsKey(ID)) {
      UUID uuid = UUID.randomUUID();
      request.put(ID, uuid.toString());
    }
  }

  private void validateId(JsonObject request, Promise<JsonObject> promise) {
    if (request.containsKey(ID)) {
      String id = request.getString(ID);
      LOGGER.debug("id in the request body: {}", id);

      if (!isValidUuid(id)) {
        promise.fail("validation failed. Incorrect id");
      }
    }
  }

  private boolean isValidUuid(String uuidString) {
    return UUID_PATTERN.matcher(uuidString).matches();
  }

  private boolean extractDataUploadStatusFromES(JsonObject res) {
    try {
      return res != null && res.getBoolean(DATA_UPLOAD_STATUS, false);
    } catch (Exception e) {
      LOGGER.error("Error extracting dataUploadStatus from ES", e);
      return false;
    }
  }

  private String extractPublishStatusFromES(JsonObject res) {
    try {
      return res != null ? res.getString(PUBLISH_STATUS, PENDING) : PENDING;
    } catch (Exception e) {
      LOGGER.error("Error extracting publishStatus from ES", e);
      return PENDING;
    }
  }

  /** True if the item carries the given type under any accepted prefix. */
  private boolean isOfType(JsonObject result, String itemType) {
    ItemType type = ItemType.fromTypeValue(itemType);
    return result.getJsonArray(TYPE).stream().anyMatch(t -> type.matches(String.valueOf(t)));
  }
}
