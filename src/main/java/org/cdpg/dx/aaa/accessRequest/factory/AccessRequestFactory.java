package org.cdpg.dx.aaa.accessRequest.factory;

import static org.cdpg.dx.aaa.accessRequest.dao.config.DbConstants.DB_REQUEST_ID;
import static org.cdpg.dx.aaa.accessRequest.dao.config.DbConstants.REQUEST_TABLE;

import io.vertx.core.json.JsonObject;
import java.util.logging.Logger;
import org.cdpg.dx.aaa.accessRequest.controller.AccessRequestController;
import org.cdpg.dx.aaa.accessRequest.dao.AccessRequestDao;
import org.cdpg.dx.aaa.accessRequest.dao.impl.AccessRequestDaoImpl;
import org.cdpg.dx.aaa.accessRequest.dao.model.AccessRequestDto;
import org.cdpg.dx.aaa.accessRequest.service.AccessRequestService;
import org.cdpg.dx.aaa.accessRequest.service.impl.AccessRequestServiceImpl;
import org.cdpg.dx.aaa.aclEmailHelper.EmailComposer;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.item.service.ItemServiceImpl;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.cdpg.dx.email.service.EmailService;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

public class AccessRequestFactory {
  private static final Logger LOGGER = Logger.getLogger(AccessRequestFactory.class.getName());

  private AccessRequestFactory() {
    throw new IllegalStateException("Utility class");
  }

  public static AccessRequestController createAccessRequestController(
      PostgresService pgService,
      ElasticsearchService elasticsearchService,
      EmailService emailService,
      KeycloakUserService keycloakUserService,
      AuditingHandler auditingHandler,
      JsonObject config) {

    AccessRequestDao accessRequestDao =
        new AccessRequestDaoImpl(pgService, REQUEST_TABLE, DB_REQUEST_ID, AccessRequestDto::new);

    ItemService itemService =
        new ItemServiceImpl(elasticsearchService, config.getString("docIndex"), accessRequestDao);

    AccessRequestService accessRequestService =
        new AccessRequestServiceImpl(itemService, accessRequestDao);

    EmailComposer emailComposer = new EmailComposer(emailService, keycloakUserService, config);

    return new AccessRequestController(accessRequestService, auditingHandler, emailComposer);
  }
}
