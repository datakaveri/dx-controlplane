package org.cdpg.dx.aaa.bookmarks.util;

import java.util.Map;

public class Constants {

  public static final Map<String, String> ALLOWED_FILTER_MAP_FOR_BOOKMARK_REQUEST =
      Map.of(
          "userId",
          "user_id",
          "entityId",
          "entity_id",
          "entityType",
          "entity_type",
          "createdAt",
          "created_at");
}
