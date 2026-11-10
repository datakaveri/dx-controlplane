package org.cdpg.dx.aaa.organization.dao;

import io.vertx.core.Future;
import java.util.List;
import java.util.UUID;
import org.cdpg.dx.aaa.organization.models.OrganizationApproval;
import org.cdpg.dx.aaa.organization.models.OrganizationApprovalType;
import org.cdpg.dx.database.postgres.base.dao.BaseDAO;

public interface OrganizationApprovalDAO extends BaseDAO<OrganizationApproval> {

  Future<OrganizationApproval> getPendingApproval(
      UUID requestId, OrganizationApprovalType approvalType);

  Future<List<OrganizationApproval>> getByRequest(
      UUID requestId, OrganizationApprovalType approvalType);
}
