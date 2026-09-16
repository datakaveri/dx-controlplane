package org.cdpg.dx.aaa.interaction.v2.service.impl;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.cdpg.dx.aaa.interaction.v2.dao.ProviderFeedbackDao;
import org.cdpg.dx.aaa.interaction.v2.dao.UserFeedbackDao;
import org.cdpg.dx.aaa.interaction.v2.dao.UserInteractionV2Dao;
import org.cdpg.dx.aaa.interaction.v2.enums.ProviderFeedbackType;
import org.cdpg.dx.aaa.interaction.v2.model.*;
import org.cdpg.dx.aaa.interaction.v2.service.UserInteractionV2Service;

import org.cdpg.dx.aaa.item.service.ItemService;

import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.response.PaginatedApiResponse;

import org.cdpg.dx.database.elastic.model.BulkSyncResult;
import org.cdpg.dx.database.postgres.models.PaginatedResult;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

public class UserInteractionV2ServiceImpl implements UserInteractionV2Service {

  private static final Logger LOGGER = LogManager.getLogger(UserInteractionV2ServiceImpl.class);

  private final UserInteractionV2Dao dao;
  private final UserFeedbackDao userFeedbackDao;
  private final ProviderFeedbackDao providerFeedbackDao;
  private final ItemService itemService;
  private final KeycloakUserService keycloakUserService;

  public UserInteractionV2ServiceImpl(
      UserInteractionV2Dao dao,
      UserFeedbackDao userFeedbackDao,
      ProviderFeedbackDao providerFeedbackDao,
      ItemService itemService,
      KeycloakUserService keycloakUserService) {

    this.dao = dao;
    this.userFeedbackDao = userFeedbackDao;
    this.providerFeedbackDao = providerFeedbackDao;
    this.itemService = itemService;
    this.keycloakUserService = keycloakUserService;
  }

  // =====================================================
  // GET interactions (Paginated)
  // =====================================================
  @Override
  public Future<PaginatedApiResponse<UserInteractionAssetResponse>> getUserInteractions(
      PaginatedRequest request) {

    return dao.getUserInteractions(request).compose(this::enrichAndConvert);
  }

  // =====================================================
  // SAVE interaction (LIKE / DISLIKE / BOOKMARK / NEUTRAL)
  // =====================================================
  @Override
  public Future<InteractionDelta> saveInteraction(UUID userId, UserInteractionV2Request req) {

    return dao.upsertInteractionWithDelta(
            userId, req.assetId(), req.assetType().name(), req.action().name())
        .onSuccess(delta -> applyDeltaAsync(req, delta));
  }

  // =====================================================
  // Async ES metrics update (fire-and-forget)
  // =====================================================
  private void applyDeltaAsync(UserInteractionV2Request req, InteractionDelta delta) {

    EngagementDelta engagementDelta = computeEngagementDelta(delta);

    if (engagementDelta.isNoOp()) {
      return;
    }

    itemService
        .updateEngagementCounters(
            UUID.fromString(delta.entityId()),
            engagementDelta.likeDelta(),
            engagementDelta.dislikeDelta())
        .onFailure(
            err ->
                LOGGER.warn(
                    "Failed to update engagement metrics for asset={}", delta.entityId(), err));
  }

  private EngagementDelta computeEngagementDelta(InteractionDelta d) {

    int likeDelta = 0;
    int dislikeDelta = 0;

    if (d.oldLiked() != d.newLiked()) {
      likeDelta += d.newLiked() ? 1 : -1;
    }

    if (d.oldDisliked() != d.newDisliked()) {
      dislikeDelta += d.newDisliked() ? 1 : -1;
    }

    return new EngagementDelta(likeDelta, dislikeDelta);
  }

  // =====================================================
  // Bulk sync (admin / maintenance)
  // =====================================================
  @Override
  public Future<BulkSyncResult> syncInteractionMetrics() {
    return dao.aggregateInteractions().compose(itemService::bulkSyncMetrics);
  }

  // =====================================================
  // Enrichment + API mapping (single correct path)
  // =====================================================
  private Future<PaginatedApiResponse<UserInteractionAssetResponse>> enrichAndConvert(
      PaginatedResult<InteractionRow> page) {

    List<InteractionRow> rows = page.data();

    if (rows.isEmpty()) {
      return Future.succeededFuture(new PaginatedApiResponse<>(List.of(), page.paginationInfo()));
    }

    List<Future<UserInteractionAssetResponse>> futures =
        rows.stream()
            .map(
                row ->
                    fetchItemSummary(UUID.fromString(row.assetId()))
                        .map(
                            item ->
                                new UserInteractionAssetResponse(
                                    item,
                                    new UserInteractionState(
                                        row.isLiked(), row.isDisliked(), row.isBookmarked()))))
            .toList();

    return CompositeFuture.all(new ArrayList<>(futures))
        .map(
            cf ->
                new PaginatedApiResponse<>(
                    futures.stream().map(Future::result).toList(), page.paginationInfo()));
  }

