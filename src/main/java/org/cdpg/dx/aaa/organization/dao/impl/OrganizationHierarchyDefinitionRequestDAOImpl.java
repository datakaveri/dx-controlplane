package org.cdpg.dx.aaa.organization.dao.impl;

import org.cdpg.dx.aaa.organization.config.Constants;
import org.cdpg.dx.aaa.organization.dao.OrganizationHierarchyDefinitionRequestDAO;
import org.cdpg.dx.aaa.organization.models.OrganizationHierarchyDefinitionRequest;
import org.cdpg.dx.database.postgres.base.dao.AbstractBaseDAO;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class OrganizationHierarchyDefinitionRequestDAOImpl
    extends AbstractBaseDAO<OrganizationHierarchyDefinitionRequest>
    implements OrganizationHierarchyDefinitionRequestDAO {

  public OrganizationHierarchyDefinitionRequestDAOImpl(PostgresService postgresService) {
    super(
        postgresService,
        Constants.ORG_HIERARCHY_DEFINITION_REQUEST_TABLE,
        "id",
        OrganizationHierarchyDefinitionRequest::fromJson);
  }
}
