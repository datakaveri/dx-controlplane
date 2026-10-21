package org.cdpg.dx.aaa.organization.dao.impl;

import io.vertx.core.Future;

import java.util.List;
import java.util.UUID;

import org.cdpg.dx.aaa.organization.config.Constants;
import org.cdpg.dx.aaa.organization.dao.OrganizationApprovalDAO;
import org.cdpg.dx.aaa.organization.models.OrganizationApproval;
import org.cdpg.dx.aaa.organization.models.OrganizationApprovalType;
import org.cdpg.dx.database.postgres.base.dao.AbstractBaseDAO;
import org.cdpg.dx.database.postgres.service.PostgresService;

import static org.cdpg.dx.aaa.organization.models.OrganizationApproval.*;

public class OrganizationApprovalDAOImpl extends AbstractBaseDAO<OrganizationApproval>
    implements OrganizationApprovalDAO {

  public OrganizationApprovalDAOImpl(PostgresService postgresService) {
    super(
        postgresService, Constants.ORGANIZATION_APPROVAL_TABLE, ID, OrganizationApproval::fromJson);
  }

  @Override
  public Future<OrganizationApproval> getPendingApproval(
      UUID requestId, OrganizationApprovalType approvalType) {

    return getAllWithFilters(
            java.util.Map.of(
                REQUEST_ID, requestId.toString(),
                APPROVAL_TYPE, approvalType.name().toLowerCase(),
                STATUS, "pending"))
        .map(results -> results.isEmpty() ? null : results.getFirst());
  }

  @Override
  public Future<List<OrganizationApproval>> getByRequest(
      UUID requestId, OrganizationApprovalType approvalType) {

    return getAllWithFilters(
        java.util.Map.of(
            REQUEST_ID, requestId.toString(),
            APPROVAL_TYPE, approvalType.name().toLowerCase()));
  }
}
