package org.cdpg.dx.aaa.interaction.v2.service.impl;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.cdpg.dx.aaa.interaction.v2.dao.ProviderFeedbackDao;
import org.cdpg.dx.aaa.interaction.v2.dao.UserFeedbackDao;
import org.cdpg.dx.aaa.interaction.v2.dao.UserInteractionV2Dao;
import org.cdpg.dx.aaa.interaction.v2.model.*;
import org.cdpg.dx.aaa.interaction.v2.service.UserInteractionV2Service;

import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.item.util.GetItemRequest;

import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.response.PaginatedApiResponse;

import org.cdpg.dx.database.elastic.model.BulkSyncResult;
import org.cdpg.dx.database.postgres.models.PaginatedResult;

public class UserInteractionV2ServiceImpl implements UserInteractionV2Service {

  private static final Logger LOGGER = LogManager.getLogger(UserInteractionV2ServiceImpl.class);

  private final UserInteractionV2Dao dao;
  private final UserFeedbackDao userFeedbackDao;
  private final ProviderFeedbackDao providerFeedbackDao;
  private final ItemService itemService;

  public UserInteractionV2ServiceImpl(UserInteractionV2Dao dao, UserFeedbackDao userFeedbackDao, ProviderFeedbackDao providerFeedbackDao,ItemService itemService) {

    this.dao = dao;
    this.userFeedbackDao = userFeedbackDao;
    this.providerFeedbackDao = providerFeedbackDao;
    this.itemService = itemService;
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

    GetItemRequest request = new GetItemRequest(assetId.toString(), null);

    return itemService
        .getItem(request)
        .map(
            response -> {
              if (response == null || response.getTotalHits() == 0) {
                return null;
              }

              JsonObject item = response.getResponse().getJsonArray("results").getJsonObject(0);

              String type =
                  item.getJsonArray("type") != null && !item.getJsonArray("type").isEmpty()
                      ? item.getJsonArray("type").getString(0)
                      : null;

              return new ItemSummary(
                  item.getString("id"),
                  item.getString("name"),
                  item.getString("shortDescription"),
                  type,
                  item.getString("accessPolicy"),
                  item.getString("ownerUserId"),
                  item.getString("organizationId"),
                  item.getString("organization"),
                  item.getString("uploadedBy"),
                  item.getJsonArray("resourceServer"),
                  item.getString("fileFormat"),
                  item.getString("itemCreatedAt"),
                  item.getJsonObject("metrics", new JsonObject()));
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
  public Future<UserFeedback> postUserFeedback(UserFeedback request)
  {
    LOGGER.info("Inside service imple method - post user feedbaack");
    return userFeedbackDao.updateFeedback(request);
  }


  @Override
  public Future<ProviderFeedback> postProviderFeedback(ProviderFeedback request)
  {
    LOGGER.info("Inside service imple method - post provider feedbaack");
    return providerFeedbackDao.postProviderFeedback(request);
  }

  @Override
  public Future<Boolean> deleteUserFeedback(UUID reqId, UUID userId)
  {
    LOGGER.info("Inside service imple method - delete user feedbaack");
    return userFeedbackDao.deleteFeedback(reqId,userId);
  }


  @Override
  public  Future<UserFeedbackPaginatedResponse> getUserFeedback(PaginatedRequest request)
  {
    LOGGER.debug("UserInteractionsPaginatedResponse() method started");
    return userFeedbackDao.fetchUserFeedbacks(request);
  }

  @Override
  public  Future<ProviderFeedbackPaginatedResponse> getProviderFeedback(PaginatedRequest request)
  {
    LOGGER.debug("UserInteractionsPaginatedResponse() method started");
    return providerFeedbackDao.fetchProviderFeedbacks(request);
  }

  @Override
  public Future<Boolean> deleteProviderFeedback(UUID reqId, UUID userId)
  {
    LOGGER.info("Inside service imple method - delete user feedbaack");
    return providerFeedbackDao.deleteProviderFeedback(reqId,userId);
  }


}
