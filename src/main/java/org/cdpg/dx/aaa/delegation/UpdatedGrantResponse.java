package org.cdpg.dx.aaa.delegation;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.delegation.models.DelegationGrant;
import org.cdpg.dx.aaa.delegation.models.DelegationScopeConstraint;

import java.util.List;

public record UpdatedGrantResponse(DelegationGrant grant, List<DelegationScopeConstraint> scopeConstraints) {

  public JsonObject toJson() {
    JsonObject json = grant.toJson();
    JsonArray constraintArray = new JsonArray();
    if (scopeConstraints != null) {
      scopeConstraints.forEach(c -> constraintArray.add(c.toJson()));
    }
    json.put("scope_constraints", constraintArray);
    return json;
  }
}
