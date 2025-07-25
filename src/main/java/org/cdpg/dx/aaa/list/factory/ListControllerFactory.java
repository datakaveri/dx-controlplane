package org.cdpg.dx.aaa.list.factory;

import org.cdpg.dx.aaa.list.controller.ListController;
import org.cdpg.dx.aaa.list.service.ListService;
import org.cdpg.dx.aaa.list.service.ListServiceImpl;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;

public class ListControllerFactory {

  public static ListController createListController(
      ElasticsearchService elasticsearchService, AuditingHandler auditingHandler, String docIndex) {
    ListService listService = new ListServiceImpl(elasticsearchService, docIndex);
    return new ListController(auditingHandler, listService);
  }
}
