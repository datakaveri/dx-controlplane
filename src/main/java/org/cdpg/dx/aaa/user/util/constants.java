package org.cdpg.dx.aaa.user.util;

import java.util.Map;

public class constants {
  public static final String CUSTOM_ROLE_ID = "id";
  public static final String SCOPE = "scope";
  public static final String ROLE = "role";
  public static final String USER_ID = "user_id";
  public static final String CREATED_AT = "created_at";
  public static final String UPDATED_AT = "updated_at";
  public static final String CUSTOM_ROLE_SCOPE_TABLE = "custom_user_role";


  // Fields that can be used for filtering in requests
  public static final Map<String, String> ALLOWED_FILTER_MAP_FOR_CUSTOM_ROLE = Map.of(
    "userId", "user_id",
    "role", "role",
    "createdAt", "created_at",
    "updatedAt", "updated_at"
  );

  // Fields that can be used for sorting/filtering mapping API -> DB
  public static final Map<String, String> API_TO_DB_CUSTOM_ROLE = Map.of(
    "id", "id",
    "userId", "user_id",
    "role", "role",
    "scope", "scope",
    "createdAt", "created_at",
    "updatedAt", "updated_at"
  );

}
