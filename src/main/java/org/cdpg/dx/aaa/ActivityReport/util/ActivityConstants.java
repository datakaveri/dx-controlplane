package org.cdpg.dx.aaa.ActivityReport.util;

import java.util.Map;
import java.util.Set;

public final class ActivityConstants {
  // Table names
  public static final String ACTIVITY_LOG_TABLE_NAME = "user_activity_log";

  // Common fields
  public static final String ID = "id";
  public static final String USER_ID = "user_id";
  public static final String NAME = "name";
  public static final String DESCRIPTION = "description";
  public static final String CREATED_AT = "created_at";
  public static final String UPDATED_AT = "updated_at";

  // Asset activity log fields

  public static final String ASSET_NAME = "asset_name";
  public static final String ASSET_TYPE = "asset_type";
  public static final String OPERATION = "operation";
  public static final String ASSET_ID = "asset_id";
  public static final String API = "api";
  public static final String METHOD = "method";
  public static final String SIZE = "size";
  public static final String ROLE = "role";
  public static final String ORIGIN_SERVER = "origin_server";
  public static final String MYACTIVITY_ENABLED = "myactivity_enabled";
  public static final String ORGANIZATION_ID = "org_id";
  public static final String ORGANIZATION_NAME = "org_name";
  public static final String SHORT_DESCRIPTION = "short_description";

  public static final Set<String> ALLOW_FILTER_ADMIN =
      Set.of("userId", "assetType", "operation", "assetname", "api", "role");

  public static final Map<String, String> API_TO_DB_MAP_ADMIN =
      Map.of(
          "userId",
          "user_id",
          "assetType",
          "asset_type",
          "operation",
          "operation",
          "assetname",
          "asset_name",
          "api",
          "api",
          "role",
          "role");

  public static final Map<String, String> API_TO_DB_MAP_CONSUMER =
      Map.of(
          "assetType",
          "asset_type",
          "operation",
          "operation",
          "assetname",
          "asset_name",
          "api",
          "api",
          "role",
          "role");

  private ActivityConstants() {
    // Utility class; prevent instantiation
  }
}
