package org.cdpg.dx.aaa.bookmarks.service;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.bookmarks.model.Bookmark;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.database.postgres.models.PaginatedResult;
import org.cdpg.dx.database.postgres.models.UpsertResult;

import java.util.UUID;

public interface BookmarkService {

  Future<UpsertResult<Bookmark>> createBookmark(UUID userId, UUID entityId, String entityType);

  Future<PaginatedResult<Bookmark>> getBookmarks(PaginatedRequest paginatedRequest);

  Future<Void> deleteBookmark(UUID userId, UUID entityId);
}
