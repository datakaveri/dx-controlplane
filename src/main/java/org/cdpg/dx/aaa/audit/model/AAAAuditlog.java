package org.cdpg.dx.aaa.audit.model;

import io.vertx.core.json.JsonObject;
import org.cdpg.dx.auditing.model.AuditLog;

import java.util.UUID;

public class AAAAuditlog implements AuditLog {
    private final UUID id;
    private final String assetName;
    private final UUID assetId;
    private final String assetType;
    private final String operation;
    private final String createdAt;
    private final String api;
    private final String method;
    private final long size;
    private final String role;
    private final UUID userId;
    private final String originServer;
    private final boolean myActivityEnabled;
    private final String shortDescription;

    public AAAAuditlog(UUID id, String assetName, UUID assetId, String assetType, String operation, String createdAt,
                       String api, String method, long size, String role, UUID userId, String originServer,
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
        json.put("myactivity_enabled", myActivityEnabled);
        json.put("short_description", shortDescription);
        return json;
    }
}