package org.cdpg.dx.aaa.bookmarks.factory;

import org.cdpg.dx.aaa.bookmarks.controller.BookmarksController;
import org.cdpg.dx.aaa.bookmarks.dao.BookmarkDAO;
import org.cdpg.dx.aaa.bookmarks.dao.impl.BookmarkDAOImpl;
import org.cdpg.dx.aaa.bookmarks.handler.BookmarksHandler;
import org.cdpg.dx.aaa.bookmarks.service.BookmarkService;
import org.cdpg.dx.aaa.bookmarks.service.impl.BookmarkServiceImpl;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class BookmarksControllerFactory {

  private BookmarksControllerFactory() {}

  public static BookmarksController create(PostgresService postgresService, URNGenerator urnGenerator) {
    BookmarkDAO bookmarkDAO = new BookmarkDAOImpl(postgresService);
    BookmarkService bookmarkService = new BookmarkServiceImpl(bookmarkDAO);
    BookmarksHandler bookmarksHandler = new BookmarksHandler(bookmarkService, urnGenerator);
    return new BookmarksController(bookmarksHandler);
  }
}
