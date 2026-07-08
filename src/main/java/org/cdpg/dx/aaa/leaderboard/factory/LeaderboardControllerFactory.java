package org.cdpg.dx.aaa.leaderboard.factory;

import org.cdpg.dx.aaa.leaderboard.controller.LeaderboardController;
import org.cdpg.dx.aaa.leaderboard.dao.LeaderboardQueryDao;
import org.cdpg.dx.aaa.leaderboard.dao.LeaderboardQueryDaoImpl;
import org.cdpg.dx.aaa.leaderboard.service.LeaderboardService;
import org.cdpg.dx.aaa.leaderboard.service.impl.LeaderboardServiceImpl;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class LeaderboardControllerFactory {
  private LeaderboardControllerFactory() {}

  public static LeaderboardController create(
      PostgresService postgresService, URNGenerator urnGenerator) {
    LeaderboardQueryDao leaderboardQueryDao = new LeaderboardQueryDaoImpl(postgresService);
    LeaderboardService leaderboardService = new LeaderboardServiceImpl(leaderboardQueryDao);
    return new LeaderboardController(leaderboardService, urnGenerator);
  }
}
