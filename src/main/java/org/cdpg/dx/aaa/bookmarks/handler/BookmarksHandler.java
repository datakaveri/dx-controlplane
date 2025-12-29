package org.cdpg.dx.aaa.bookmarks.handler;

import static org.cdpg.dx.aaa.bookmarks.util.Constants.ALLOWED_FILTER_MAP_FOR_BOOKMARK_REQUEST;
import static org.cdpg.dx.database.postgres.util.Constants.DEFAULT_SORTING_ORDER;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.bookmarks.service.BookmarkService;
import org.cdpg.dx.common.FailureHandler;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.request.PaginationRequestBuilder;
import org.cdpg.dx.common.response.ResponseBuilder;

public class BookmarksHandler {
  private static final Logger LOGGER = LogManager.getLogger(BookmarksHandler.class);
  private final BookmarkService bookmarkService;
  private final URNGenerator urnGenerator;

  public BookmarksHandler(BookmarkService bookmarkService, URNGenerator urnGenerator) {
    this.bookmarkService = bookmarkService;
    this.urnGenerator = urnGenerator;
  }

  public void createBookmark(RoutingContext ctx) {
    LOGGER.trace("createBookmark() handler started");

    UUID userId = UUID.fromString(ctx.user().subject());
    JsonObject body = ctx.body().asJsonObject();
    UUID entityId = UUID.fromString(body.getString("entityId"));
    String entityType = body.getString("entityType");
    bookmarkService
        .createBookmark(userId, entityId, entityType)
        .onSuccess(
            result -> {
              LOGGER.info(
                  "Bookmark created with id: {} for userId: {} and entityId: {}",
                  result.entity().id(),
                  userId,
                  entityId);
              ResponseBuilder.sendSuccess(ctx, result.entity(), urnGenerator);
            })
        .onFailure(ctx::fail);
  }

  public void getBookmarks(RoutingContext ctx) {
    LOGGER.trace("getBookmarks() handler started");
    UUID userId = UUID.fromString(ctx.user().subject());

    PaginatedRequest request =
        PaginationRequestBuilder.from(ctx)
            .additionalFilters(Map.of("user_id", userId.toString()))
            .allowedFiltersDbMap(ALLOWED_FILTER_MAP_FOR_BOOKMARK_REQUEST)
            .apiToDbMap(ALLOWED_FILTER_MAP_FOR_BOOKMARK_REQUEST)
            .allowedTimeFields(Set.of("created_at"))
            .defaultTimeField("created_at")
            .defaultSort("created_at", DEFAULT_SORTING_ORDER)
            .allowedSortFields(ALLOWED_FILTER_MAP_FOR_BOOKMARK_REQUEST.keySet())
            .build();

    bookmarkService
        .getBookmarks(request)
        .onSuccess(
            paginatedResult -> {
              LOGGER.info(
                  "Fetched {} bookmarks for userId: {}", paginatedResult.data().size(), userId);
              ResponseBuilder.sendSuccess(
                  ctx, paginatedResult.data(), paginatedResult.paginationInfo(), urnGenerator);
            })
        .onFailure(ctx::fail);
  }

  public void deleteBookmark(RoutingContext ctx) {
    LOGGER.trace("deleteBookmark() handler started");
    UUID userId = UUID.fromString(ctx.user().subject());
    UUID entityId = UUID.fromString(ctx.pathParam("entityId"));

    bookmarkService
        .deleteBookmark(userId, entityId)
        .onSuccess(
            handler -> {
              LOGGER.info("Bookmark deleted for userId: {} and entityId: {}", userId, entityId);
              ResponseBuilder.sendNoContent(ctx, urnGenerator);
            })
        .onFailure(ctx::fail);
  }
}
