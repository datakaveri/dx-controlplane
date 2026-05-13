package org.cdpg.dx.aaa.interaction.v2.factory;

import org.cdpg.dx.aaa.interaction.v2.controller.UserInteractionV2Controller;
import org.cdpg.dx.aaa.interaction.v2.dao.ProviderFeedbackDao;
import org.cdpg.dx.aaa.interaction.v2.dao.UserFeedbackDao;
import org.cdpg.dx.aaa.interaction.v2.dao.UserInteractionV2Dao;
import org.cdpg.dx.aaa.interaction.v2.dao.impl.ProviderFeedbackDaoImpl;
import org.cdpg.dx.aaa.interaction.v2.dao.impl.UserFeedbackDaoImpl;
import org.cdpg.dx.aaa.interaction.v2.dao.impl.UserInteractionV2DaoImpl;
import org.cdpg.dx.aaa.interaction.v2.service.UserInteractionV2Service;
import org.cdpg.dx.aaa.interaction.v2.service.impl.UserInteractionV2ServiceImpl;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class UserInteractionV2controllerFactory {

  public static UserInteractionV2Controller create(
      PostgresService postgresService,
      ItemService itemService,
      AuditingHandler auditingHandler,
      URNGenerator urnGenerator) {

    UserInteractionV2Dao dao = new UserInteractionV2DaoImpl(postgresService);
    UserFeedbackDao userFeedbackDao = new UserFeedbackDaoImpl(postgresService);
    ProviderFeedbackDao providerFeedbackDao = new ProviderFeedbackDaoImpl(postgresService);
    UserInteractionV2Service service = new UserInteractionV2ServiceImpl(dao, userFeedbackDao,providerFeedbackDao,itemService);
    return new UserInteractionV2Controller(
        auditingHandler,
        service,
        urnGenerator);
  }
}
