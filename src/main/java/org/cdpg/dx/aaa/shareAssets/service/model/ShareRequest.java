package org.cdpg.dx.aaa.shareAssets.service.model;

import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.UUID;

public class ShareRequest {

  private UUID itemId;
  private String shareType;
  private List<UUID> ids;

  public static ShareRequest fromJson(JsonObject json) {

    List<UUID> ids =
        json.getJsonArray("ids").stream().map(Object::toString).map(UUID::fromString).toList();

    ShareRequest request = new ShareRequest();

    request.itemId = UUID.fromString(json.getString("itemId"));
    request.shareType = json.getString("shareType");
    request.ids = ids;

    return request;
  }

  public UUID getItemId() {
    return itemId;
  }

  public String getShareType() {
    return shareType;
  }

  public List<UUID> getIds() {
    return ids;
  }
}
