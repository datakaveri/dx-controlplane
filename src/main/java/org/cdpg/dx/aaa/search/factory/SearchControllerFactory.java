package org.cdpg.dx.aaa.search.factory;

import org.cdpg.dx.aaa.search.controller.SearchController;
import org.cdpg.dx.aaa.search.service.SearchService;
import org.cdpg.dx.aaa.search.service.SearchServiceImpl;
import org.cdpg.dx.aaa.shareAssets.service.VisibilityService;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

public class SearchControllerFactory {

  public static SearchController createSearchController(
      ElasticsearchService elasticsearchService,
      KeycloakUserService keycloakUserService,
      VisibilityService visibilityService,
      AuditingHandler auditingHandler,
      String docIndex,
      URNGenerator unrGenerator) {
    SearchService searchService = new SearchServiceImpl(elasticsearchService, visibilityService,
        docIndex);
    return new SearchController(searchService, keycloakUserService, auditingHandler, unrGenerator);
  }
}
