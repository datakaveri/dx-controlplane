package org.cdpg.dx.aaa.delegation;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.delegation.models.DelegationGrant;
import org.cdpg.dx.aaa.delegation.models.DelegationScopeConstraint;

import java.util.List;

public record UpdatedGrantResponse(JsonObject grant, List<JsonObject> scopeConstraints) {

  public JsonObject toJson() {
//    JsonObject json = grant.toJson();
    JsonArray constraintArray = new JsonArray();
    if (scopeConstraints != null) {
      scopeConstraints.forEach(c -> constraintArray.add(c));
    }
    grant.put("scope_constraints", constraintArray);
    return grant;
  }
}
