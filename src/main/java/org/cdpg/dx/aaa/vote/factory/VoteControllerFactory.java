package org.cdpg.dx.aaa.vote.factory;

import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.vote.controller.VoteController;
import org.cdpg.dx.aaa.vote.dao.ItemVoteDao;
import org.cdpg.dx.aaa.vote.dao.impl.ItemVoteDaoImpl;
import org.cdpg.dx.aaa.vote.service.ItemVoteService;
import org.cdpg.dx.aaa.vote.service.impl.ItemVoteServiceImplImpl;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class VoteControllerFactory {
  private VoteControllerFactory() {}

  public static VoteController create(PostgresService postgresService, URNGenerator urnGenerator) {

    ItemVoteDao itemVoteDao = new ItemVoteDaoImpl(postgresService);
    ItemVoteService itemVoteService = new ItemVoteServiceImplImpl(itemVoteDao) {};

    return new VoteController(itemVoteService, urnGenerator);
  }
}
