package org.cdpg.dx.aaa.item.util;

import static org.cdpg.dx.aaa.common.Constants.*;
import static org.cdpg.dx.database.elastic.util.Constants.DATA_UPLOAD_STATUS;
import static org.cdpg.dx.database.elastic.util.Constants.DETAIL_ITEM_NOT_FOUND;
import static org.cdpg.dx.database.elastic.util.Constants.MEDIA_URL;
import static org.cdpg.dx.database.elastic.util.Constants.PENDING;
import static org.cdpg.dx.database.elastic.util.Constants.PUBLISH_STATUS;

import io.vertx.core.Promise;
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
import org.cdpg.dx.database.elastic.model.ElasticsearchResponse;

public class ItemExistenceValidator {

  private static final Logger LOGGER = LogManager.getLogger(ItemExistenceValidator.class);
  private final ItemService itemService;

  public ItemExistenceValidator(ItemService itemService) {
    this.itemService = itemService;
  }

  /** Generates timestamp with timezone +05:30. */
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

  public void validateApps(JsonObject request, String method, Promise<JsonObject> promise) {
    validateAndAddId(request, promise);

    setCommonFields(request);

    itemService
        .itemWithTheNameExists(ITEM_TYPE_APPS, request.getString(NAME))
        .onFailure(
            err -> {
              if (err.getMessage().equals(DETAIL_ITEM_NOT_FOUND)) {
                promise.complete(request);
              } else {
                LOGGER.debug("Fail: DB Error: " + err.getLocalizedMessage());
                promise.fail(VALIDATION_FAILURE_MSG);
              }
            })
        .onSuccess(
            res -> {
              if (REQUEST_POST.equalsIgnoreCase(method)
                  && ElasticsearchResponse.getTotalHits() > 0) {
                promise.fail("Fail: Apps item already exists");
              } else {
                promise.complete(request);
              }
            });
  }

  public void validateAiModel(JsonObject request, String method, Promise<JsonObject> promise) {
    validateAndAddId(request, promise);

    setCommonFields(request);

    itemService
        .itemWithTheNameExists(ITEM_TYPE_AI_MODEL, request.getString(NAME))
        .onFailure(
            err -> {
              if (err.getMessage().equals(DETAIL_ITEM_NOT_FOUND)) {
                // For POST, if not found, good to proceed
                if (REQUEST_POST.equalsIgnoreCase(method)) {
                  request.put(
                      DATA_UPLOAD_STATUS,
                      request.containsKey(MEDIA_URL) && !request.getString(MEDIA_URL).isBlank());
                  request.put(PUBLISH_STATUS, PENDING);
                  promise.complete(request);
                } else {
                  promise.complete(request);
                }
              } else {
                LOGGER.debug("Fail: DB Error: " + err.getLocalizedMessage());
                promise.fail(VALIDATION_FAILURE_MSG);
              }
            })
        .onSuccess(
            res -> {
              String returnType = getReturnTypeForValidation(res.toJson());
              if (REQUEST_POST.equalsIgnoreCase(method)
                  && returnType.contains(ITEM_TYPE_AI_MODEL)) {
                promise.fail("Fail: AI Model item already exists");
                return;
              }

              boolean mediaUrlPresent =
                  request.containsKey(MEDIA_URL) && !request.getString(MEDIA_URL).isBlank();

              if (REQUEST_POST.equalsIgnoreCase(method)) {
                request.put(DATA_UPLOAD_STATUS, mediaUrlPresent);
                request.put(PUBLISH_STATUS, PENDING);
              } else {
                boolean wasPreviouslyUploaded = extractDataUploadStatusFromES(res.toJson());
                boolean previousMediaUrlPresent = extractMediaUrlFromES(res.toJson());

                request.put(
                    DATA_UPLOAD_STATUS,
                    mediaUrlPresent || (wasPreviouslyUploaded && !previousMediaUrlPresent));
                request.put(PUBLISH_STATUS, extractPublishStatusFromES(res.toJson()));
              }

              promise.complete(request);
            });
  }

  public void validateDataBank(JsonObject request, String method, Promise<JsonObject> promise) {
    validateAndAddId(request, promise);
    if (!request.containsKey(ID)) {
      request.put(ID, UUID.randomUUID().toString());
    }

    setCommonFields(request);

    itemService
        .itemWithTheNameExists(ITEM_TYPE_DATA_BANK, request.getString(NAME))
        .onFailure(
            err -> {
              if (err.getMessage().equals(DETAIL_ITEM_NOT_FOUND)) {
                // For POST, if not found, good to proceed
                if (REQUEST_POST.equalsIgnoreCase(method)) {
                  request.put(
                      DATA_UPLOAD_STATUS,
                      request.containsKey(MEDIA_URL) && !request.getString(MEDIA_URL).isBlank());
                  request.put(PUBLISH_STATUS, PENDING);
                  promise.complete(request);
                } else {
                  promise.complete(request);
                }
              } else {
                LOGGER.debug("Fail: DB Error: " + err.getLocalizedMessage());
                promise.fail(VALIDATION_FAILURE_MSG);
              }
            })
        .onSuccess(
            res -> {
              String returnType = getReturnTypeForValidation(res.toJson());
              if (REQUEST_POST.equalsIgnoreCase(method)
                  && returnType.contains(ITEM_TYPE_DATA_BANK)) {
                promise.fail("Fail: DataBank item already exists");
                return;
              }

              boolean mediaUrlPresent =
                  request.containsKey(MEDIA_URL) && !request.getString(MEDIA_URL).isBlank();

              if (REQUEST_POST.equalsIgnoreCase(method)) {
                request.put(DATA_UPLOAD_STATUS, mediaUrlPresent);
                request.put(PUBLISH_STATUS, PENDING);
              } else {
                boolean wasPreviouslyUploaded = extractDataUploadStatusFromES(res.toJson());
                boolean previousMediaUrlPresent = extractMediaUrlFromES(res.toJson());

                request.put(
                    DATA_UPLOAD_STATUS,
                    mediaUrlPresent || (wasPreviouslyUploaded && !previousMediaUrlPresent));
                request.put(PUBLISH_STATUS, extractPublishStatusFromES(res.toJson()));
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
