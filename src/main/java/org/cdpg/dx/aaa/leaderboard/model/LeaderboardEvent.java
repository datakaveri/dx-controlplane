package org.cdpg.dx.aaa.leaderboard.model;

import io.vertx.core.json.JsonObject;
import java.util.UUID;

import static org.cdpg.dx.auditing.v2.Constant.UserActivityAuditSchema.*;

public record LeaderboardEvent(
    UUID assetId,
    String action, // CREATE, UPDATE, VIEW, DOWNLOAD, LIKE, DELETE
    String assetType,
    UUID providerId,
    UUID organizationId,
    String assetName,
    String accessPolicy,
    String organizationName,
    String organizationType,
    boolean dataUploadStatus,
    String publishStatus,
    String createdAt) {

  public static LeaderboardEvent fromJson(JsonObject json) {

    UUID assetId =
        json.getString(ASSET_ID) != null ? UUID.fromString(json.getString(ASSET_ID)) : null;

    UUID providerId =
        json.getString(ASSET_PROVIDER_ID) != null
            ? UUID.fromString(json.getString(ASSET_PROVIDER_ID))
            : null;

    UUID organizationId =
        json.getString(ASSET_ORG_ID) != null ? UUID.fromString(json.getString(ASSET_ORG_ID)) : null;
    JsonObject context = json.getJsonObject(CONTEXT);
    return new LeaderboardEvent(
        assetId,
        json.getString(ACTION),
        json.getString(ASSET_TYPE),
        providerId,
        organizationId,
        json.getString(ASSET_NAME),
        json.getString(ASSET_ACCESS_POLICY),
        json.getString(ASSET_ORG_NAME),
        json.getString(ASSET_ORG_TYPE),
        context.getBoolean("dataUploadStatus", true),
        context.getString("publishStatus", "ACTIVE"),
        json.getString(CREATED_AT));
  }
}
