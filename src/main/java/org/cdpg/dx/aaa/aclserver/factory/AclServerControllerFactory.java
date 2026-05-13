package org.cdpg.dx.aaa.aclserver.factory;

import org.cdpg.dx.aaa.aclserver.controller.AclServerController;
import org.cdpg.dx.aaa.aclserver.dao.impl.AclServerDAOImpl;
import org.cdpg.dx.aaa.aclserver.service.AclServerService;
import org.cdpg.dx.aaa.aclserver.service.AclServerServiceImpl;
import org.cdpg.dx.apiserver.ApiController;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class AclServerControllerFactory {
  private AclServerControllerFactory() {}

  public static ApiController createController(
      PostgresService postgresService, AuditingHandler auditingHandler, URNGenerator urnGenerator) {
    AclServerService aclServerService =
        new AclServerServiceImpl(new AclServerDAOImpl(postgresService));
    return new AclServerController(aclServerService, auditingHandler, urnGenerator);
  }
}
