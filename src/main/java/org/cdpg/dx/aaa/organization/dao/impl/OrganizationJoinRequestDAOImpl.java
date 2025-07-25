package org.cdpg.dx.aaa.organization.dao.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.organization.dao.OrganizationJoinRequestDAO;
import org.cdpg.dx.aaa.organization.models.OrganizationJoinRequest;
import org.cdpg.dx.aaa.organization.config.Constants;
import org.cdpg.dx.database.postgres.base.dao.AbstractBaseDAO;
import org.cdpg.dx.database.postgres.service.PostgresService;

import static org.cdpg.dx.aaa.organization.config.Constants.ORG_JOIN_ID;

public class OrganizationJoinRequestDAOImpl extends AbstractBaseDAO<OrganizationJoinRequest> implements OrganizationJoinRequestDAO {

    private static final Logger LOGGER = LogManager.getLogger(OrganizationJoinRequestDAOImpl.class);

    public OrganizationJoinRequestDAOImpl(PostgresService postgresService) {
      super(postgresService, Constants.ORG_JOIN_REQUEST_TABLE, ORG_JOIN_ID, OrganizationJoinRequest::fromJson);

    }


}
