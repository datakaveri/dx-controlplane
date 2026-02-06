package org.cdpg.dx.aaa.item.factory;

import io.vertx.ext.web.client.WebClient;
import org.cdpg.dx.aaa.connector.service.ConnectorService;
import org.cdpg.dx.aaa.delegation.ItemOwnershipValidator;
import org.cdpg.dx.aaa.delegation.service.DelegationService;
import org.cdpg.dx.aaa.ingestion.service.IngestionService;
import org.cdpg.dx.aaa.item.controller.ItemController;
import org.cdpg.dx.aaa.item.service.ItemRegistryService;
import org.cdpg.dx.aaa.item.service.ItemRegistryServiceImpl;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.item.service.ItemServiceImpl;
import org.cdpg.dx.aaa.item.service.central.CentralItemServiceImpl;
import org.cdpg.dx.acl.policy.dao.PolicyDao;
import org.cdpg.dx.acl.policy.dao.impl.PolicyDaoImpl;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.database.elastic.central.service.CentralElasticsearchService;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

public class ItemControllerFactory {

  public static ItemController createCrudController(
      AuditingHandler auditingHandler,
      ElasticsearchService elasticsearchService,
      CentralElasticsearchService centralElasticsearchService,
      PostgresService pgService,
      KeycloakUserService keycloakUserService,
      ItemOwnershipValidator itemOwnershipValidator,
      String centralDocIndex,
      String docIndex,
      String vocContext,
      String apdURL,
      String uploadedBy,
      URNGenerator urnGenerator,
      WebClient webClient,
      IngestionService ingestionService,
      ConnectorService connectorService,
      String dataPlaneUrl,
      String controlPlaneUrl,
      String ogcDataPlaneUrl,
      boolean isCentralCatEnabled,
      boolean isEdgeCatalogue,
      boolean isStandalone,
      DelegationService delegationService) {
    PolicyDao policyDao = new PolicyDaoImpl(pgService);
    ItemService itemService =
        new ItemServiceImpl(
            elasticsearchService, keycloakUserService, policyDao, webClient, docIndex, apdURL);
    ItemService centralItemService =
        new CentralItemServiceImpl(
            centralElasticsearchService,
            keycloakUserService,
            policyDao,
            webClient,
            centralDocIndex,
            apdURL);

    ItemRegistryService orchestrationService =
        new ItemRegistryServiceImpl(
            itemService,
            centralItemService,
            ingestionService,
            connectorService,
            webClient,
            dataPlaneUrl,
            controlPlaneUrl,
            ogcDataPlaneUrl,
            isCentralCatEnabled);
    return new ItemController(
        auditingHandler,
        itemService,
        itemOwnershipValidator,
        centralItemService,
        vocContext,
        uploadedBy,
        isCentralCatEnabled,
        urnGenerator,
        orchestrationService,
        delegationService,
        keycloakUserService);
  }
}