  // =====================================================
  // Fetch asset metadata from ES
  // =====================================================
  private Future<ItemSummary> fetchItemSummary(UUID assetId) {

    return itemService
        .getItemSource(assetId.toString())
        .map(
            source -> {
              if (source == null) {
                return null;
              }

              String type =
                  source.getJsonArray("type") != null && !source.getJsonArray("type").isEmpty()
                      ? source.getJsonArray("type").getString(0)
                      : null;

              return new ItemSummary(
                  assetId.toString(),
                  source.getString("name"),
                  source.getString("shortDescription"),
                  type,
                  source.getString("accessPolicy"),
                  source.getString("ownerUserId"),
                  source.getString("organizationId"),
                  source.getString("organization"),
                  source.getString("uploadedBy"),
                  source.getJsonArray("resourceServer"),
                  source.getString("fileFormat"),
                  source.getString("industry"),
                  source.getJsonArray("tags"),
                  source.getFloat("dataReadiness"),
                  source.getString("itemCreatedAt"),
                  source.getJsonObject("metrics", new JsonObject()));
            })
        .recover(
            err -> {
              LOGGER.warn("Failed to fetch item metadata for assetId={}", assetId, err);
              return Future.succeededFuture(null);
            });
  }

  // =====================================================
  // Internal helper
  // =====================================================
  private record EngagementDelta(int likeDelta, int dislikeDelta) {
    boolean isNoOp() {
      return likeDelta == 0 && dislikeDelta == 0;
    }
  }

  @Override
  public Future<UserFeedback> postUserFeedback(UserFeedback request) {
    LOGGER.info("Inside service imple method - post user feedbaack");
    return userFeedbackDao.postFeedback(request);
  }

  @Override
  public Future<UserFeedback> putUserFeedback(UserFeedback request) {
    LOGGER.info("Inside service imple method - put user feedbaack");
    return userFeedbackDao.putFeedback(request);
  }

  @Override
  public Future<UserFeedback> updateFeedbackStatus(
      UUID feedbackId, FeedbackStatus status, String comment) {

    LOGGER.info("Updating feedback status: feedbackId={}, status={}", feedbackId, status);

    return userFeedbackDao.updateFeedbackStatus(feedbackId, status, comment);
  }

  @Override
  public Future<ProviderFeedback> postProviderFeedback(ProviderFeedback request) {
    LOGGER.info("Inside service imple method - post provider feedbaack");
    return providerFeedbackDao.postProviderFeedback(request);
  }

  @Override
  public Future<Boolean> deleteUserFeedback(UUID userId, UUID assetId) {
    LOGGER.info("Inside service imple method - delete user feedbaack");
    return userFeedbackDao
        .deleteFeedback(userId, assetId)
        .compose(
            deleted ->
                deleted
                    ? Future.succeededFuture(true)
                    : Future.failedFuture(new DxNotFoundException("Feedback not found")));
  }

  @Override
  public Future<UserFeedbackPage> getUserFeedback(PaginatedRequest request) {
    LOGGER.debug("UserInteractionsPaginatedResponse() method started");
    return userFeedbackDao
        .fetchUserFeedbacks(request)
        .compose(
            page ->
                enrichFeedbackList(page.data())
                    .map(
                        enriched ->
                            new UserFeedbackPage(page.summary(), enriched, page.paginationInfo())));
  }

  @Override
  public Future<UserFeedbackPage> getPlatformUsersFeedbacks(PaginatedRequest request) {
    LOGGER.debug("UserInteractionsPaginatedResponse() method started");
    return userFeedbackDao
        .fetchPlatformUsersFeedbacks(request)
        .compose(
            page ->
                enrichFeedbackList(page.data())
                    .map(
                        enriched ->
                            new UserFeedbackPage(page.summary(), enriched, page.paginationInfo())));
  }

  // =====================================================
  // Best-effort userId -> name/organisation enrichment for feedback rows
  // =====================================================
  private Future<List<UserFeedbackResponse>> enrichFeedbackList(List<UserFeedback> rows) {

    if (rows.isEmpty()) {
      return Future.succeededFuture(List.of());
    }

    Map<UUID, Future<DxUser>> userLookups = new LinkedHashMap<>();
    for (UserFeedback row : rows) {
      userLookups.computeIfAbsent(row.userId(), this::fetchUserSafe);
    }

    return CompositeFuture.all(new ArrayList<>(userLookups.values()))
        .map(
            cf ->
                rows.stream()
                    .map(
                        row -> {
                          DxUser user = userLookups.get(row.userId()).result();
                          return UserFeedbackResponse.from(
                              row,
                              user != null ? user.name() : null,
                              user != null ? user.organisationName() : null);
                        })
                    .toList());
  }

  private Future<DxUser> fetchUserSafe(UUID userId) {
    return keycloakUserService
        .getUserById(userId)
        .recover(
            err -> {
              LOGGER.warn("Failed to resolve user info for userId={}", userId, err);
              return Future.succeededFuture(null);
            });
  }

  @Override
  public Future<ProviderFeedbackPaginatedResponse> getProviderFeedback(PaginatedRequest request) {
    LOGGER.debug("UserInteractionsPaginatedResponse() method started");
    return providerFeedbackDao.fetchProviderFeedbacks(request);
  }

  @Override
  public Future<Boolean> deleteProviderFeedback(
      UUID userId, UUID assetId, ProviderFeedbackType type) {
    LOGGER.info("Inside service imple method - delete user feedbaack");
    return providerFeedbackDao.deleteProviderFeedback(userId, assetId, type);
  }
}
