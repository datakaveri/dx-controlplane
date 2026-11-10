package org.cdpg.dx.aaa.vote.service;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.vote.model.VoteRequest;
import org.cdpg.dx.aaa.vote.model.VoteResponse;

import java.util.UUID;

public interface ItemVoteService {

  Future<VoteResponse> castVote(UUID userId, VoteRequest request);
}
