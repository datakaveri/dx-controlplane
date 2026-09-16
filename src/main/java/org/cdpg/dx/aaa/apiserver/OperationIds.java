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

  public static final String OP_WITHDRAW_USER_ORG_JOIN_REQUESTS =
      "withdraw-auth-v2-organisations-join-request";

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

  public static final String OP_GET_PROVIDER_REQUEST = "get-auth-v2-provider-requests";

  public static final String OP_UPDATE_PROVIDER_REQUEST = "put-auth-v2-user-roles";

  public static final String OP_GET_USER_PROVIDER_REQUESTS = "get-auth-v2-user-provider-requests";

  public static final String OP_DELETE_USER_PROVIDER_REQUEST =
      "delete-auth-v2-user-provider-requests";

  public static final String OP_CREATE_PROVIDER_ROLE = "post-auth-v2-organization-user-provider";

  public static final String OP_CREATE_PLATFORM_PROVIDER_REQUEST = "post-auth-v2-platform-provider-requests";

  public static final String OP_GET_PLATFORM_PROVIDER_REQUESTS = "get-auth-v2-platform-provider-requests";

  public static final String OP_UPDATE_PLATFORM_PROVIDER_REQUEST = "patch-auth-v2-platform-provider-requests";

  public static final String OP_GET_USER_PLATFORM_PROVIDER_REQUEST = "get-auth-v2-user-platform-provider-requests";

  public static final String OP_DELETE_USER_PLATFORM_PROVIDER_REQUEST = "delete-auth-v2-user-platform-provider-requests";

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

  /* =====================================================
   * Bookmarks
   * ===================================================== */
  public static final String OP_POST_BOOKMARK = "post-bookmark";
  public static final String OP_GET_BOOKMARKS = "get-bookmarks";
  public static final String OP_DELETE_BOOKMARK = "delete-bookmark";

  /* ===================================================== */
  public static final String OP_POST_APPID = "post-appId";
  public static final String OP_GET_APPID = "get-appId";
  public static final String OP_DELETE_APPID = "delete-appId";
  public static final String OP_UPDATE_STATUS_APPID = "update-appId-status";
  public static final String OP_POST_APPID_DX_USER = "post-appId-dxUser";
  /* =====================================================
   * Toekens
   * ===================================================== */
  public static final String OP_POST_APP_TOKEN = "post-auth-v2-app-token";
  public static final String OP_POST_ClIENT_TOKEN = "post-auth-v2-token";

  public static final String OP_GET_DASHBOARD_USAGE_SUMMARY = "get-dashboard-usage-summary";
  public static final String OP_POST_ITEM_VOTE = "post-item-vote";

  /* =====================================================
   * Leaderboard
   * ===================================================== */
  public static final String OP_GET_ORG_LEADERBOARD = "get-organisation-leaderboard";
  public static final String OP_GET_PROVIDER_LEADERBOARD = "get-provider-leaderboard";
  public static final String OP_GET_ASSET_LEADERBOARD = "get-asset-leaderboard";

  public static final String OP_POST_USER_INTERACTION = "post-user-intraction";
  public static final String OP_GET_USER_INTERACTIONS = "get-user-interaction";
  public static final String OP_GET_ACTIVITY_FOR_CONSUMER = "get-ActivityLogs-for-consumer";
  public static final String OP_GET_ACTIVITY_FOR_ADMIN = "get-activityLogs-for-admin";
  public static final String OP_SYNC_INTERACTION_METRICS = "sync-interaction-metrics";
  public static final String OP_POST_USER_FEEDBACK = "post-user-feedback";
  public static final String OP_PLATFORM_PUT_USER_FEEDBACK = "put-platform-user-feedback";
  public static final String OP_PUT_USER_FEEDBACK = "put-user-feedback";
  public static final String OP_GET_PLATFORM_USER_FEEDBACK = "get-platform-user-feedback";
  public static final String OP_GET_USER_FEEDBACK = "get-my-user-feedback";
  public static final String OP_DELETE_USER_FEEDBACK = "delete-user-feedback";
  public static final String OP_POST_PROVIDER_FEEDBACK = "post-provider-feedback";
  public static final String OP_GET_PROVIDER_FEEDBACK = "get-provider-feedback";
  public static final String OP_DELETE_PROVIDER_FEEDBACK = "delete-provider-feedback";

  public static final String OP_GET_CONVERSATION_MESSAGES = "get-request-conversations";
  public static final String OP_GET_CONVERSATION_MESSAGE = "get-request-conversation-by-id";
  public static final String OP_CREATE_CONVERSATION_MESSAGE = "post-request-conversation";
  public static final String OP_REPLY_CONVERSATION_MESSAGE = "post-request-conversation-reply";
  public static final String OP_UPDATE_CONVERSATION_MESSAGE = "put-request-conversation";
  public static final String OP_DELETE_CONVERSATION_MESSAGE = "delete-request-conversation";
  public static final String OP_GET_CONVERSATION_BY_REQUEST_TYPE = "get-conversations-by-request-type";
  public static final String OP_GET_THREADED_MESSAGE = "get-threaded-message";
}
