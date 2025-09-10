package org.cdpg.dx.aaa.item.factory;

import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_REQUEST_ID;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.REQUEST_TABLE;

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

public class ItemControllerFactory {

  public static ItemController createCrudController(
      AuditingHandler auditingHandler,
      ElasticsearchService elasticsearchService,
      PostgresService pgService,
      String docIndex,
      String vocContext,
      URNGenerator urnGenerator) {
    AccessRequestDao accessRequestDao =
        new AccessRequestDaoImpl(pgService, REQUEST_TABLE, DB_REQUEST_ID, AccessRequestDto::new);

    ItemService crudService = new ItemServiceImpl(elasticsearchService, docIndex, accessRequestDao);
    return new ItemController(auditingHandler, crudService, vocContext, urnGenerator);
  }
}
