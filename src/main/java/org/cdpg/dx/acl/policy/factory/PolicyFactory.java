package org.cdpg.dx.acl.policy.factory;

import static org.cdpg.dx.aaa.common.Constants.DELETED_DOCS_INDEX;
import static org.cdpg.dx.aaa.common.Constants.DOC_INDEX;
import static org.cdpg.dx.database.elastic.util.Constants.APD_URL;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import java.util.logging.Logger;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.item.service.ItemServiceImpl;
import org.cdpg.dx.acl.policy.controller.PolicyController;
import org.cdpg.dx.acl.policy.dao.PolicyDao;
import org.cdpg.dx.acl.policy.dao.impl.PolicyDaoImpl;
import org.cdpg.dx.acl.policy.service.PolicyService;
import org.cdpg.dx.acl.policy.service.impl.PolicyServiceImpl;
import org.cdpg.dx.acl.rule.dao.AccessRuleDao;
import org.cdpg.dx.acl.rule.dao.impl.AccessRuleDaoImpl;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

public class PolicyFactory {
  private static final Logger LOGGER = Logger.getLogger(PolicyFactory.class.getName());

  public static PolicyController createPolicyController(
      PostgresService pgService,
      ElasticsearchService elasticsearchService,
      KeycloakUserService keycloakUserService,
      AuditingHandler auditingHandler,
      URNGenerator urnGenerator,
      WebClient webClient,
      JsonObject config) {
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
            config.getString(DELETED_DOCS_INDEX),
            config.getString(APD_URL));

    PolicyService policyService =
        new PolicyServiceImpl(
            itemService, keycloakUserService, policyDao, accessRuleDao, config.getString(APD_URL));

    return new PolicyController(
        policyService, pgService, auditingHandler, keycloakUserService, urnGenerator, config);
  }
}
