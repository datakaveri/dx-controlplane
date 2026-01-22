package org.cdpg.dx.aaa.interaction.factory;

import org.cdpg.dx.aaa.interaction.controller.UserInteractionController;
import org.cdpg.dx.aaa.interaction.dao.UserInteractionDao;
import org.cdpg.dx.aaa.interaction.dao.impl.UserInteractionDaoImpl;
import org.cdpg.dx.aaa.interaction.service.UserInteractionService;
import org.cdpg.dx.aaa.interaction.service.impl.UserInteractionServiceImpl;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class UserInteractionControllerFactory {

  private UserInteractionControllerFactory() {}

  public static UserInteractionController create(
      PostgresService postgresService, URNGenerator urnGenerator) {

    UserInteractionDao dao = new UserInteractionDaoImpl(postgresService);
    UserInteractionService service = new UserInteractionServiceImpl(dao) {};

    return new UserInteractionController(service, urnGenerator);
  }
}
