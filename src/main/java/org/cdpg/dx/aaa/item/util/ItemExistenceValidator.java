package org.cdpg.dx.aaa.item.util;

import static org.cdpg.dx.aaa.common.Constants.ACTIVE;
import static org.cdpg.dx.aaa.common.Constants.ID;
import static org.cdpg.dx.aaa.common.Constants.ITEM_CREATED_AT;
import static org.cdpg.dx.aaa.common.Constants.ITEM_STATUS;
import static org.cdpg.dx.aaa.common.Constants.ITEM_TYPE_AI_MODEL;
import static org.cdpg.dx.aaa.common.Constants.ITEM_TYPE_APPS;
import static org.cdpg.dx.aaa.common.Constants.ITEM_TYPE_DATA_BANK;
import static org.cdpg.dx.aaa.common.Constants.LAST_UPDATED;
import static org.cdpg.dx.aaa.common.Constants.NAME;
import static org.cdpg.dx.aaa.common.Constants.REQUEST_POST;
import static org.cdpg.dx.aaa.common.Constants.TYPE;
import static org.cdpg.dx.aaa.common.Constants.UUID_PATTERN;
import static org.cdpg.dx.aaa.common.Constants.VALIDATION_FAILURE_MSG;
import static org.cdpg.dx.database.elastic.util.Constants.COS_ADMIN;
import static org.cdpg.dx.database.elastic.util.Constants.DATA_UPLOAD_STATUS;
import static org.cdpg.dx.database.elastic.util.Constants.DETAIL_ITEM_NOT_FOUND;
import static org.cdpg.dx.database.elastic.util.Constants.MEDIA_URL;
import static org.cdpg.dx.database.elastic.util.Constants.ORG_ADMIN;
import static org.cdpg.dx.database.elastic.util.Constants.PENDING;
import static org.cdpg.dx.database.elastic.util.Constants.PUBLISH_STATUS;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.exception.DxConflictException;
import org.cdpg.dx.database.elastic.model.ElasticsearchResponse;

public class ItemExistenceValidator {

  private static final Logger LOGGER = LogManager.getLogger(ItemExistenceValidator.class);
  private final ItemService itemService;
  private final ItemService centralItemService;
  private final boolean isCentralCatEnabled;

  public ItemExistenceValidator(ItemService itemService,
                                ItemService centralItemService,
                                boolean isCentralCatEnabled) {
    this.itemService = itemService;
    this.centralItemService = centralItemService;
    this.isCentralCatEnabled = isCentralCatEnabled;
  }

  /**
   * Generates timestamp with timezone +05:30.
   */
  public static String getUtcDatetimeAsString() {
    DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ssZ");
    df.setTimeZone(TimeZone.getTimeZone("IST"));
    return df.format(new Date());
  }

  public static String getPrettyLastUpdatedForUI() {
    DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");
    DateTimeFormatter outputFormatter =
        DateTimeFormatter.ofPattern("dd MMMM, yyyy - hh:mm a", Locale.ENGLISH);

    // Format the current date in IST
    ZonedDateTime nowIst = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
    String istTime = nowIst.format(inputFormatter);

    // Parse using OffsetDateTime (handles the +0530 format correctly)
    OffsetDateTime offsetDateTime = OffsetDateTime.parse(istTime, inputFormatter);

    // Format to the desired output
    return offsetDateTime.format(outputFormatter);
  }

  private Future<Boolean> itemExists(
      ItemService service,
      String itemType,
      String name
  ) {
    return service.itemWithTheNameExists(itemType, name)
        .map(res -> getReturnTypeForValidation(res.toJson()).contains(itemType))
        .recover(err -> {
          if (DETAIL_ITEM_NOT_FOUND.equals(err.getMessage())) {
            return Future.succeededFuture(false);
          }
          return Future.failedFuture(new DxBadRequestException(VALIDATION_FAILURE_MSG));
        });
  }

  public void validateApps(JsonObject request, String method, Promise<JsonObject> promise) {
    validateAndAddId(request, promise);
    setCommonFields(request);

    if (!REQUEST_POST.equalsIgnoreCase(method)) {
      setPublishStatus(request);
      promise.complete(request);
      return;
    }

    String name = request.getString(NAME);

    Future<Boolean> localExists =
        itemExists(itemService, ITEM_TYPE_APPS, name);

    Future<Boolean> centralExists =
        isCentralCatEnabled
            ? itemExists(centralItemService, ITEM_TYPE_APPS, name)
            : Future.succeededFuture(false);

    Future.all(localExists, centralExists)
        .onFailure(promise::fail)
        .onSuccess(cf -> {

          boolean l = cf.resultAt(0);
          boolean c = cf.resultAt(1);

          if (l && c) {
            promise.fail(new DxConflictException(
                "Apps item with this name already exists"));
            return;
          }
          if (l) {
            promise.fail(new DxConflictException(
                "Apps item with this name already exists in local catalogue"));
            return;
          }
          if (c) {
            promise.fail(new DxConflictException(
                "Apps item with this name already exists in central catalogue"));
            return;
          }

          setPublishStatus(request);
          promise.complete(request);
        });
  }

  private void setPublishStatus(JsonObject request) {
    JsonArray roles = request.getJsonArray("roles", new JsonArray());
    LOGGER.debug("roles: " + roles.getList().toString());
    request.put(PUBLISH_STATUS,
        (roles.contains(ORG_ADMIN) || roles.contains(COS_ADMIN)) ? ACTIVE : PENDING
    );
    LOGGER.debug("publishStatus: " + request.getString(PUBLISH_STATUS));
  }

