package org.cdpg.dx.aaa.item.factory;

import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_REQUEST_ID;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.REQUEST_TABLE;

import io.vertx.ext.web.client.WebClient;
import org.cdpg.dx.aaa.item.controller.ItemController;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.item.service.ItemServiceImpl;
import org.cdpg.dx.acl.accessRequest.dao.AccessRequestDao;
import org.cdpg.dx.acl.accessRequest.dao.impl.AccessRequestDaoImpl;
import org.cdpg.dx.acl.accessRequest.dao.model.AccessRequestDto;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
import org.cdpg.dx.aaa.ingestion.service.IngestionService;
import org.cdpg.dx.aaa.connector.service.ConnectorService;
import org.cdpg.dx.aaa.item.service.ItemRegistryService;
import org.cdpg.dx.aaa.item.service.ItemRegistryServiceImpl;

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
          URNGenerator urnGenerator,
          WebClient webClient,
          IngestionService ingestionService,
          ConnectorService connectorService,
          HashMap<String, String> scriptConfigMap){
      AccessRequestDao accessRequestDao =
        new AccessRequestDaoImpl(pgService, REQUEST_TABLE, DB_REQUEST_ID, AccessRequestDto::new);

    ItemService crudService = new ItemServiceImpl(elasticsearchService, keycloakUserService,
        accessRequestDao, webClient, docIndex, apdURL);
      ItemRegistryService orchestrationService = new ItemRegistryServiceImpl(crudService, ingestionService, connectorService, webClient, scriptConfigMap);
    return new ItemController(auditingHandler, crudService, vocContext, urnGenerator,orchestrationService);
  }
}
