package org.cdpg.dx.auditing.v2.model;

import io.vertx.core.json.JsonObject;
import java.util.HashMap;
import java.util.Map;
import org.cdpg.dx.database.postgres.base.entity.BaseEntity;

/**
 * One row of gateway_access_log — a security/traffic event captured at the API
 * gateway (dx-gateway-go) for denied or failed requests (401/403/404/5xx).
 *
 * <p>Wire format produced by dx-common-go auditing.GatewayEvent; field names
 * here must match its JSON tags.
 */
public class GatewayAccessLogEntity implements BaseEntity<GatewayAccessLogEntity> {

  private String requestId;
  private String event;
  private String userId;
  private String method;
  private String api;
  private Integer status;
  private String authPath;
  private String fgaRelation;
  private String fgaResource;
  private String upstream;
  private String ipAddress;
  private String userAgent;
  private JsonObject detail;
  private String createdAt;

  public static GatewayAccessLogEntity fromJson(JsonObject json) {
    GatewayAccessLogEntity e = new GatewayAccessLogEntity();
    e.requestId = json.getString("request_id");
    e.event = json.getString("event");
    e.userId = json.getString("user_id");
    e.method = json.getString("method");
    e.api = json.getString("api");
    e.status = json.getInteger("status");
    e.authPath = json.getString("auth_path");
    e.fgaRelation = json.getString("fga_relation");
    e.fgaResource = json.getString("fga_resource");
    e.upstream = json.getString("upstream");
    e.ipAddress = json.getString("ip_address");
    e.userAgent = json.getString("user_agent");
    e.detail = json.getJsonObject("detail");
    e.createdAt = json.getString("created_at");
    if (e.event == null || e.event.isBlank()) {
      throw new IllegalArgumentException("gateway log event is required");
    }
    return e;
  }

  @Override
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    json.put("request_id", requestId);
    json.put("event", event);
    json.put("user_id", userId);
    json.put("method", method);
    json.put("api", api);
    json.put("status", status);
    json.put("auth_path", authPath);
    json.put("fga_relation", fgaRelation);
    json.put("fga_resource", fgaResource);
    json.put("upstream", upstream);
    json.put("ip_address", ipAddress);
    json.put("user_agent", userAgent);
    json.put("detail", detail);
    json.put("created_at", createdAt);
    return json;
  }

  @Override
  public Map<String, Object> toNonEmptyFieldsMap() {
    Map<String, Object> map = new HashMap<>();
    putIfPresent(map, "request_id", requestId);
    putIfPresent(map, "event", event);
    putIfPresent(map, "user_id", userId);
    putIfPresent(map, "method", method);
    putIfPresent(map, "api", api);
    if (status != null) {
      map.put("status", status);
    }
    putIfPresent(map, "auth_path", authPath);
    putIfPresent(map, "fga_relation", fgaRelation);
    putIfPresent(map, "fga_resource", fgaResource);
    putIfPresent(map, "upstream", upstream);
    putIfPresent(map, "ip_address", ipAddress);
    putIfPresent(map, "user_agent", userAgent);
    if (detail != null && !detail.isEmpty()) {
      map.put("detail", detail);
    }
    putIfPresent(map, "created_at", createdAt);
    return map;
  }

  private static void putIfPresent(Map<String, Object> map, String key, String value) {
    if (value != null && !value.isBlank()) {
      map.put(key, value);
    }
  }

  @Override
  public String getTableName() {
    return "gateway_access_log";
  }

  public String getEvent() {
    return event;
  }
}
