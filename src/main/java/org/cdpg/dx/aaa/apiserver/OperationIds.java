package org.cdpg.dx.aaa.apiserver;

public final class OperationIds {
  public OperationIds() {}

  /* ---------- Organization reports ---------- */

  public static final String OP_ORG_CREATE_REQUEST_REPORT =
      "get-auth-v2-organisations-requests-report";

  public static final String OP_ORG_LIST_REPORT = "get-auth-v2-organisations-report";

  public static final String OP_ORG_JOIN_REQUEST_REPORT =
      "get-auth-v2-organisations-join_requests-report";

  /* ---------- Role request reports ---------- */

  public static final String OP_COMPUTE_ROLE_REQUEST_REPORT = "get-auth-v2-compute-requests-report";

  public static final String OP_PROVIDER_ROLE_REQUEST_REPORT =
      "get-auth-v2-organization-user-provider_role-requests-report";

  /* ---------- Credit reports ---------- */

  public static final String OP_CREDIT_REQUEST_REPORT = "get-auth-v2-credit-request-report";
}
