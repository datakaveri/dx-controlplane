package org.cdpg.dx.aaa.bookmarks.dao.impl;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.bookmarks.dao.BookmarkDAO;
import org.cdpg.dx.aaa.bookmarks.model.Bookmark;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.database.postgres.base.dao.AbstractBaseDAO;
import org.cdpg.dx.database.postgres.models.Condition;
import org.cdpg.dx.database.postgres.models.DeleteQuery;
import org.cdpg.dx.database.postgres.models.PaginatedResult;
import org.cdpg.dx.database.postgres.models.UpsertResult;
import org.cdpg.dx.database.postgres.service.PostgresService;

import java.util.List;
import java.util.UUID;

public class BookmarkDAOImpl extends AbstractBaseDAO<Bookmark> implements BookmarkDAO {

  public BookmarkDAOImpl(PostgresService postgresService) {
    super(postgresService, "bookmarks", "id", Bookmark::fromJson);
  }

  @Override
  public Future<UpsertResult<Bookmark>> upsert(Bookmark bookmark) {
    return super.upsertNew(bookmark, List.of("user_id", "entity_id", "entity_type"), List.of());
  }

  @Override
  public Future<PaginatedResult<Bookmark>> getByUser(PaginatedRequest paginatedRequest) {
    return super.getAllWithFilters(paginatedRequest);
  }

  @Override
  public Future<Void> delete(UUID userId, UUID entityId) {

    Condition condition =
        new Condition(
            List.of(
                new Condition("user_id", Condition.Operator.EQUALS, List.of(userId.toString())),
                new Condition(
                    "entity_id", Condition.Operator.EQUALS, List.of(entityId.toString()))),
            Condition.LogicalOperator.AND);

    DeleteQuery query = new DeleteQuery("bookmarks", condition, null, null);

    return postgresService.delete(query).mapEmpty();
  }
}
