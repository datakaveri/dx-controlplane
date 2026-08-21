package org.cdpg.dx.aaa.conversation.factory;

import org.cdpg.dx.aaa.conversation.controller.ConversationController;
import org.cdpg.dx.aaa.conversation.dao.ConversationDao;
import org.cdpg.dx.aaa.conversation.dao.RequestTypeMappingDao;
import org.cdpg.dx.aaa.conversation.dao.impl.ConversationDaoImpl;
import org.cdpg.dx.aaa.conversation.dao.impl.RequestTypeMappingDaoImpl;
import org.cdpg.dx.aaa.conversation.service.ConversationParticipantService;
import org.cdpg.dx.aaa.conversation.service.ConversationService;
import org.cdpg.dx.aaa.conversation.service.impl.ConversationParticipantServiceImpl;
import org.cdpg.dx.aaa.conversation.service.impl.ConversationServiceImpl;
import org.cdpg.dx.aaa.delegation.service.DelegationService;
import org.cdpg.dx.aaa.email.util.EmailComposer;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.aaa.user.service.UserService;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class ConversationControllerFactory {

  private ConversationControllerFactory() {}

  public static ConversationController create(
      PostgresService postgresService,
      OrganizationService organizationService,
      DelegationService delegationService,
      UserService userService,
      EmailComposer emailComposer,
      URNGenerator urnGenerator) {
    ConversationDao dao = new ConversationDaoImpl(postgresService);
    RequestTypeMappingDao requestTypeMappingDao = new RequestTypeMappingDaoImpl(postgresService);
    ConversationService service = new ConversationServiceImpl(dao, requestTypeMappingDao);
    ConversationParticipantService conversationParticipantService =
        new ConversationParticipantServiceImpl(organizationService, delegationService, userService);
    return new ConversationController(
        service, conversationParticipantService, emailComposer, urnGenerator);
  }
}
