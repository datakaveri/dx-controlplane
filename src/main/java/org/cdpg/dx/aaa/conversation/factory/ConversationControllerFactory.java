package org.cdpg.dx.aaa.conversation.factory;

import org.cdpg.dx.aaa.conversation.controller.ConversationController;
import org.cdpg.dx.aaa.conversation.dao.ConversationDao;
import org.cdpg.dx.aaa.conversation.dao.RequestTypeMappingDao;
import org.cdpg.dx.aaa.conversation.dao.impl.ConversationDaoImpl;
import org.cdpg.dx.aaa.conversation.dao.impl.RequestTypeMappingDaoImpl;
import org.cdpg.dx.aaa.conversation.service.ConversationService;
import org.cdpg.dx.aaa.conversation.service.impl.ConversationServiceImpl;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class ConversationControllerFactory {

  private ConversationControllerFactory() {}

  public static ConversationController create(PostgresService postgresService, URNGenerator urnGenerator) {
    ConversationDao dao = new ConversationDaoImpl(postgresService);
    RequestTypeMappingDao requestTypeMappingDao = new RequestTypeMappingDaoImpl(postgresService);
    ConversationService service = new ConversationServiceImpl(dao, requestTypeMappingDao);
    return new ConversationController(service, urnGenerator);
  }
}
