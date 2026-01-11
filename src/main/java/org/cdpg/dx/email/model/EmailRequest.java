package org.cdpg.dx.email.model;

import static org.cdpg.dx.email.util.Constants.*;

import io.vertx.core.json.JsonObject;
import org.cdpg.dx.email.util.TemplateRef;
import org.cdpg.dx.email.util.TemplateResolver;

public record EmailRequest(
    String consumerUserId,
    TemplateResolver templateResolver,
    String providerUserId,
    String assetType,
    String itemId,
    String shortDescription,
    boolean isCreated,
    String status,
    String assetName) {
  public static EmailRequest fromJson(JsonObject json) {
    if (json == null) {
      throw new IllegalArgumentException("EmailRequest json cannot be null");
    }
    if (!json.containsKey(CONSUMER_USER_ID)
        || !json.containsKey(TEMPLATE_TYPE)
        || !json.containsKey(TEMPLATE_STRUCTURE)
        || !json.containsKey("isCreated")) {
      throw new IllegalArgumentException("Missing required fields in EmailRequest json");
    }
    String consumerUserId = json.getString(CONSUMER_USER_ID);
    String providerUserId = json.getString(PROVIDER_USER_ID, null);
    String assetType = json.getString(ASSET_TYPE, null);
    String itemId = json.getString(ITEM_ID, null);
    String shortDescription = json.getString(SHORT_DESCRIPTION, null);
    boolean isCreated = json.getBoolean("isCreated", false);
    String status = json.getString(STATUS, null);
    String assetName = json.getString(ASSET_NAME, null);

    // Template resolution
    TemplateRef.Type type = TemplateRef.Type.valueOf(json.getString(TEMPLATE_TYPE));
    String value = json.getString(TEMPLATE_STRUCTURE);

    TemplateResolver resolver = new TemplateResolver(new TemplateRef(type, value));

    return new EmailRequest(
        consumerUserId,
        resolver,
        providerUserId,
        assetType,
        itemId,
        shortDescription,
        isCreated,
        status,
        assetName);
  }

  @Override
  public String toString() {
    return "EmailRequest{"
        + "consumerUserId='"
        + consumerUserId
        + '\''
        + ", templateResolver="
        + templateResolver
        + ", providerUserId='"
        + providerUserId
        + '\''
        + ", assetType='"
        + assetType
        + '\''
        + ", itemId='"
        + itemId
        + '\''
        + ", shortDescription='"
        + shortDescription
        + '\''
        + ", isCreated="
        + isCreated
        + ", status='"
        + status
        + '\''
        + ", assetName='"
        + assetName
        + '\''
        + '}';
  }
}
