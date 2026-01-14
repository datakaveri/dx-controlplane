package org.cdpg.dx.aaa.vote.service.impl;

import io.vertx.core.Future;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.vote.dao.ItemVoteDao;
import org.cdpg.dx.aaa.vote.model.ItemVote;
import org.cdpg.dx.aaa.vote.model.VoteRequest;
import org.cdpg.dx.aaa.vote.model.VoteResponse;
import org.cdpg.dx.aaa.vote.model.VoteType;
import org.cdpg.dx.aaa.vote.service.ItemVoteService;

import java.util.UUID;

public class ItemVoteServiceImplImpl implements ItemVoteService {

  private static final Logger LOGGER = LogManager.getLogger(ItemVoteServiceImplImpl.class);

  private final ItemVoteDao itemVoteDao;

  public ItemVoteServiceImplImpl(ItemVoteDao itemVoteDao) {
    this.itemVoteDao = itemVoteDao;
  }

  @Override
  public Future<VoteResponse> castVote(UUID userId, VoteRequest request) {

    LOGGER.debug(
        "Received vote request: userId={}, entityId={}, entityType={}, voteType={}",
        userId,
        request.entityId(),
        request.entityType(),
        request.voteType());

    // -----------------------------
    // NEUTRAL → delete vote
    // -----------------------------
    if (request.voteType() == VoteType.NEUTRAL) {

      LOGGER.info("Removing vote for userId={} on entityId={}", userId, request.entityId());

      return itemVoteDao
          .deleteVote(userId, request.entityId())
          .onSuccess(
              v ->
                  LOGGER.info(
                      "Vote removed successfully for userId={} on entityId={}",
                      userId,
                      request.entityId()))
          .onFailure(
              err ->
                  LOGGER.error(
                      "Failed to remove vote for userId={} on entityId={}",
                      userId,
                      request.entityId(),
                      err))
          .map(
              v ->
                  new VoteResponse(
                      userId, request.entityId(), request.entityType(), VoteType.NEUTRAL));
    }

    // -----------------------------
    // LIKE / DISLIKE → upsert vote
    // -----------------------------
    ItemVote vote =
        new ItemVote(
            null, userId, request.entityId(), request.entityType(), request.voteType(), null, null);

    LOGGER.info(
        "Upserting vote: userId={}, entityId={}, entityType={}, voteType={}",
        userId,
        request.entityId(),
        request.entityType(),
        request.voteType());

    return itemVoteDao
        .upsertVote(vote)
        .onSuccess(
            res ->
                LOGGER.info(
                    "Vote upsert successful: userId={}, entityId={}, voteType={}",
                    userId,
                    request.entityId(),
                    request.voteType()))
        .onFailure(
            err ->
                LOGGER.error(
                    "Vote upsert failed: userId={}, entityId={}, voteType={}",
                    userId,
                    request.entityId(),
                    request.voteType(),
                    err))
        .map(
            res -> {

              LOGGER.info("EntityId: {}", res.entity().entityId());
              LOGGER.info("VoteType: {}", res.entity().voteType());
              LOGGER.info("isCreated: {}", res.created());
              ItemVote upsertedVote = res.entity();
              return new VoteResponse(
                  upsertedVote.userId(),
                  upsertedVote.entityId(),
                  upsertedVote.entityType(),
                  upsertedVote.voteType());
            });
  }
}
