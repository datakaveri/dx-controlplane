package org.cdpg.dx.common.email;

import static org.cdpg.dx.email.util.Constants.*;

import io.vertx.core.json.JsonObject;
import org.cdpg.dx.acl.accessRequest.util.EmailType;

public record SendEmail(
    String consumerUserId,
    String templateType,
    String templateStructure,
    String providerUserId,
    String assetType,
    String itemId,
    String shortDescription,
    boolean isCreated,
    String status,
    String assetName,
    EmailType emailType) {
  private static void putIfPresent(JsonObject json, String key, String value) {
    if (value != null && !value.isBlank()) {
      json.put(key, value);
    }
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    putIfPresent(json, CONSUMER_USER_ID, consumerUserId);
    putIfPresent(json, TEMPLATE_TYPE, templateType);
    putIfPresent(json, TEMPLATE_STRUCTURE, templateStructure);
    putIfPresent(json, PROVIDER_USER_ID, providerUserId);
    putIfPresent(json, ASSET_TYPE, assetType);
    putIfPresent(json, ITEM_ID, itemId);
    putIfPresent(json, SHORT_DESCRIPTION, shortDescription);
    putIfPresent(json, STATUS, status);
    putIfPresent(json, ASSET_NAME, assetName);
    // boolean is primitive → always present
    json.put("isCreated", isCreated);
    putIfPresent(json, "emailType", emailType != null ? emailType.name() : null);
    return json;
  }
}
