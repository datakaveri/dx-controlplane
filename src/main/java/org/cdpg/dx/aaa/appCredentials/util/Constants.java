package org.cdpg.dx.aaa.appCredentials.util;

import java.util.Map;

public class Constants {

  public static final Map<String, String> ALLOWED_FILTER_MAP_FOR_APP_CREDENTIALS =
      Map.of(
          "userId",
          "user_id",
          "app_id",
          "appId",
          "status",
          "entity_type",
          "createdAt",
          "created_at");

  public static final String ID = "id";
  public static final String APP_ID = "app_id";
  public static final String USER_ID = "user_id";
  public static final String APP_SECRET = "app_secret_hash";
  public static final String EXPIRY_AT = "expiry_at";
  public static final String STATUS = "status";
  public static final String ROLE = "role";
  public static final String CREATED_AT = "created_at";
  public static final String MODIFIED_AT = "modified_at";
  public static final String REVOKED_AT = "revoked_at";
  public static final String SCOPE = "scope";
  public static final String ENTITY_TYPE = "entity_type";
  public static final String ENTITY_ID = "entity_id";
}
