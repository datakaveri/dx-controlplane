package org.cdpg.dx.acl.accessRequest.dao.model;

import io.vertx.core.json.JsonObject;
import java.time.LocalDateTime;
import java.util.UUID;

public class PolicyAccessInfo {

  private UUID policyId;
  private JsonObject constraints;
  private LocalDateTime expiryAt;

  public PolicyAccessInfo() {}

  public PolicyAccessInfo(
      UUID policyId,
      JsonObject constraints,
      LocalDateTime expiryAt) {
    this.policyId = policyId;
    this.constraints = constraints;
    this.expiryAt = expiryAt;
  }

  public UUID getPolicyId() {
    return policyId;
  }

  public void setPolicyId(UUID policyId) {
    this.policyId = policyId;
  }

  public JsonObject getConstraints() {
    return constraints;
  }

  public void setConstraints(JsonObject constraints) {
    this.constraints = constraints;
  }

  public LocalDateTime getExpiryAt() {
    return expiryAt;
  }

  public void setExpiryAt(LocalDateTime expiryAt) {
    this.expiryAt = expiryAt;
  }

  public JsonObject toJson() {
    return JsonObject.mapFrom(this);
  }
}
