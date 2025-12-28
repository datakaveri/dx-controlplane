package org.cdpg.dx.aaa.apiserver;

public final class OperationIds {

  private OperationIds() {}

  /* =====================================================
   * Organization – Create request
   * ===================================================== */

  public static final String OP_GET_ORG_CREATE_REQUESTS = "get-auth-v2-organisations-request";

  public static final String OP_GET_USER_ORG_CREATE_REQUESTS =
      "get-auth-v2-user-organisations-request";

  public static final String OP_DELETE_USER_ORG_CREATE_REQUEST =
      "delete-auth-v2-user-organisations-request";

  public static final String OP_CREATE_ORG_REQUEST = "post-auth-v2-organisations-request";

  public static final String OP_APPROVE_ORG_CREATE_REQUEST = "post-auth-v2-approve-create_org";

  /* =====================================================
   * Organization – Join request
   * ===================================================== */

  public static final String OP_CREATE_ORG_JOIN_REQUEST =
      "post-auth-v2-organisations-join-requests";

  public static final String OP_GET_ORG_JOIN_REQUESTS = "get-auth-v2-organisations-join-requests";

  public static final String OP_GET_USER_ORG_JOIN_REQUESTS =
      "get-auth-v2-user-organisations-join-requests";

  public static final String OP_DELETE_USER_ORG_JOIN_REQUEST =
      "delete-auth-v2-user-organisations-join-requests";

  public static final String OP_APPROVE_ORG_JOIN_REQUEST =
      "put-auth-v2-organisations-join-requests";

  /* =====================================================
   * Organization – Core
   * ===================================================== */

  public static final String OP_LIST_ORGANISATIONS = "get-auth-v2-org";

  public static final String OP_GET_ORGANISATION_BY_ID = "get-auth-v2-organisations-id";

  public static final String OP_UPDATE_ORGANISATION_BY_ID = "put-auth-v2-organisations-id";

  public static final String OP_DELETE_ORGANISATION_BY_ID = "delete-auth-v2-organisations-id";

  /* =====================================================
   * Organization – Users
   * ===================================================== */

  public static final String OP_GET_ORG_USERS = "get-auth-v2-org-users";

  public static final String OP_GET_ORG_USER_INFO = "get-auth-v2-organisations-id-users-user_id";

  public static final String OP_DELETE_ORG_USER = "delete-auth-v2-organisations-users-id";

  public static final String OP_UPDATE_ORG_USER_ROLE = "put-auth-v2-organization-users-role";

  /* =====================================================
   * Provider roles
   * ===================================================== */

  public static final String OP_CREATE_PROVIDER_REQUEST = "post-auth-v2-user-roles";

  public static final String OP_GET_PROVIDER_REQUEST = "get-auth-v2-orgid-provider-requests";

  public static final String OP_UPDATE_PROVIDER_REQUEST = "put-auth-v2-user-roles";

  public static final String OP_GET_USER_PROVIDER_REQUESTS = "get-auth-v2-user-provider-requests";

  public static final String OP_DELETE_USER_PROVIDER_REQUEST =
      "delete-auth-v2-user-provider-requests";

  public static final String OP_CREATE_PROVIDER_ROLE = "post-auth-v2-organization-user-provider";

  /* =====================================================
   * Organization Reports
   * ===================================================== */

  public static final String OP_ORG_CREATE_REQUEST_REPORT =
      "get-auth-v2-organisations-requests-report";

  public static final String OP_ORG_LIST_REPORT = "get-auth-v2-organisations-report";

  public static final String OP_ORG_JOIN_REQUEST_REPORT =
      "get-auth-v2-organisations-join_requests-report";

  public static final String OP_COMPUTE_ROLE_REQUEST_REPORT = "get-auth-v2-compute-requests-report";

  public static final String OP_PROVIDER_ROLE_REQUEST_REPORT =
      "get-auth-v2-organization-user-provider_role-requests-report";

  public static final String OP_CREDIT_REQUEST_REPORT = "get-auth-v2-credit-request-report";
}
