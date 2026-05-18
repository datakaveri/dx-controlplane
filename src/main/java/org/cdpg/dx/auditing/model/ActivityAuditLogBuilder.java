package org.cdpg.dx.auditing.model;

import io.vertx.core.json.JsonObject;
import org.cdpg.dx.auditing.enums.*;
import org.cdpg.dx.auth.model.DxRole;

import java.util.UUID;

import static org.cdpg.dx.auditing.schema.ActivityAuditSchema.*;

public class ActivityAuditLogBuilder {

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
  private Operation operation;
  private OriginServer originServer;
  private String issuer;

  private EntityType entityType;
  private UUID entityId;
  private String entityName;
  private String shortDescription;

  private String ipAddress;
  private String userAgent;
  private Long sizeBytes;

  private String createdAt;
  private Long epochMs;
  private String ingestedAt;

  private String status;
  private String statusMessage;

  private JsonObject details;
  private boolean myActivityEnabled;

  public ActivityAuditLogBuilder() {}

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

  public Operation getOperation() {
    return operation;
  }

  public void setOperation(Operation operation) {
    this.operation = operation;
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

  public String getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(String createdAt) {
    this.createdAt = createdAt;
  }

  public Long getEpochMs() {
    return epochMs;
  }

  public void setEpochMs(Long epochMs) {
    this.epochMs = epochMs;
  }

  public String getIngestedAt() {
    return ingestedAt;
  }

  public void setIngestedAt(String ingestedAt) {
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

  private static String safeEnum(Enum<?> e) {
    return e == null ? null : e.name();
  }

  private static String safeString(Object value) {
    return value == null ? null : value.toString();
  }

  public JsonObject toJson() {

    JsonObject json = new JsonObject();

    json.put(ID, safeString(id));
    json.put(USER_ID, safeString(userId));
    json.put(ORG_ID, safeString(orgId));
    json.put(ORG_NAME, orgName);

    json.put(ROLE, role != null ? role.value() : null);
    json.put(IS_DELEGATE, isDelegate);

    json.put(DELEGATOR_ID, safeString(delegatorId));
    json.put(DELEGATOR_ROLE, safeEnum(delegatorRole));

    json.put(PROVIDER_ID, safeString(providerId));

    json.put(API, api);
    json.put(METHOD, safeEnum(method));
    json.put(ACTION, safeEnum(operation));
    json.put(ORIGIN_SERVER, safeEnum(originServer));
    json.put(ISSUER, issuer);

    json.put(ENTITY_TYPE, safeEnum(entityType));
    json.put(ENTITY_ID, safeString(entityId));
    json.put(ENTITY_NAME, entityName);
    json.put(SHORT_DESCRIPTION, shortDescription);

    json.put(IP_ADDRESS, ipAddress);
    json.put(USER_AGENT, userAgent);
    json.put(SIZE_BYTES, sizeBytes);

    json.put(CREATED_AT, safeString(createdAt));
    json.put(EPOCH_MS, epochMs);
    json.put(INGESTED_AT, safeString(ingestedAt));

    json.put(STATUS, status);
    json.put(STATUS_MESSAGE, statusMessage);

    json.put(DETAILS, details);
    json.put(MYACTIVITY_ENABLED, myActivityEnabled);

    return json;
  }

  public static class Builder {

    private final ActivityAuditLogBuilder log = new ActivityAuditLogBuilder();

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

    public Builder withIsDelegate(boolean val) {
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

    public Builder withOperation(Operation a) {
      log.operation = a;
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

    public Builder withCreatedAt(String instant) {
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

    public ActivityAuditLogBuilder build() {
      return log;
    }
  }
}
