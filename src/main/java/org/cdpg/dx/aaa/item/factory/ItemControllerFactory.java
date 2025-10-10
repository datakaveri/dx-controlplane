package org.cdpg.dx.aaa.item.factory;

import io.vertx.ext.web.client.WebClient;
import org.cdpg.dx.aaa.connector.service.ConnectorService;
import org.cdpg.dx.aaa.ingestion.service.IngestionService;
import org.cdpg.dx.aaa.item.controller.ItemController;
import org.cdpg.dx.aaa.item.service.ItemRegistryService;
import org.cdpg.dx.aaa.item.service.ItemRegistryServiceImpl;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.item.service.ItemServiceImpl;
import org.cdpg.dx.acl.policy.dao.PolicyDao;
import org.cdpg.dx.acl.policy.dao.impl.PolicyDaoImpl;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

import java.util.HashMap;

public class ItemControllerFactory {

  public static ItemController createCrudController(
      AuditingHandler auditingHandler,
      ElasticsearchService elasticsearchService,
      PostgresService pgService,
      KeycloakUserService keycloakUserService,
      String docIndex,
      String vocContext,
      String apdURL,
      String verifiedBy,
      URNGenerator urnGenerator,
      WebClient webClient,
      IngestionService ingestionService,
      ConnectorService connectorService,
      HashMap<String, String> scriptConfigMap) {
      PolicyDao policyDao = new PolicyDaoImpl(pgService);
    ItemService crudService = new ItemServiceImpl(elasticsearchService, keycloakUserService,
        policyDao, webClient, docIndex, apdURL);

          ItemRegistryService orchestrationService = new ItemRegistryServiceImpl(crudService, ingestionService, connectorService, webClient, scriptConfigMap);

    return new ItemController(auditingHandler, crudService, vocContext, verifiedBy, urnGenerator,
        orchestrationService);
  }
}
