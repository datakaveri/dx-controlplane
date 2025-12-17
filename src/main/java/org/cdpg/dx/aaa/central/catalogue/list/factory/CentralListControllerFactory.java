package org.cdpg.dx.aaa.central.catalogue.list.factory;

import org.cdpg.dx.aaa.central.catalogue.list.controller.CentralListController;
import org.cdpg.dx.aaa.central.catalogue.list.service.CentralListService;
import org.cdpg.dx.aaa.central.catalogue.list.service.CentralListServiceImpl;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.database.elastic.central.service.CentralElasticsearchService;

public class CentralListControllerFactory {

  public static CentralListController createListController(
      CentralElasticsearchService centralElasticsearchService, AuditingHandler auditingHandler,
      String docIndex, URNGenerator urnGenerator) {
    CentralListService centralListService = new CentralListServiceImpl(centralElasticsearchService,
        docIndex);
    return new CentralListController(auditingHandler, centralListService, urnGenerator);
  }
}
