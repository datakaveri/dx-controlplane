package org.cdpg.dx.aaa.aclserver.dao.impl;

import static org.cdpg.dx.aaa.aclserver.config.Constants.ACL_SERVER_TABLE;
import static org.cdpg.dx.aaa.aclserver.config.Constants.ID;

import org.cdpg.dx.aaa.aclserver.dao.AclServerDAO;
import org.cdpg.dx.aaa.aclserver.model.AclServer;
import org.cdpg.dx.database.postgres.base.dao.AbstractBaseDAO;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class AclServerDAOImpl extends AbstractBaseDAO<AclServer> implements AclServerDAO {
  public AclServerDAOImpl(PostgresService postgresService) {
    super(postgresService, ACL_SERVER_TABLE, ID, AclServer::fromJson);
  }
}
