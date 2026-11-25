package org.cdpg.dx.aaa.organization.models;

public enum OrganizationApprovalType {
  ORGANIZATION_USER_JOIN,
  PROVIDER_ROLE,
  DEPT_ADMIN_SELF_REQUEST,
  // Joining an organisation as the admin of the selected department. Like a
  // self-request, only someone strictly above that department may approve.
  DEPT_ADMIN_JOIN
}
