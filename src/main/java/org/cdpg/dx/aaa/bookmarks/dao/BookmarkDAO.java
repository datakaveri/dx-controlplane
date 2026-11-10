package org.cdpg.dx.aaa.bookmarks.dao;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.bookmarks.model.Bookmark;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.database.postgres.models.PaginatedResult;
import org.cdpg.dx.database.postgres.models.UpsertResult;

import java.util.UUID;

public interface BookmarkDAO {

  Future<UpsertResult<Bookmark>> upsert(Bookmark bookmark);

  Future<PaginatedResult<Bookmark>> getByUser(PaginatedRequest paginatedRequest);

  Future<Void> delete(UUID userId, UUID entityId);
}
