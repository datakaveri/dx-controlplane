package org.cdpg.dx.acl.policy.dao.model;

import io.vertx.core.json.JsonObject;
import java.time.LocalDateTime;

public class PolicyDto {

  private String policyId;
  private String itemId;
  private String itemType;
  private LocalDateTime expiryAt;
  private JsonObject constraints;
  private String status;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private JsonObject consumer;
  private JsonObject provider;

  public PolicyDto() {}

  public PolicyDto(JsonObject row) {
    this.policyId = row.getString("policyId");
    this.itemId = row.getString("itemId");
    this.itemType = row.getString("itemType");
    this.status = row.getString("status");

    if (row.containsKey("expiryAt") && row.getString("expiryAt") != null) {
      this.expiryAt = LocalDateTime.parse(row.getString("expiryAt"));
    }

    if (row.containsKey("createdAt") && row.getString("createdAt") != null) {
      this.createdAt = LocalDateTime.parse(row.getString("createdAt"));
    }

    if (row.containsKey("updatedAt") && row.getString("updatedAt") != null) {
      this.updatedAt = LocalDateTime.parse(row.getString("updatedAt"));
    }

    this.constraints = row.getJsonObject("constraints");

    if (row.containsKey("consumer")) {
      this.consumer = row.getJsonObject("consumer");
    }

    if (row.containsKey("provider")) {
      this.provider = row.getJsonObject("provider");
    }
  }

  public JsonObject toJson() {
    return new JsonObject()
        .put("policyId", policyId)
        .put("itemId", itemId)
        .put("itemType", itemType)
        .put("expiryAt", expiryAt != null ? expiryAt.toString() : null)
        .put("constraints", constraints)
        .put("status", status)
        .put("createdAt", createdAt != null ? createdAt.toString() : null)
        .put("updatedAt", updatedAt != null ? updatedAt.toString() : null)
        .put("consumer", consumer)
        .put("provider", provider);
  }

  // Getters and setters
  public String getPolicyId() { return policyId; }
  public PolicyDto setPolicyId(String policyId) { this.policyId = policyId; return this; }

  public String getItemId() { return itemId; }
  public PolicyDto setItemId(String itemId) { this.itemId = itemId; return this; }

  public String getItemType() { return itemType; }
  public PolicyDto setItemType(String itemType) { this.itemType = itemType; return this; }

  public LocalDateTime getExpiryAt() { return expiryAt; }
  public PolicyDto setExpiryAt(LocalDateTime expiryAt) { this.expiryAt = expiryAt; return this; }

  public JsonObject getConstraints() { return constraints; }
  public PolicyDto setConstraints(JsonObject constraints) { this.constraints = constraints; return this; }

  public String getStatus() { return status; }
  public PolicyDto setStatus(String status) { this.status = status; return this; }

  public LocalDateTime getCreatedAt() { return createdAt; }
  public PolicyDto setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }

  public LocalDateTime getUpdatedAt() { return updatedAt; }
  public PolicyDto setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }

  public JsonObject getConsumer() { return consumer; }
  public PolicyDto setConsumer(JsonObject consumer) { this.consumer = consumer; return this; }

  public JsonObject getProvider() { return provider; }
  public PolicyDto setProvider(JsonObject provider) { this.provider = provider; return this; }

}
