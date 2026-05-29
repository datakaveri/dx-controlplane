package org.cdpg.dx.aaa.list.factory;

import org.cdpg.dx.aaa.list.controller.ListController;
import org.cdpg.dx.aaa.list.service.ListService;
import org.cdpg.dx.aaa.list.service.ListServiceImpl;
import org.cdpg.dx.aaa.shareAssets.service.VisibilityService;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

public class ListControllerFactory {

  public static ListController createListController(
      ElasticsearchService elasticsearchService,
      KeycloakUserService keycloakUserService,
      VisibilityService visibilityService,
      AuditingHandler auditingHandler,
      String docIndex,
      URNGenerator urnGenerator) {
    ListService listService = new ListServiceImpl(elasticsearchService, visibilityService,
        docIndex);
    return new ListController(auditingHandler, listService, keycloakUserService, urnGenerator);
  }
}
