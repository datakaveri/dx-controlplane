package org.cdpg.dx.acl.accessRequest.factory;

import static org.cdpg.dx.aaa.common.Constants.DOC_INDEX;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_REQUEST_ID;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.REQUEST_TABLE;
import static org.cdpg.dx.database.elastic.util.Constants.APD_URL;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import java.util.logging.Logger;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.item.service.ItemServiceImpl;
import org.cdpg.dx.acl.accessRequest.controller.AccessRequestController;
import org.cdpg.dx.acl.accessRequest.dao.AccessRequestDao;
import org.cdpg.dx.acl.accessRequest.dao.impl.AccessRequestDaoImpl;
import org.cdpg.dx.acl.accessRequest.dao.model.AccessRequestDto;
import org.cdpg.dx.acl.accessRequest.service.AccessRequestService;
import org.cdpg.dx.acl.accessRequest.service.impl.AccessRequestServiceImpl;
import org.cdpg.dx.acl.policy.dao.PolicyDao;
import org.cdpg.dx.acl.policy.dao.impl.PolicyDaoImpl;
import org.cdpg.dx.acl.rule.dao.AccessRuleDao;
import org.cdpg.dx.acl.rule.dao.impl.AccessRuleDaoImpl;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.cdpg.dx.databroker.service.DataBrokerService;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

public class AccessRequestFactory {
  private static final Logger LOGGER = Logger.getLogger(AccessRequestFactory.class.getName());

  private AccessRequestFactory() {
    throw new IllegalStateException("Utility class");
  }

  public static AccessRequestController createAccessRequestController(
      PostgresService pgService,
      ElasticsearchService elasticsearchService,
      DataBrokerService dataBrokerService,
      KeycloakUserService keycloakUserService,
      AuditingHandler auditingHandler,
      JsonObject config,
      URNGenerator urnGenerator,
      WebClient webClient,
      String emailExchange,
      String emailRoutingKey) {
    AccessRequestDao accessRequestDao =
        new AccessRequestDaoImpl(pgService, REQUEST_TABLE, DB_REQUEST_ID, AccessRequestDto::new);

    PolicyDao policyDao = new PolicyDaoImpl(pgService);
    AccessRuleDao accessRuleDao = new AccessRuleDaoImpl(pgService);
    ItemService itemService =
        new ItemServiceImpl(
            elasticsearchService,
            keycloakUserService,
            pgService,
            policyDao,
            webClient,
            config.getString(DOC_INDEX),
            config.getString(APD_URL));

    AccessRequestService accessRequestService =
        new AccessRequestServiceImpl(keycloakUserService, itemService, accessRequestDao,
            policyDao, accessRuleDao);

    return new AccessRequestController(
        accessRequestService,
        auditingHandler,
        dataBrokerService,
        urnGenerator,
        pgService,
        keycloakUserService,
        emailExchange,
        emailRoutingKey);
  }
}
