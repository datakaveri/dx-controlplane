package org.cdpg.dx.acl.accessRequest.dao.model;

import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_ADDITIONAL_INFO;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_CREATED_AT;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_ITEM_ID;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_REQUEST_ID;

import io.vertx.core.json.JsonObject;
import java.time.LocalDateTime;
import java.util.UUID;

public class PendingRequestInfo {

  private UUID requestId;

  private UUID itemId;

  private LocalDateTime createdAt;

  private JsonObject additionalInfo;

  public static PendingRequestInfo fromJson(JsonObject row) {

    PendingRequestInfo info = new PendingRequestInfo();

    if (row.getString(DB_REQUEST_ID) != null) {
      info.setRequestId(UUID.fromString(row.getString(DB_REQUEST_ID)));
    }

    if (row.getString(DB_ITEM_ID) != null) {
      info.setItemId(UUID.fromString(row.getString(DB_ITEM_ID)));
    }

    if (row.getString(DB_CREATED_AT) != null) {
      info.setCreatedAt(LocalDateTime.parse(row.getString(DB_CREATED_AT)));
    }

    info.setAdditionalInfo(
        row.getJsonObject(DB_ADDITIONAL_INFO, new JsonObject()));

    return info;
  }

  public UUID getRequestId() {
    return requestId;
  }

  public void setRequestId(UUID requestId) {
    this.requestId = requestId;
  }

  public UUID getItemId() {
    return itemId;
  }

  public void setItemId(UUID itemId) {
    this.itemId = itemId;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public JsonObject getAdditionalInfo() {
    return additionalInfo;
  }

  public void setAdditionalInfo(JsonObject additionalInfo) {
    this.additionalInfo = additionalInfo;
  }
}
