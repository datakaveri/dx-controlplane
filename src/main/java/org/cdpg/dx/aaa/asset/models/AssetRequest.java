package org.cdpg.dx.aaa.asset.models;

import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.asset.util.Constants;
import org.cdpg.dx.aaa.organization.models.Status;
import org.cdpg.dx.common.exception.DxValidationException;
import org.cdpg.dx.database.postgres.base.entity.BaseEntity;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.cdpg.dx.common.util.DateTimeHelper.FORMATTER;
import static org.cdpg.dx.common.util.DateTimeHelper.parseDateTime;
import static org.cdpg.dx.common.util.ValidationUtils.requireNonNull;

public record AssetRequest (
    UUID id,
    UUID assetId,
    UUID userId,
    String status,
    String type,
    JsonObject additionalInfo,
    LocalDateTime requestedAt,
    LocalDateTime updatedAt
) implements BaseEntity<AssetRequest> {

  public static AssetRequest fromJson(JsonObject json) {
    try {
      return new AssetRequest(
        json.containsKey(Constants.ASSET_REQUEST_ID)
          ? UUID.fromString(json.getString(Constants.ASSET_REQUEST_ID))
          : null,
        UUID.fromString(requireNonNull(json.getString(Constants.ASSET_ID), Constants.ASSET_ID)),
        UUID.fromString(requireNonNull(json.getString(Constants.USER_ID), Constants.USER_ID)),
        json.getString(Constants.STATUS) != null
          ? json.getString(Constants.STATUS)
          : Status.PENDING.getStatus(),
        json.getString(Constants.TYPE),
        json.getJsonObject(Constants.ADDITONAL_INFO),
        parseDateTime(json.getString(Constants.REQUESTED_AT)),
        parseDateTime(json.getString(Constants.UPDATED_AT))
      );
    } catch (IllegalArgumentException e) {
      throw new DxValidationException("Missing or invalid required field: " + e.getMessage());
    }
  }

  @Override
  public JsonObject toJson() {
    JsonObject json = new JsonObject();

    if (id != null) json.put(Constants.ASSET_REQUEST_ID, id.toString());
    if(assetId!=null) json.put(Constants.ASSET_ID, assetId.toString());
    json.put(Constants.USER_ID, userId.toString());
    if (additionalInfo != null) json.put(Constants.ADDITONAL_INFO, additionalInfo);
    if (status != null && !status.isEmpty()) json.put(Constants.STATUS, status);
    if (type != null) json.put(Constants.TYPE, type);
    if (requestedAt != null) json.put(Constants.REQUESTED_AT, requestedAt.format(FORMATTER));
    if (updatedAt != null) json.put(Constants.UPDATED_AT, updatedAt.format(FORMATTER));

    return json;
  }

  @Override
  public Map<String, Object> toNonEmptyFieldsMap() {
    Map<String, Object> map = new HashMap<>();

    if (id != null) map.put(Constants.ASSET_REQUEST_ID, id.toString());
    if(assetId!=null) map.put(Constants.ASSET_ID, assetId.toString());
    map.put(Constants.USER_ID, userId.toString());
    if (additionalInfo != null) map.put(Constants.ADDITONAL_INFO, additionalInfo);
    if (status != null && !status.isEmpty()) map.put(Constants.STATUS, status);
    if (type != null) map.put(Constants.TYPE, type);
    if (requestedAt != null) map.put(Constants.REQUESTED_AT, requestedAt.format(FORMATTER));
    if (updatedAt != null) map.put(Constants.UPDATED_AT, updatedAt.format(FORMATTER));

    return map;
  }

  @Override
  public String getTableName() {
    return Constants.ASSET_REQUEST_TABLE;
  }
}
