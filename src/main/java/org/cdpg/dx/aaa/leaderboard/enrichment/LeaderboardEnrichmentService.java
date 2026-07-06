package org.cdpg.dx.aaa.leaderboard.enrichment;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.item.util.GetItemRequest;
import org.cdpg.dx.aaa.leaderboard.model.LeaderboardEvent;
import org.cdpg.dx.auditing.v2.util.AssetTypeUtil;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

import java.util.UUID;

public class LeaderboardEnrichmentService {

  private static final Logger LOGGER = LogManager.getLogger(LeaderboardEnrichmentService.class);

  private final ItemService itemService;
  private final KeycloakUserService keycloakUserService;

  public LeaderboardEnrichmentService(
      ItemService itemService, KeycloakUserService keycloakUserService) {
    this.itemService = itemService;
    this.keycloakUserService = keycloakUserService;
  }

  public Future<LeaderboardEvent> enrich(LeaderboardEvent event) {

    // No asset → nothing to enrich
    if (event == null || event.assetId() == null || event.action().equalsIgnoreCase("DELETE")) {
      return Future.succeededFuture(event);
    }

    GetItemRequest request = new GetItemRequest(event.assetId().toString(), null);

    return itemService
        .getItem(request)
        .map(resp -> applyItem(event, resp.getResponse()))
        .recover(
            err -> {
              LOGGER.warn(
                  "Leaderboard enrichment failed [assetId={}], skipping eligibility",
                  event.assetId(),
                  err);
              return Future.succeededFuture(event);
            })
        .compose(this::resolveProviderName)
        .onSuccess(
            e ->
                LOGGER.info(
                    "Leaderboard enrichment success [assetId={}, publishStatus={}]",
                    e.assetId(),
                    e.publishStatus()));
  }

  private LeaderboardEvent applyItem(LeaderboardEvent event, JsonObject response) {

    JsonObject item = response.getJsonArray("results").getJsonObject(0);

    // Always take the real state from the catalogue item — eligibility (dataUploadStatus &&
    // publishStatus ACTIVE) may only be reached later via PATCH, so CREATE alone must not
    // assume the asset is publishable. Missing fields count as pass (e.g. usecases).
    boolean dataUploadStatus = item.getBoolean("dataUploadStatus", true);
    String publishStatus = item.getString("publishStatus", "ACTIVE");

    return new LeaderboardEvent(
        event.assetId(),
        event.action(),
        AssetTypeUtil.extractAssetType(item),
        safeUuid(item.getString("ownerUserId")),
        event.providerName(),
        safeUuid(item.getString("organizationId")),
        item.getString("name"),
        item.getString("accessPolicy"),
        item.getString("organization"),
        item.getString("organizationType"),
        dataUploadStatus,
        publishStatus,
        event.createdAt());
  }

  /**
   * Best-effort provider name lookup from Keycloak. The catalogue item carries only ownerUserId, so
   * this is the only reliable name source for platform providers who are not in
   * organization_users. Only CREATE/UPDATE persist the name, so other actions skip the lookup.
   */
  private Future<LeaderboardEvent> resolveProviderName(LeaderboardEvent event) {

    boolean isUpsertAction =
        "CREATE".equalsIgnoreCase(event.action()) || "UPDATE".equalsIgnoreCase(event.action());

    if (event.providerId() == null || !isUpsertAction || event.providerName() != null) {
      return Future.succeededFuture(event);
    }

    return keycloakUserService
        .getUserById(event.providerId())
        .map(
            user -> {
              if (user == null || user.name() == null || user.name().isBlank()) {
                return event;
              }
              return withProviderName(event, user.name());
            })
        .recover(
            err -> {
              LOGGER.warn(
                  "Provider name lookup failed [providerId={}], proceeding without name",
                  event.providerId(),
                  err);
              return Future.succeededFuture(event);
            });
  }

  private LeaderboardEvent withProviderName(LeaderboardEvent event, String providerName) {
    return new LeaderboardEvent(
        event.assetId(),
        event.action(),
        event.assetType(),
        event.providerId(),
        providerName,
        event.organizationId(),
        event.assetName(),
        event.accessPolicy(),
        event.organizationName(),
        event.organizationType(),
        event.dataUploadStatus(),
        event.publishStatus(),
        event.createdAt());
  }

  private UUID safeUuid(String v) {
    try {
      return v == null ? null : UUID.fromString(v);
    } catch (Exception e) {
      return null;
    }
  }
}
