package org.cdpg.dx.aaa.resourceserver.dao.impl;

import org.cdpg.dx.aaa.resourceserver.dao.ResourceServerDAO;
import org.cdpg.dx.aaa.resourceserver.models.ResourceServer;
import org.cdpg.dx.database.postgres.base.dao.AbstractBaseDAO;
import org.cdpg.dx.database.postgres.service.PostgresService;

import static org.cdpg.dx.aaa.resourceserver.config.Constants.RESOURCE_SERVER_TABLE;
import static org.cdpg.dx.aaa.resourceserver.config.Constants.ID;

public class ResourceServerDAOImpl extends AbstractBaseDAO<ResourceServer> implements ResourceServerDAO {

  public ResourceServerDAOImpl(PostgresService postgresService) {
    super(postgresService, RESOURCE_SERVER_TABLE, ID, ResourceServer::fromJson);
  }
}


