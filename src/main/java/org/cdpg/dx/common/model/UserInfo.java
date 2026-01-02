package org.cdpg.dx.common.model;

import io.vertx.core.json.JsonObject;
import java.util.UUID;

public record UserInfo(
  UUID sub,
  String name,
  String preferredUsername,
  String givenName,
  String familyName,
  String email
) {

  public JsonObject toJson() {
    return new JsonObject()
      .put("sub", sub != null ? sub.toString() : null)
      .put("name", name)
      .put("preferredUsername", preferredUsername)
      .put("givenName", givenName)
      .put("familyName", familyName)
      .put("email", email);
  }


}

