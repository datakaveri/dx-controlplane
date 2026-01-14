package org.cdpg.dx.aaa.vote.dao.impl;

import io.vertx.core.Future;

import java.util.List;
import java.util.UUID;
import org.cdpg.dx.aaa.vote.dao.ItemVoteDao;
import org.cdpg.dx.aaa.vote.model.ItemVote;
import org.cdpg.dx.database.postgres.base.dao.AbstractBaseDAO;
import org.cdpg.dx.database.postgres.models.Condition;
import org.cdpg.dx.database.postgres.models.DeleteQuery;
import org.cdpg.dx.database.postgres.models.UpsertResult;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class ItemVoteDaoImpl extends AbstractBaseDAO<ItemVote> implements ItemVoteDao {

  public ItemVoteDaoImpl(PostgresService postgresService) {
    super(postgresService, "item_votes", "id", ItemVote::fromJson);
  }

  @Override
  public Future<UpsertResult<ItemVote>> upsertVote(ItemVote itemVote) {
    return super.upsertNew(
        itemVote, List.of("user_id", "entity_id"), List.of("vote_type", "updated_at"));
  }

  @Override
  public Future<Void> deleteVote(UUID userId, UUID entityId) {

    Condition condition =
        new Condition(
            List.of(
                new Condition("user_id", Condition.Operator.EQUALS, List.of(userId.toString())),
                new Condition(
                    "entity_id", Condition.Operator.EQUALS, List.of(entityId.toString()))),
            Condition.LogicalOperator.AND);

    DeleteQuery query = new DeleteQuery("item_votes", condition, null, null);

    return postgresService.delete(query).mapEmpty();
  }
}