  public void validateAiModel(JsonObject request, String method, Promise<JsonObject> promise) {

    validateAndAddId(request, promise);
    setCommonFields(request);

    String name = request.getString(NAME);

    // -------- Local existence check --------
    Future<Boolean> localExistsFuture =
        itemService.itemWithTheNameExists(ITEM_TYPE_AI_MODEL, name)
            .map(res -> {
              String returnType = getReturnTypeForValidation(res.toJson());
              return returnType.contains(ITEM_TYPE_AI_MODEL);
            })
            .recover(err -> {
              if (DETAIL_ITEM_NOT_FOUND.equals(err.getMessage())) {
                return Future.succeededFuture(false);
              }
              LOGGER.debug("Fail: Local DB error: {}", err.getLocalizedMessage());
              return Future.failedFuture(VALIDATION_FAILURE_MSG);
            });

    // -------- Central existence check --------
    Future<Boolean> centralExistsFuture =
        isCentralCatEnabled
            ? centralItemService.itemWithTheNameExists(ITEM_TYPE_AI_MODEL, name)
            .map(res -> {
              String returnType = getReturnTypeForValidation(res.toJson());
              return returnType.contains(ITEM_TYPE_AI_MODEL);
            })
            .recover(err -> {
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
        .onSuccess(cf -> {

          boolean localExists = cf.resultAt(0);
          boolean centralExists = cf.resultAt(1);

          // ================= POST =================
          if (REQUEST_POST.equalsIgnoreCase(method)) {

            if (localExists && centralExists) {
              promise.fail(new DxConflictException(
                  "AI Model item with this name already exists"));
              return;
            }

            if (localExists) {
              promise.fail(new DxConflictException(
                  "AI Model item with this name already exists in local catalogue"));
              return;
            }

            if (centralExists) {
              promise.fail(new DxConflictException(
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

          boolean wasPreviouslyUploaded =
              extractDataUploadStatusFromES(request);

          // Sticky behavior
          request.put(
              DATA_UPLOAD_STATUS,
              mediaUrlPresent || wasPreviouslyUploaded
          );

          // Preserve publish status
          request.put(
              PUBLISH_STATUS,
              extractPublishStatusFromES(request)
          );

          promise.complete(request);
        });
  }

  public void validateDataBank(JsonObject request, String method, Promise<JsonObject> promise) {
    validateAndAddId(request, promise);

    if (!request.containsKey(ID)) {
      request.put(ID, UUID.randomUUID().toString());
    }

    setCommonFields(request);
    String name = request.getString(NAME);

    Future<Boolean> localExistsFuture =
        itemService.itemWithTheNameExists(ITEM_TYPE_DATA_BANK, name)
            .map(res -> {
              String returnType = getReturnTypeForValidation(res.toJson());
              return returnType.contains(ITEM_TYPE_DATA_BANK);
            })
            .recover(err -> {
              if (DETAIL_ITEM_NOT_FOUND.equals(err.getMessage())) {
                return Future.succeededFuture(false);
              }
              LOGGER.debug("Fail: DB Error: {}", err.getLocalizedMessage());
              return Future.failedFuture(VALIDATION_FAILURE_MSG);
            });

    Future<Boolean> centralExistsFuture =
        isCentralCatEnabled
            ? centralItemService.itemWithTheNameExists(ITEM_TYPE_DATA_BANK, name)
            .map(res -> {
              String returnType = getReturnTypeForValidation(res.toJson());
              return returnType.contains(ITEM_TYPE_DATA_BANK);
            })
            .recover(err -> {
              if (DETAIL_ITEM_NOT_FOUND.equals(err.getMessage())) {
                return Future.succeededFuture(false);
              }
              LOGGER.debug("Fail: DB Error: {}", err.getLocalizedMessage());
              return Future.failedFuture(VALIDATION_FAILURE_MSG);
            }) : Future.succeededFuture(false);

    Future.all(localExistsFuture, centralExistsFuture)
        .onFailure(promise::fail)
        .onSuccess(cf -> {

          boolean localExists = cf.resultAt(0);
          boolean centralExists = cf.resultAt(1);

          // POST: existence conflicts
          if (REQUEST_POST.equalsIgnoreCase(method)) {

            if (localExists && centralExists) {
              LOGGER.error("Fail: DataBank item with the name {} already exists", name);
              promise.fail(new DxConflictException("DataBank item with this name already exists"));
              return;
            }

            if (localExists) {
              LOGGER.error("Fail: DataBank item with the name {} already exists in local cat",
                  name);
              promise.fail(new DxConflictException(
                  "Item with this name already exists in local catalogue"));
              return;
            }

            if (centralExists) {
              LOGGER.error("Fail: DataBank item with the name {} already exists in central cat",
                  name);
              promise.fail(new DxConflictException(
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
                DATA_UPLOAD_STATUS,
                mediaUrlPresent || extractDataUploadStatusFromES(request));
            request.put(PUBLISH_STATUS, extractPublishStatusFromES(request));
          }

          promise.complete(request);
        });
  }

  private void setCommonFields(JsonObject request) {
    request
        .put(ITEM_STATUS, ACTIVE)
        .put(LAST_UPDATED, getPrettyLastUpdatedForUI())
        .put(ITEM_CREATED_AT, getUtcDatetimeAsString());
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
      LOGGER.debug("id in the request body: " + id);

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

  private boolean extractMediaUrlFromES(JsonObject res) {
    try {
      return res != null && !res.getString(MEDIA_URL, "").isBlank();
    } catch (Exception e) {
      LOGGER.error("Error extracting mediaURL from ES {}", e.getMessage());
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

  private String getReturnTypeForValidation(JsonObject result) {
    return result.getJsonArray(TYPE).toString();
  }
}
