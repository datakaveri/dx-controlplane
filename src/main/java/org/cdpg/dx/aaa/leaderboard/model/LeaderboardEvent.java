package org.cdpg.dx.aaa.leaderboard.model;

import io.vertx.core.json.JsonObject;
import java.util.UUID;

import static org.cdpg.dx.auditing.v2.Constant.UserActivityAuditSchema.*;

public record LeaderboardEvent(
    UUID assetId,
    String action, // CREATE, UPDATE, VIEW, DOWNLOAD, LIKE, DISLIKE, NEUTRAL, DELETE
    String assetType,
    UUID providerId,
    String providerName, // resolved from Keycloak during enrichment; null in raw events
    UUID organizationId, // nullable — platform providers have no organisation
    String assetName,
    String accessPolicy,
    String organizationName,
    String organizationType,
    boolean dataUploadStatus,
    String publishStatus,
    String createdAt,
    boolean wasLiked) { // vote events only: the user's previous vote was LIKE

  public static LeaderboardEvent fromJson(JsonObject json) {

    UUID assetId =
        json.getString(ASSET_ID) != null ? UUID.fromString(json.getString(ASSET_ID)) : null;

    UUID providerId =
        json.getString(ASSET_PROVIDER_ID) != null
            ? UUID.fromString(json.getString(ASSET_PROVIDER_ID))
            : null;

    UUID organizationId =
        json.getString(ASSET_ORG_ID) != null ? UUID.fromString(json.getString(ASSET_ORG_ID)) : null;
    JsonObject context = json.getJsonObject(CONTEXT, new JsonObject());
    return new LeaderboardEvent(
        assetId,
        json.getString(ACTION),
        json.getString(ASSET_TYPE),
        providerId,
        null,
        organizationId,
        json.getString(ASSET_NAME),
        json.getString(ASSET_ACCESS_POLICY),
        json.getString(ASSET_ORG_NAME),
        json.getString(ASSET_ORG_TYPE),
        // Fail closed: a CREATE/UPDATE may only enter the leaderboard once enrichment proves
        // dataUploadStatus=true && publishStatus=ACTIVE from the catalogue item. If the item
        // lookup fails, these raw defaults keep the event ineligible instead of blindly passing.
        context.getBoolean("dataUploadStatus", false),
        context.getString("publishStatus", "PENDING"),
        json.getString(CREATED_AT),
        // Vote events carry the InteractionDelta as context. Fail closed here too: without
        // proof the previous vote was LIKE, a Dislike/Neutral must not decrement likes.
        context.getBoolean("oldLiked", false));
  }
}
