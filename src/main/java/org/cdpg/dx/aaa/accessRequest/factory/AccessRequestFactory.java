package org.cdpg.dx.aaa.accessRequest.factory;

import static org.cdpg.dx.aaa.accessRequest.dao.config.DbConstants.DB_REQUEST_ID;
import static org.cdpg.dx.aaa.accessRequest.dao.config.DbConstants.REQUEST_TABLE;
import static org.cdpg.dx.common.config.ServiceProxyAddressConstants.*;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import java.util.logging.Logger;
import org.cdpg.dx.aaa.accessRequest.controller.AccessRequestController;
import org.cdpg.dx.aaa.accessRequest.dao.AccessRequestDao;
import org.cdpg.dx.aaa.accessRequest.dao.impl.AccessRequestDaoImpl;
import org.cdpg.dx.aaa.accessRequest.dao.model.AccessRequestDto;
import org.cdpg.dx.aaa.accessRequest.service.AccessRequestService;
import org.cdpg.dx.aaa.accessRequest.service.impl.AccessRequestServiceImpl;
import org.cdpg.dx.aaa.aclEmailHelper.EmailComposer;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.catalogueService.service.CatalogueService;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.cdpg.dx.databroker.service.DataBrokerService;
import org.cdpg.dx.email.service.EmailService;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
import org.cdpg.dx.keycloak.service.KeycloakUserServiceImpl;

public class AccessRequestFactory {
  private static final Logger LOGGER = Logger.getLogger(AccessRequestFactory.class.getName());

  private AccessRequestFactory() {
    throw new IllegalStateException("Utility class");
  }

  public static AccessRequestController createAccessRequestController(
      Vertx vertx, JsonObject config) {

    CatalogueService catalogueService =
        CatalogueService.createProxy(vertx, CATALOGUE_SERVICE_ADDRESS);
    PostgresService postgresService = PostgresService.createProxy(vertx, POSTGRES_SERVICE_ADDRESS);
    AccessRequestDao accessRequestDao =
        new AccessRequestDaoImpl(
            postgresService, REQUEST_TABLE, DB_REQUEST_ID, AccessRequestDto::new);
    KeycloakUserService keycloakUserService = new KeycloakUserServiceImpl(config);
    EmailService emailService = EmailService.createProxy(vertx, EMAIL_SERVICE_ADDRESS);
    EmailComposer emailComposer = new EmailComposer(emailService, keycloakUserService, config);
    AccessRequestService accessRequestService =
        new AccessRequestServiceImpl(catalogueService, accessRequestDao);

    DataBrokerService dataBrokerService =
        DataBrokerService.createProxy(vertx, DATA_BROKER_SERVICE_ADDRESS);

    AuditingHandler auditingHandler = new AuditingHandler(dataBrokerService);

    return new AccessRequestController(accessRequestService, auditingHandler, emailComposer);
  }
}
