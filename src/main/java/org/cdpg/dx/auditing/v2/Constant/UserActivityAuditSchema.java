package org.cdpg.dx.auditing.v2.Constant;

public final class UserActivityAuditSchema {

  private UserActivityAuditSchema() {}

  public static final String TABLE = "activity_audit_log";

  public static final String ID = "id";

  // User context
  public static final String USER_ID = "user_id";
  public static final String USER_NAME = "user_name";
  public static final String ORG_ID = "org_id";
  public static final String ORG_NAME = "org_name";
  public static final String ORG_TYPE = "org_type";
  public static final String APP_ID = "app_id";
  public static final String ROLE = "role";
  public static final String ISSUER = "issuer";

  // Delegation
  public static final String DELEGATE_ID = "delegate_id";

  // Actor classification (who actually performed the action: SELF / DELEGATE / APP)
  public static final String ACTOR_TYPE = "actor_type";

  // API metadata
  public static final String API = "api";
  public static final String HTTP_METHOD = "method";
  public static final String ACTION = "action";
  public static final String ORIGIN_SERVER = "origin_server";

  // Asset
  public static final String ASSET_ID = "asset_id";
  public static final String ASSET_NAME = "asset_name";
  public static final String ASSET_SHORT_DESCRIPTION = "asset_sort_discription";
  public static final String ASSET_TYPE = "asset_type";
  public static final String ASSET_ACCESS_POLICY = "asset_access_policy";
  public static final String ASSET_ORG_ID = "asset_org_id";
  public static final String ASSET_ORG_NAME = "asset_org_name";
  public static final String ASSET_ORG_TYPE = "asset_org_type";
  public static final String ASSET_PROVIDER_ID = "asset_provider_id";
  public static final String ASSET_PROVIDER_NAME = "asset_provider_name";

  // Metrics / workflow
  public static final String SIZE_BYTES = "size_bytes";
  public static final String AMOUNT = "amount";
  public static final String REQUEST_ID = "request_id";

  // Classification
  public static final String LOG_TYPE = "log_type";

  // Technical
  public static final String IP_ADDRESS = "ip_address";
  public static final String USER_AGENT = "user_agent";

  // Time
  public static final String CREATED_AT = "created_at";

  // JSON
  public static final String CONTEXT = "context";
  public static final String SANDBOX_TYPE = "sandbox_type";
}
