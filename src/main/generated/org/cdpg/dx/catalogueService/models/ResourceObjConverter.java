package org.cdpg.dx.catalogueService.models;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.impl.JsonUtil;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Converter and mapper for {@link org.cdpg.dx.catalogueService.models.ResourceObj}.
 * NOTE: This class has been automatically generated from the {@link org.cdpg.dx.catalogueService.models.ResourceObj} original class using Vert.x codegen.
 */
public class ResourceObjConverter {


  private static final Base64.Decoder BASE64_DECODER = JsonUtil.BASE64_DECODER;
  private static final Base64.Encoder BASE64_ENCODER = JsonUtil.BASE64_ENCODER;

  public static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, ResourceObj obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "groupLevelResource":
          if (member.getValue() instanceof Boolean) {
            obj.setGroupLevelResource((Boolean)member.getValue());
          }
          break;
        case "isGroupLevelResource":
          break;
        case "itemType":
          if (member.getValue() instanceof String) {
            obj.setItemType(org.cdpg.dx.catalogueService.models.ItemType.valueOf((String)member.getValue()));
          }
          break;
        case "resourceServerUrl":
          if (member.getValue() instanceof String) {
            obj.setResourceServerUrl((String)member.getValue());
          }
          break;
      }
    }
  }

  public static void toJson(ResourceObj obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

  public static void toJson(ResourceObj obj, java.util.Map<String, Object> json) {
    json.put("isGroupLevelResource", obj.getIsGroupLevelResource());
    if (obj.getItemType() != null) {
      json.put("itemType", obj.getItemType().name());
    }
    if (obj.getResourceServerUrl() != null) {
      json.put("resourceServerUrl", obj.getResourceServerUrl());
    }
  }
}
