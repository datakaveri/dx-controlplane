package org.cdpg.dx.acl.accessRequest.util;


import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import java.time.LocalDateTime;
import java.util.UUID;
import org.cdpg.dx.acl.accessRequest.dao.model.AccessRequestDto;
import org.cdpg.dx.auditing.model.AuditLog;

public class AuditingHelper {
  private AuditingHelper() {
  }

  public static AuditLog createAuditLog(AccessRequestDto dto, User user, String endpoint, String method,
                                        String operation, String organizationId, String organizationName) {
    UUID id = UUID.randomUUID();
    String assetName = dto.getAssetName();
    UUID assetId = UUID.fromString(dto.getItemId());
    String assetType = dto.getAssetType();
    String createdAt = LocalDateTime.now().toString();
    long size = 0L; // Size is not applicable for AccessRequest, set to 0
    JsonObject principal = user.principal();
    JsonObject realmAccess = principal.getJsonObject("realm_access");
    JsonArray userRoles = realmAccess.getJsonArray("roles");
    String role = userRoles.contains("consumer") ? "consumer" : "provider";
    UUID userId = UUID.fromString(principal.getString("sub"));
    String originServer = "ACL";
    boolean myActivityEnabled = true;
    String shortDescription = dto.getShortDescription();

    return new AclApdAuditLog(id, assetName, assetId, assetType, operation, createdAt,
        endpoint, method, size, role, userId, originServer,organizationId, organizationName, myActivityEnabled, shortDescription);
  }
}
