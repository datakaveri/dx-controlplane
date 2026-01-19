package org.cdpg.dx.aaa.leaderboard.factory;

import org.cdpg.dx.aaa.leaderboard.controller.LeaderboardController;
import org.cdpg.dx.aaa.leaderboard.dao.LeaderboardDao;
import org.cdpg.dx.aaa.leaderboard.dao.LeaderboardDaoImpl;
import org.cdpg.dx.aaa.leaderboard.service.LeaderboardService;
import org.cdpg.dx.aaa.leaderboard.service.impl.LeaderboardServiceImpl;
import org.cdpg.dx.aaa.vote.controller.VoteController;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class LeaderboardControllerFactory {
  private LeaderboardControllerFactory() {}

  public static LeaderboardController create(
      PostgresService postgresService, URNGenerator urnGenerator) {
    LeaderboardDao leaderboardDao = new LeaderboardDaoImpl(postgresService);
    LeaderboardService leaderboardService = new LeaderboardServiceImpl(leaderboardDao);
    return new LeaderboardController(leaderboardService, urnGenerator);
  }
}
