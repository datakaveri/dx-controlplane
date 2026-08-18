package org.cdpg.dx.aaa.delegation;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.List;

public record UpdatedGrantResponse(
    JsonObject grant,
    JsonObject delegator,
    JsonObject delegate,
    List<JsonObject> scopeConstraints) {

  public JsonObject toJson() {
    JsonArray constraintArray = new JsonArray();

    if (scopeConstraints != null) {
      scopeConstraints.forEach(
          constraint -> {
            JsonObject cleanedConstraint = constraint.copy();
            cleanedConstraint.remove("id");
            cleanedConstraint.remove("delegationId");
            constraintArray.add(cleanedConstraint);
          });
    }

    grant.put("scopeConstraints", constraintArray);
    grant.put("delegator", delegator);
    grant.put("delegate", delegate);

    return grant;
  }
}
