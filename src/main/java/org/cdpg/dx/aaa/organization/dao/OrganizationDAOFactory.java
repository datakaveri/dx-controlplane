package org.cdpg.dx.aaa.organization.dao;

import org.cdpg.dx.aaa.organization.dao.impl.*;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class OrganizationDAOFactory {
    private final PostgresService postgresService;

    public OrganizationDAOFactory(PostgresService postgresService) {
        this.postgresService = postgresService;
    }

    public OrganizationDAO organizationDAO() {
        return new OrganizationDAOImpl(postgresService);
    }

    public OrganizationCreateRequestDAO organizationCreateRequest() {
        return new OrganizationCreateRequestDAOImpl(postgresService);
    }

    public OrganizationUserDAO organizationUserDAO() {
        return new OrganizationUserDAOImpl(postgresService);
    }

    public OrganizationJoinRequestDAO organizationJoinRequestDAO() {
        return new OrganizationJoinRequestDAOImpl(postgresService);
    }

    public ProviderRoleRequestDAO providerRoleRequestDAO() {
        return new ProviderRoleRequestDAOImpl(postgresService);
    }

    public OrganizationHierarchyNodeDAO organizationHierarchyNodeDAO() {
        return new OrganizationHierarchyNodeDAOImpl(postgresService);
    }

    public OrganizationApprovalDAO organizationApprovalDAO() {
        return new OrganizationApprovalDAOImpl(postgresService);
    }

    public OrganizationHierarchyDefinitionRequestDAO organizationHierarchyDefinitionRequestDAO() {
        return new OrganizationHierarchyDefinitionRequestDAOImpl(postgresService);
    }

    public DeptAdminRequestDAO deptAdminRequestDAO() {
        return new DeptAdminRequestDAOImpl(postgresService);
    }
}
