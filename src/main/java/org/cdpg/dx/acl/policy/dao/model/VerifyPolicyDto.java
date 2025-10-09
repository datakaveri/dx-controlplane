package org.cdpg.dx.acl.policy.dao.model;

import io.vertx.core.json.JsonObject;

public class VerifyPolicyDto {
  private String type;
  private JsonObject apdConstraints;

  public VerifyPolicyDto() {
  }

  public VerifyPolicyDto(String type, JsonObject apdConstraints) {
    this.type = type;
    this.apdConstraints = apdConstraints;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public JsonObject getApdConstraints() {
    return apdConstraints;
  }

  public void setApdConstraints(JsonObject apdConstraints) {
    this.apdConstraints = apdConstraints;
  }


  public JsonObject toJson() {
    return new JsonObject()
        .put("type", type)
        .put("apdConstraints", apdConstraints);
  }
}
