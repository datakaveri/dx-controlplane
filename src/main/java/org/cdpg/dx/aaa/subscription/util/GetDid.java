package org.cdpg.dx.aaa.subscription.util;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.Optional;

public class GetDid {
  public static Optional<String> getDid(JsonObject principal, String sub) {

    JsonObject realmAccess = principal.getJsonObject("realm_access");
    if (realmAccess == null) {
      return Optional.empty();
    }

    JsonArray roles = realmAccess.getJsonArray("roles", new JsonArray());
    // Case 1: Delegate
    if (roles.contains("delegate")) {

      JsonArray delegationScope = principal.getJsonArray("delegation_scope", new JsonArray());

      // adjust this condition if your scope naming differs
      if (!delegationScope.isEmpty()) {
        if (principal.getString("did") != null) {
          return Optional.ofNullable(principal.getString("did"));
        } else {
          return Optional.ofNullable(sub);
        }
      }
    }
    // Case 2: Direct consumer
    if (roles.contains("consumer")) {
      return Optional.ofNullable(sub);
    }

    return Optional.empty();
  }
}
