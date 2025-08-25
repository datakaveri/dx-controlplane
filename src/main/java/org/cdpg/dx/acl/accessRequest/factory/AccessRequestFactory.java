package org.cdpg.dx.acl.accessRequest.factory;

import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_REQUEST_ID;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.REQUEST_TABLE;

import io.vertx.core.json.JsonObject;
import java.util.logging.Logger;
import org.cdpg.dx.acl.accessRequest.controller.AccessRequestController;
import org.cdpg.dx.acl.accessRequest.dao.AccessRequestDao;
import org.cdpg.dx.acl.accessRequest.dao.impl.AccessRequestDaoImpl;
import org.cdpg.dx.acl.accessRequest.dao.model.AccessRequestDto;
import org.cdpg.dx.acl.accessRequest.service.AccessRequestService;
import org.cdpg.dx.acl.accessRequest.service.impl.AccessRequestServiceImpl;
import org.cdpg.dx.acl.aclEmailHelper.EmailComposer;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.item.service.ItemServiceImpl;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.common.URNGenerator;
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
    JsonObject config,
    URNGenerator urnGenerator) {

    ItemService itemService =
      new ItemServiceImpl(elasticsearchService, config.getString("docIndex"));

    AccessRequestDao accessRequestDao =
      new AccessRequestDaoImpl(pgService, REQUEST_TABLE, DB_REQUEST_ID, AccessRequestDto::new);

    AccessRequestService accessRequestService =
      new AccessRequestServiceImpl(itemService, accessRequestDao);

    EmailComposer emailComposer = new EmailComposer(emailService, keycloakUserService, config);

    return new AccessRequestController(accessRequestService, auditingHandler, emailComposer,urnGenerator);
  }
}
