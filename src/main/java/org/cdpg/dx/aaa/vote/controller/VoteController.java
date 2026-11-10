package org.cdpg.dx.aaa.vote.controller;

import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.apiserver.ApiController;
import org.cdpg.dx.aaa.vote.model.VoteRequest;
import org.cdpg.dx.aaa.vote.service.ItemVoteService;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.response.ResponseBuilder;

import static org.cdpg.dx.aaa.apiserver.OperationIds.OP_POST_ITEM_VOTE;

public class VoteController implements ApiController {
  private static final Logger LOGGER = LogManager.getLogger(VoteController.class);
  private final ItemVoteService voteService;
  private final URNGenerator urnGenerator;

  public VoteController(ItemVoteService voteService, URNGenerator urnGenerator) {
    this.voteService = voteService;
    this.urnGenerator = urnGenerator;
  }

  @Override
  public void register(RouterBuilder builder) {
    LOGGER.info("Registering VoteController routes");

    builder.operation(OP_POST_ITEM_VOTE).handler(this::voteHandler);
  }

  public void voteHandler(RoutingContext ctx) {
    VoteRequest req = ctx.body().asJsonObject().mapTo(VoteRequest.class);

    UUID userId = UUID.fromString(ctx.user().subject());

    voteService
        .castVote(userId, req)
        .onSuccess(
            result -> {
              ResponseBuilder.sendSuccess(ctx, result, urnGenerator);
            })
        .onFailure(ctx::fail);
  }
}
