package org.cdpg.dx.auditing.model;

import io.vertx.core.json.JsonObject;
import org.cdpg.dx.auditing.enums.*;
import org.cdpg.dx.auth.authorization.model.DxRole;

import java.time.Instant;
import java.util.UUID;

public class ActivityAuditLog {

  private UUID id;

  private UUID userId;
  private UUID orgId;
  private String orgName;

  private DxRole role;
  private boolean isDelegate;

  private UUID delegatorId;
  private DxRole delegatorRole;

  private UUID providerId;

  private String api;
  private HttpMethod method;
  private ActionType action;
  private OriginServer originServer;
  private String issuer;

  private EntityType entityType;
  private UUID entityId;
  private String entityName;
  private String shortDescription;

  private String ipAddress;
  private String userAgent;
  private Long sizeBytes;

  private Instant createdAt;
  private Long epochMs;
  private Instant ingestedAt;

  private String status;
  private String statusMessage;

  private JsonObject details;
  private boolean myActivityEnabled;

  public ActivityAuditLog() {}

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public UUID getOrgId() {
    return orgId;
  }

  public void setOrgId(UUID orgId) {
    this.orgId = orgId;
  }

  public String getOrgName() {
    return orgName;
  }

  public void setOrgName(String orgName) {
    this.orgName = orgName;
  }

  public DxRole getRole() {
    return role;
  }

  public void setRole(DxRole role) {
    this.role = role;
  }

  public boolean isDelegate() {
    return isDelegate;
  }

  public void setDelegate(boolean delegate) {
    isDelegate = delegate;
  }

  public UUID getDelegatorId() {
    return delegatorId;
  }

  public void setDelegatorId(UUID delegatorId) {
    this.delegatorId = delegatorId;
  }

  public DxRole getDelegatorRole() {
    return delegatorRole;
  }

  public void setDelegatorRole(DxRole delegatorRole) {
    this.delegatorRole = delegatorRole;
  }

  public UUID getProviderId() {
    return providerId;
  }

  public void setProviderId(UUID providerId) {
    this.providerId = providerId;
  }

  public String getApi() {
    return api;
  }

  public void setApi(String api) {
    this.api = api;
  }

  public HttpMethod getMethod() {
    return method;
  }

  public void setMethod(HttpMethod method) {
    this.method = method;
  }

  public ActionType getAction() {
    return action;
  }

  public void setAction(ActionType action) {
    this.action = action;
  }

  public OriginServer getOriginServer() {
    return originServer;
  }

  public void setOriginServer(OriginServer originServer) {
    this.originServer = originServer;
  }

  public String getIssuer() {
    return issuer;
  }

  public void setIssuer(String issuer) {
    this.issuer = issuer;
  }

  public EntityType getEntityType() {
    return entityType;
  }

  public void setEntityType(EntityType entityType) {
    this.entityType = entityType;
  }

  public UUID getEntityId() {
    return entityId;
  }

  public void setEntityId(UUID entityId) {
    this.entityId = entityId;
  }

  public String getEntityName() {
    return entityName;
  }

  public void setEntityName(String entityName) {
    this.entityName = entityName;
  }

  public String getShortDescription() {
    return shortDescription;
  }

