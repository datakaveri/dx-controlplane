package org.cdpg.dx.aaa.bookmarks.service.impl;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.bookmarks.dao.BookmarkDAO;
import org.cdpg.dx.aaa.bookmarks.model.Bookmark;
import org.cdpg.dx.aaa.bookmarks.model.BookmarkEntityType;
import org.cdpg.dx.aaa.bookmarks.service.BookmarkService;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.database.postgres.models.PaginatedResult;
import org.cdpg.dx.database.postgres.models.UpsertResult;

import java.util.UUID;

public class BookmarkServiceImpl implements BookmarkService {

  private final BookmarkDAO bookmarkDAO;

  public BookmarkServiceImpl(BookmarkDAO bookmarkDAO) {
    this.bookmarkDAO = bookmarkDAO;
  }

  @Override
  public Future<UpsertResult<Bookmark>> createBookmark(
      UUID userId, UUID entityId, String entityType) {

    BookmarkEntityType type = BookmarkEntityType.fromValue(entityType);

    Bookmark bookmark = new Bookmark(null, userId, entityId, type, null);

    return bookmarkDAO.upsert(bookmark);
  }

  @Override
  public Future<PaginatedResult<Bookmark>> getBookmarks(PaginatedRequest paginatedRequest) {
    return bookmarkDAO.getByUser(paginatedRequest);
  }

  @Override
  public Future<Void> deleteBookmark(UUID userId, UUID entityId) {
    return bookmarkDAO.delete(userId, entityId);
  }
}
