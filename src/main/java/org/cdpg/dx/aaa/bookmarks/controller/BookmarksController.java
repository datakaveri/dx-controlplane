package org.cdpg.dx.aaa.bookmarks.controller;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.apiserver.ApiController;
import org.cdpg.dx.aaa.apiserver.OperationIds;
import org.cdpg.dx.aaa.asset.controller.AssetController;
import org.cdpg.dx.aaa.bookmarks.handler.BookmarksHandler;
import org.cdpg.dx.auth.authorization.handler.AuthorizationHandler;
import org.cdpg.dx.auth.model.DxRole;

public class BookmarksController implements ApiController {
  private static final Logger LOGGER = LogManager.getLogger(BookmarksController.class);
  final BookmarksHandler bookmarksHandler;

  public BookmarksController(BookmarksHandler bookmarksHandler) {
    this.bookmarksHandler = bookmarksHandler;
  }

  @Override
  public void register(RouterBuilder builder) {
    LOGGER.info("Registering Bookmarks APIs");
    Handler<RoutingContext> authorizationHandler =
        AuthorizationHandler.forRoles(DxRole.ORG_ADMIN, DxRole.COS_ADMIN, DxRole.CONSUMER);

    builder
        .operation(OperationIds.OP_POST_BOOKMARK)
        .handler(authorizationHandler)
        .handler(bookmarksHandler::createBookmark);

    builder
        .operation(OperationIds.OP_GET_BOOKMARKS)
        .handler(authorizationHandler)
        .handler(bookmarksHandler::getBookmarks);
    builder
        .operation(OperationIds.OP_DELETE_BOOKMARK)
        .handler(authorizationHandler)
        .handler(bookmarksHandler::deleteBookmark);
  }
}
