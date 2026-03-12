package org.cdpg.dx.database.elastic.model;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.impl.JsonUtil;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Converter and mapper for {@link org.cdpg.dx.database.elastic.model.OrderBy}.
 * NOTE: This class has been automatically generated from the {@link org.cdpg.dx.database.elastic.model.OrderBy} original class using Vert.x codegen.
 */
public class OrderByConverter {


  private static final Base64.Decoder BASE64_DECODER = JsonUtil.BASE64_DECODER;
  private static final Base64.Encoder BASE64_ENCODER = JsonUtil.BASE64_ENCODER;

  public static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, OrderBy obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "column":
          if (member.getValue() instanceof String) {
            obj.setColumn((String)member.getValue());
          }
          break;
        case "direction":
          if (member.getValue() instanceof String) {
            obj.setDirection(org.cdpg.dx.database.elastic.model.OrderBy.Direction.valueOf((String)member.getValue()));
          }
          break;
      }
    }
  }

  public static void toJson(OrderBy obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

  public static void toJson(OrderBy obj, java.util.Map<String, Object> json) {
    if (obj.getColumn() != null) {
      json.put("column", obj.getColumn());
    }
    if (obj.getDirection() != null) {
      json.put("direction", obj.getDirection().name());
    }
  }
}
