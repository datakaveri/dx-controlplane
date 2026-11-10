package org.cdpg.dx.aaa.conversation.dao.impl;

import org.cdpg.dx.aaa.conversation.dao.RequestTypeMappingDao;
import org.cdpg.dx.aaa.conversation.model.RequestTypeMapping;
import org.cdpg.dx.database.postgres.base.dao.AbstractBaseDAO;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class RequestTypeMappingDaoImpl extends AbstractBaseDAO<RequestTypeMapping>
    implements RequestTypeMappingDao {

  public RequestTypeMappingDaoImpl(PostgresService postgresService) {
    super(postgresService, "request_type_mapping", "id", RequestTypeMapping::fromJson);
  }
}
