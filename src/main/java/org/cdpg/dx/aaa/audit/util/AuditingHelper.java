package org.cdpg.dx.aaa.audit.util;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.cdpg.dx.aaa.audit.model.AAAAuditlog;
import org.cdpg.dx.auditing.model.AuditLog;

public class AuditingHelper {
  private AuditingHelper() {
  }

  public static AuditLog createAuditLog(User user, String endpoint, String method, String operation) {
    UUID id = UUID.randomUUID();
    String assetName = "AAA";
    UUID assetId = UUID.randomUUID();
    String assetTypeValue = "AAA";
    String assetType = "AAA";
//    String operation = dto.getRequestType().getRequest();
    String createdAt = LocalDateTime.now().toString();
    long size = 0L; // Size is not applicable for AccessRequest, set to 0
    JsonObject principal = user.principal();
    JsonObject realmAccess = principal.getJsonObject("realm_access");
    JsonArray userRoles = realmAccess.getJsonArray("roles");
    String role = userRoles.contains("consumer") ? "consumer" : "provider";
    UUID userId = UUID.fromString(principal.getString("sub"));
    String originServer = "AAA";
    boolean myActivityEnabled = false;
    String shortDescription = operation;

    return new AAAAuditlog(id, assetName, assetId, assetType, operation, createdAt,
            endpoint, method, size, role, userId, originServer, myActivityEnabled, shortDescription);
  }
}
