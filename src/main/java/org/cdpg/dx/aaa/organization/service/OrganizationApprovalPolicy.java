package org.cdpg.dx.aaa.organization.service;

import org.cdpg.dx.aaa.organization.models.OrganizationApprovalType;

public interface OrganizationApprovalPolicy {

  /**
   * Whether a dept_admin below the root may approve this type of request at
   * all (scoped to their own subtree, via ancestor-or-self containment --
   * see {@code OrganizationApprovalServiceImpl}). When false, only the root
   * org_admin may approve, regardless of department.
   */
  boolean isDelegable(OrganizationApprovalType approvalType);
}
