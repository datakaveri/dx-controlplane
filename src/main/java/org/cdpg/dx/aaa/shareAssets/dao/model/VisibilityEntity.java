package org.cdpg.dx.aaa.shareAssets.dao.model;

import static org.cdpg.dx.aaa.shareAssets.util.Constants.*;

import io.vertx.core.json.JsonObject;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.cdpg.dx.database.postgres.base.entity.BaseEntity;

public class VisibilityEntity implements BaseEntity<VisibilityEntity> {

  private UUID id;
  private UUID itemId;
  private String visibilityType;
  private UUID userId;
  private UUID orgId;
  private UUID sharedBy;
  private String status;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public VisibilityEntity() {}

  public VisibilityEntity(JsonObject json) {

    this.id = json.getString(DB_ID) != null ? UUID.fromString(json.getString(DB_ID)) : null;

    this.itemId =
        json.getString(DB_ITEM_ID) != null ? UUID.fromString(json.getString(DB_ITEM_ID)) : null;

    this.visibilityType = json.getString(DB_SHARE_TYPE);

    this.userId =
        json.getString(DB_USER_ID) != null ? UUID.fromString(json.getString(DB_USER_ID)) : null;

    this.orgId =
        json.getString(DB_ORG_ID) != null ? UUID.fromString(json.getString(DB_ORG_ID)) : null;

    this.sharedBy =
        json.getString(DB_SHARED_BY) != null ? UUID.fromString(json.getString(DB_SHARED_BY)) : null;

    this.status = json.getString(DB_STATUS);

    this.createdAt =
        json.getString(DB_CREATED_AT) != null
            ? LocalDateTime.parse(json.getString(DB_CREATED_AT))
            : null;

    this.updatedAt =
        json.getString(DB_UPDATED_AT) != null
            ? LocalDateTime.parse(json.getString(DB_UPDATED_AT))
            : null;
  }

  @Override
  public Map<String, Object> toNonEmptyFieldsMap() {

    Map<String, Object> map = new HashMap<>();

    if (id != null) {
      map.put(DB_ID, id);
    }

    if (itemId != null) {
      map.put(DB_ITEM_ID, itemId);
    }

    if (visibilityType != null) {
      map.put(DB_SHARE_TYPE, visibilityType);
    }

    if (userId != null) {
      map.put(DB_USER_ID, userId);
    }

    if (orgId != null) {
      map.put(DB_ORG_ID, orgId);
    }

    if (sharedBy != null) {
      map.put(DB_SHARED_BY, sharedBy);
    }

    map.put(DB_STATUS, status != null ? status : ACTIVE);

    if (createdAt != null) {
      map.put(DB_CREATED_AT, createdAt);
    }

    if (updatedAt != null) {
      map.put(DB_UPDATED_AT, updatedAt);
    }

    return map;
  }

  public JsonObject toJson() {

    return new JsonObject()
        .put("id", id)
        .put("itemId", itemId)
        .put("visibilityType", visibilityType)
        .put("userId", userId)
        .put("orgId", orgId)
        .put("sharedBy", sharedBy)
        .put("status", status)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt);
  }

  @Override
  public String getTableName() {
    return SHARE_TABLE;
  }

  public UUID getId() {
    return id;
  }

  public VisibilityEntity setId(UUID id) {
    this.id = id;
    return this;
  }

  public UUID getItemId() {
    return itemId;
  }

  public VisibilityEntity setItemId(UUID itemId) {
    this.itemId = itemId;
    return this;
  }

  public String getVisibilityType() {
    return visibilityType;
  }

  public VisibilityEntity setVisibilityType(String visibilityType) {
    this.visibilityType = visibilityType;
    return this;
  }

  public UUID getUserId() {
    return userId;
  }

  public VisibilityEntity setUserId(UUID userId) {
    this.userId = userId;
    return this;
  }

  public UUID getOrgId() {
    return orgId;
  }

  public VisibilityEntity setOrgId(UUID orgId) {
    this.orgId = orgId;
    return this;
  }

  public UUID getSharedBy() {
    return sharedBy;
  }

  public VisibilityEntity setSharedBy(UUID sharedBy) {
    this.sharedBy = sharedBy;
    return this;
  }

  public String getStatus() {
    return status;
  }

  public VisibilityEntity setStatus(String status) {
    this.status = status;
    return this;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public VisibilityEntity setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
    return this;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public VisibilityEntity setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
    return this;
  }
}
