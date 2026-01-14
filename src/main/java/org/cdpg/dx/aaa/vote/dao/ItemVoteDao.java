package org.cdpg.dx.aaa.vote.dao;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.vote.model.ItemVote;
import org.cdpg.dx.database.postgres.models.UpsertResult;

import java.util.UUID;

public interface ItemVoteDao {

  Future<UpsertResult<ItemVote>> upsertVote(ItemVote itemVote);

  Future<Void> deleteVote(UUID userId, UUID entityId);
}
