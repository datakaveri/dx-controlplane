package org.cdpg.dx.catalogueService.models;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.impl.JsonUtil;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Converter and mapper for {@link org.cdpg.dx.catalogueService.models.Asset}.
 * NOTE: This class has been automatically generated from the {@link org.cdpg.dx.catalogueService.models.Asset} original class using Vert.x codegen.
 */
public class AssetConverter {


  private static final Base64.Decoder BASE64_DECODER = JsonUtil.BASE64_DECODER;
  private static final Base64.Encoder BASE64_ENCODER = JsonUtil.BASE64_ENCODER;

  public static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, Asset obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "assetName":
          if (member.getValue() instanceof String) {
            obj.setAssetName((String)member.getValue());
          }
          break;
        case "assetType":
          if (member.getValue() instanceof String) {
            obj.setAssetType((String)member.getValue());
          }
          break;
        case "itemId":
          if (member.getValue() instanceof String) {
            obj.setItemId((String)member.getValue());
          }
          break;
        case "organizationId":
          if (member.getValue() instanceof String) {
            obj.setOrganizationId((String)member.getValue());
          }
          break;
        case "providerId":
          if (member.getValue() instanceof String) {
            obj.setProviderId((String)member.getValue());
          }
          break;
        case "shortDescription":
          if (member.getValue() instanceof String) {
            obj.setShortDescription((String)member.getValue());
          }
          break;
      }
    }
  }

  public static void toJson(Asset obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

  public static void toJson(Asset obj, java.util.Map<String, Object> json) {
    if (obj.getAssetName() != null) {
      json.put("assetName", obj.getAssetName());
    }
    if (obj.getAssetType() != null) {
      json.put("assetType", obj.getAssetType());
    }
    if (obj.getItemId() != null) {
      json.put("itemId", obj.getItemId());
    }
    if (obj.getOrganizationId() != null) {
      json.put("organizationId", obj.getOrganizationId());
    }
    if (obj.getProviderId() != null) {
      json.put("providerId", obj.getProviderId());
    }
    if (obj.getShortDescription() != null) {
      json.put("shortDescription", obj.getShortDescription());
    }
  }
}
