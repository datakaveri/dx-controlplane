package org.cdpg.dx.acl.policy.factory;

import static org.cdpg.dx.aaa.common.Constants.DOC_INDEX;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_REQUEST_ID;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.REQUEST_TABLE;
import static org.cdpg.dx.database.elastic.util.Constants.APD_URL;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import java.util.logging.Logger;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.item.service.ItemServiceImpl;
import org.cdpg.dx.acl.accessRequest.dao.AccessRequestDao;
import org.cdpg.dx.acl.accessRequest.dao.impl.AccessRequestDaoImpl;
import org.cdpg.dx.acl.accessRequest.dao.model.AccessRequestDto;
import org.cdpg.dx.acl.policy.controller.PolicyController;
import org.cdpg.dx.acl.policy.dao.PolicyDao;
import org.cdpg.dx.acl.policy.dao.impl.PolicyDaoImpl;
import org.cdpg.dx.acl.policy.service.PolicyService;
import org.cdpg.dx.acl.policy.service.impl.PolicyServiceImpl;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.cdpg.dx.database.postgres.service.PostgresServiceImpl;
import org.cdpg.dx.keycloak.service.KeycloakUserService;


public class PolicyFactory {
  private static final Logger LOGGER = Logger.getLogger(PolicyFactory.class.getName());

  public static PolicyController createPolicyController(PostgresService pgService,
                                                        ElasticsearchService elasticsearchService,
                                                        KeycloakUserService keycloakUserService,
                                                        AuditingHandler auditingHandler,
                                                        URNGenerator urnGenerator,
                                                        WebClient webClient,
                                                        JsonObject config) {
    AccessRequestDao accessRequestDao =
        new AccessRequestDaoImpl(pgService, REQUEST_TABLE, DB_REQUEST_ID, AccessRequestDto::new);
    ItemService itemService =
        new ItemServiceImpl(elasticsearchService, keycloakUserService,
            accessRequestDao, webClient, config.getString(DOC_INDEX), config.getString(APD_URL));

    PolicyDao policyDao = new PolicyDaoImpl(pgService, itemService, config);
    PolicyService policyService  = new PolicyServiceImpl(policyDao, config);

    return new PolicyController(policyService, pgService, auditingHandler, urnGenerator, config);
  }
}