package org.cdpg.dx.aaa.resourceserver.factory;

import org.cdpg.dx.aaa.apiserver.ApiController;
import org.cdpg.dx.aaa.resourceserver.controller.ResourceServerController;
import org.cdpg.dx.aaa.resourceserver.dao.impl.ResourceServerDAOImpl;
import org.cdpg.dx.aaa.resourceserver.service.ResourceServerService;
import org.cdpg.dx.aaa.resourceserver.service.ResourceServerServiceImpl;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class ResourceServerControllerFactory {

  private ResourceServerControllerFactory() {}

  public static ApiController createController(PostgresService pgService, AuditingHandler auditingHandler, URNGenerator urnGenerator) {
    ResourceServerService service = new ResourceServerServiceImpl(new ResourceServerDAOImpl(pgService));
    return new ResourceServerController(service, auditingHandler, urnGenerator);
  }
}


