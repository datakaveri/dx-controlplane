package org.cdpg.dx.aaa.organization.dao.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.organization.dao.OrganizationCreateRequestDAO;
import org.cdpg.dx.aaa.organization.models.OrganizationCreateRequest;
import org.cdpg.dx.aaa.organization.config.Constants;
import org.cdpg.dx.database.postgres.base.dao.AbstractBaseDAO;
import org.cdpg.dx.database.postgres.service.PostgresService;

import static org.cdpg.dx.aaa.organization.config.Constants.*;

public class OrganizationCreateRequestDAOImpl extends AbstractBaseDAO<OrganizationCreateRequest> implements OrganizationCreateRequestDAO {

    private static final Logger LOGGER = LogManager.getLogger(OrganizationCreateRequestDAOImpl.class);

    public OrganizationCreateRequestDAOImpl(PostgresService postgresService) {
        super(postgresService, Constants.ORG_CREATE_REQUEST_TABLE, ORG_CREATE_ID, OrganizationCreateRequest::fromJson);
    }

}
