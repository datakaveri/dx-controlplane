package org.cdpg.dx.aaa.central.catalogue.search.factory;

import org.cdpg.dx.aaa.central.catalogue.search.controller.CentralSearchController;
import org.cdpg.dx.aaa.central.catalogue.search.service.CentralSearchService;
import org.cdpg.dx.aaa.central.catalogue.search.service.CentralSearchServiceImpl;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.database.elastic.central.service.CentralElasticsearchService;

public class CentralSearchControllerFactory {

  public static CentralSearchController createSearchController(
      CentralElasticsearchService centralElasticsearchService, AuditingHandler auditingHandler,
      String docIndex,
      URNGenerator unrGenerator) {
    CentralSearchService centralSearchService =
        new CentralSearchServiceImpl(centralElasticsearchService, docIndex);
    return new CentralSearchController(centralSearchService, auditingHandler, unrGenerator);
  }
}
