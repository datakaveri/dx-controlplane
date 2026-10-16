package org.cdpg.dx.aaa.organization.service.impl;

import org.cdpg.dx.aaa.organization.models.OrganizationApprovalType;
import org.cdpg.dx.aaa.organization.service.OrganizationApprovalPolicy;

public class OrganizationApprovalPolicyImpl implements OrganizationApprovalPolicy {

  @Override
  public boolean isDelegable(OrganizationApprovalType approvalType) {
    return switch (approvalType) {
      case ORGANIZATION_USER_JOIN, PROVIDER_ROLE, DEPT_ADMIN_SELF_REQUEST -> true;
    };
  }
}
