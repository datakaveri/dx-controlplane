package org.cdpg.dx.aaa.accessRequest.util;

import io.vertx.core.json.JsonObject;
import java.util.UUID;
import org.cdpg.dx.auditing.model.AuditLog;

public class AclApdAuditLog implements AuditLog {
  private final UUID id;
  private final String assetName;
  private final UUID assetId;
  private final String assetType;
  private final String operation;
  private final String createdAt;
  private final String api;
  private final String method;
  private final Long size;
  private final String role;
  private final UUID userId;
  private final String originServer;
  private final boolean myActivityEnabled;
  private final String shortDescription;
  private final String organizationId; // Optional field  in auditing server [organization id of consumer, provider]. Can be null sometimes
  private final String organizationName; // Optional field in auditing server [organization name of consumer, provider]. Can be null sometimes

  //change this to AuditingHandler and it can be a helper
  public AclApdAuditLog(UUID id, String assetName, UUID assetId, String assetType, String operation, String createdAt,
                        String api, String method, Long size, String role, UUID userId, String originServer,String organizationId, String organizationName,
                        boolean myActivityEnabled, String shortDescription) {
    this.id = id;
    this.assetName = assetName;
    this.assetId = assetId;
    this.assetType = assetType;
    this.operation = operation;
    this.createdAt = createdAt;
    this.api = api;
    this.method = method;
    this.size = size;
    this.role = role;
    this.userId = userId;
    this.originServer = originServer;
    this.organizationId = organizationId;
    this.organizationName = organizationName;
    this.myActivityEnabled = myActivityEnabled;
    this.shortDescription = shortDescription;
  }

  @Override
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    json.put("id", id.toString());
    json.put("asset_name", assetName);
    json.put("asset_id", assetId.toString());
    json.put("asset_type", assetType);
    json.put("operation", operation);
    json.put("created_at", createdAt);
    json.put("api", api);
    json.put("method", method);
    json.put("size", size);
    json.put("role", role);
    json.put("user_id", userId.toString());
    json.put("origin_server", originServer);
    json.put("org_id", organizationId);
    json.put("org_name", organizationName);
    json.put("myactivity_enabled", myActivityEnabled);
    json.put("short_description", shortDescription);
    return json;
  }

}