package org.cdpg.dx.acl.policy.dao.model;

import io.vertx.core.json.JsonObject;

public class VerifyPolicyDto {
  private String policyId;
  private String type;
  private JsonObject constraints;
  private String expiryAt;

  public VerifyPolicyDto() {}

  public VerifyPolicyDto(JsonObject json) {
    if (json == null) {
      return;
    }
    this.policyId = json.getString("policyId");
    this.type = json.getString("type");
    this.constraints = json.getJsonObject("constraints");
    this.expiryAt = json.getString("expiryAt");
  }

  public VerifyPolicyDto(String policyId, String type, JsonObject constraints, String expiryAt) {
    this.policyId = policyId;
    this.type = type;
    this.constraints = constraints;
    this.expiryAt = expiryAt;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public JsonObject getConstraints() {
    return constraints;
  }

  public void setConstraints(JsonObject constraints) {
    this.constraints = constraints;
  }

  public String getExpiryAt() {
    return expiryAt;
  }

  public void setExpiryAt(String expiryAt) {
    this.expiryAt = expiryAt;
  }

  public String getPolicyId() {
    return policyId;
  }

  public void setPolicyId(String policyId) {
    this.policyId = policyId;
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();

    if (policyId != null) {
      json.put("policyId", policyId);
    }
    if (type != null) {
      json.put("type", type);
    }
    if (constraints != null) {
      json.put("constraints", constraints);
    }
    if (expiryAt != null) {
      json.put("expiryAt", expiryAt);
    }

    return json;
  }
}