  public void setShortDescription(String shortDescription) {
    this.shortDescription = shortDescription;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public void setIpAddress(String ipAddress) {
    this.ipAddress = ipAddress;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public void setUserAgent(String userAgent) {
    this.userAgent = userAgent;
  }

  public Long getSizeBytes() {
    return sizeBytes;
  }

  public void setSizeBytes(Long sizeBytes) {
    this.sizeBytes = sizeBytes;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Long getEpochMs() {
    return epochMs;
  }

  public void setEpochMs(Long epochMs) {
    this.epochMs = epochMs;
  }

  public Instant getIngestedAt() {
    return ingestedAt;
  }

  public void setIngestedAt(Instant ingestedAt) {
    this.ingestedAt = ingestedAt;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getStatusMessage() {
    return statusMessage;
  }

  public void setStatusMessage(String statusMessage) {
    this.statusMessage = statusMessage;
  }

  public JsonObject getDetails() {
    return details;
  }

  public void setDetails(JsonObject details) {
    this.details = details;
  }

  public boolean isMyActivityEnabled() {
    return myActivityEnabled;
  }

  public void setMyActivityEnabled(boolean myActivityEnabled) {
    this.myActivityEnabled = myActivityEnabled;
  }

  // --------------------------------------------------
  // SAFE HELPERS (NO PMD/Sonar warnings)
  // --------------------------------------------------

  private static String safeEnum(Enum<?> e) {
    return e == null ? null : e.name();
  }

  private static String safeString(Object value) {
    return value == null ? null : value.toString();
  }

  public JsonObject toJson() {

    JsonObject json = new JsonObject();

    json.put("id", safeString(id));
    json.put("user_id", safeString(userId));
    json.put("org_id", safeString(orgId));
    json.put("org_name", orgName);

    json.put("role", safeEnum(role));
    json.put("is_delegate", isDelegate);

    json.put("delegator_id", safeString(delegatorId));
    json.put("delegator_role", safeEnum(delegatorRole));

    json.put("provider_id", safeString(providerId));

    json.put("api", api);
    json.put("method", safeEnum(method));
    json.put("action", safeEnum(action));
    json.put("origin_server", safeEnum(originServer));
    json.put("issuer", issuer);

    json.put("entity_type", safeEnum(entityType));
    json.put("entity_id", safeString(entityId));
    json.put("entity_name", entityName);
    json.put("short_description", shortDescription);

    json.put("ip_address", ipAddress);
    json.put("user_agent", userAgent);
    json.put("size_bytes", sizeBytes);

    json.put("created_at", safeString(createdAt));
    json.put("epoch_ms", epochMs);
    json.put("ingested_at", safeString(ingestedAt));

    json.put("status", status);
    json.put("status_message", statusMessage);

    json.put("details", details);

    json.put("myactivity_enabled", myActivityEnabled);

    return json;
  }

  public static class Builder {

    private final ActivityAuditLog log = new ActivityAuditLog();

    public Builder withId(UUID id) {
      log.id = id;
      return this;
    }

    public Builder withUserId(UUID id) {
      log.userId = id;
      return this;
    }

    public Builder withOrgId(UUID id) {
      log.orgId = id;
      return this;
    }

    public Builder withOrgName(String name) {
      log.orgName = name;
      return this;
    }

    public Builder withRole(DxRole role) {
      log.role = role;
      return this;
    }

    public Builder withDelegate(boolean val) {
      log.isDelegate = val;
      return this;
    }

    public Builder withDelegatorId(UUID id) {
      log.delegatorId = id;
      return this;
    }

    public Builder withDelegatorRole(DxRole role) {
      log.delegatorRole = role;
      return this;
    }

    public Builder withProviderId(UUID id) {
      log.providerId = id;
      return this;
    }

    public Builder withApi(String api) {
      log.api = api;
      return this;
    }

    public Builder withMethod(HttpMethod m) {
      log.method = m;
      return this;
    }

    public Builder withAction(ActionType a) {
      log.action = a;
      return this;
    }

    public Builder withOrigin(OriginServer o) {
      log.originServer = o;
      return this;
    }

    public Builder withIssuer(String issuer) {
      log.issuer = issuer;
      return this;
    }

    public Builder withEntityType(EntityType e) {
      log.entityType = e;
      return this;
    }

    public Builder withEntityId(UUID id) {
      log.entityId = id;
      return this;
    }

    public Builder withEntityName(String name) {
      log.entityName = name;
      return this;
    }

    public Builder withShortDescription(String desc) {
      log.shortDescription = desc;
      return this;
    }

    public Builder withIp(String ip) {
      log.ipAddress = ip;
      return this;
    }

    public Builder withUserAgent(String ua) {
      log.userAgent = ua;
      return this;
    }

    public Builder withSizeBytes(Long size) {
      log.sizeBytes = size;
      return this;
    }

    public Builder withCreatedAt(Instant instant) {
      log.createdAt = instant;
      return this;
    }

    public Builder withEpochMs(Long ms) {
      log.epochMs = ms;
      return this;
    }

    public Builder withStatus(String status) {
      log.status = status;
      return this;
    }

    public Builder withStatusMessage(String msg) {
      log.statusMessage = msg;
      return this;
    }

    public Builder withDetails(JsonObject json) {
      log.details = json;
      return this;
    }

    public Builder withMyActivityEnabled(boolean flag) {
      log.myActivityEnabled = flag;
      return this;
    }

    public ActivityAuditLog build() {
      return log;
    }
  }
}
