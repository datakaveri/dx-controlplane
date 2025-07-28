package org.cdpg.dx.aaa.item.factory;

import org.cdpg.dx.aaa.item.controller.ItemController;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.item.service.ItemServiceImpl;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;

public class ItemControllerFactory {

  public static ItemController createCrudController(
      AuditingHandler auditingHandler,
      ElasticsearchService elasticsearchService,
      String docIndex,
      String vocContext) {
    ItemService crudService = new ItemServiceImpl(elasticsearchService, docIndex);
    return new ItemController(auditingHandler, crudService, vocContext);
  }
}
