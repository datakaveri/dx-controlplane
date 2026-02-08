package org.cdpg.dx.auditing.v2.model;

import io.vertx.core.json.JsonObject;

import java.math.BigDecimal;
import java.util.UUID;
import static org.cdpg.dx.auditing.v2.Constant.UserActivityAuditSchema.*;

public class UserActivityAuditLogBuilder {

  private UUID id;

  // User context
  private UUID userId;
  private String userName;
  private UUID orgId;
  private String orgName;
  private String orgType;
  private UUID appId;
  private String role;
  private String issuer;

  // Delegation
  private UUID delegatorId;
  private String delegatorRole;

  // API metadata
  private String api;
  private String httpMethod;
  private String action;
  private String originServer;

  // Asset context
  private UUID assetId;
  private String assetName;
  private String assetShortDescription;
  private String assetType;
  private String assetAccessPolicy;
  private UUID assetOrgId;
  private String assetOrgName;
  private String assetOrgType;
  private UUID assetProviderId;
  private String assetProviderName;

  // Workflow / metrics
  private Long sizeBytes;
  private BigDecimal amount;
  private UUID requestId;

  // sandbox
  private String sandboxType;

  // Classification
  private String logType;

  // Technical metadata
  private String ipAddress;
  private String userAgent;

  // Time
  private String createdAt;

  // Extensible
  private JsonObject context;

  public UserActivityAuditLogBuilder() {}

  /* -------------------- helpers -------------------- */

  private static String safe(Object value) {
    return value == null ? null : value.toString();
  }

  /* -------------------- toJson -------------------- */

  public JsonObject toJson() {

    JsonObject json = new JsonObject();

    json.put(ID, safe(id));

    json.put(USER_ID, safe(userId));
    json.put(USER_NAME, userName);

    json.put(ORG_ID, safe(orgId));
    json.put(ORG_NAME, orgName);
    json.put(ORG_TYPE, orgType);

    json.put(APP_ID, safe(appId));
    json.put(ROLE, role);
    json.put(ISSUER, issuer);

    json.put(DELEGATOR_ID, safe(delegatorId));
    json.put(DELEGATOR_ROLE, delegatorRole);

    json.put(API, api);
    json.put(HTTP_METHOD, httpMethod);
    json.put(ACTION, action);
    json.put(ORIGIN_SERVER, originServer);
    json.put(SANDBOX_TYPE, sandboxType);

    json.put(ASSET_ID, safe(assetId));
    json.put(ASSET_NAME, assetName);
    json.put(ASSET_SHORT_DESCRIPTION, assetShortDescription);
    json.put(ASSET_TYPE, assetType);
    json.put(ASSET_ACCESS_POLICY, assetAccessPolicy);

    json.put(ASSET_ORG_ID, safe(assetOrgId));
    json.put(ASSET_ORG_NAME, assetOrgName);
    json.put(ASSET_ORG_TYPE, assetOrgType);

    json.put(ASSET_PROVIDER_ID, safe(assetProviderId));
    json.put(ASSET_PROVIDER_NAME, assetProviderName);

    json.put(SIZE_BYTES, sizeBytes);
    json.put(AMOUNT, amount);
    json.put(REQUEST_ID, safe(requestId));

    json.put(LOG_TYPE, logType);

    json.put(IP_ADDRESS, ipAddress);
    json.put(USER_AGENT, userAgent);

    json.put(CREATED_AT, createdAt);
    json.put(CONTEXT, context);

    return json;
  }

  /* -------------------- Builder -------------------- */

  public static class Builder {

    private final UserActivityAuditLogBuilder log = new UserActivityAuditLogBuilder();

    public Builder withId(UUID id) {
      log.id = id;
      return this;
    }

    public Builder withUserId(UUID id) {
      log.userId = id;
      return this;
    }

    public Builder withUserName(String name) {
      log.userName = name;
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

    public Builder withOrgType(String type) {
      log.orgType = type;
      return this;
    }

    public Builder withAppId(UUID id) {
      log.appId = id;
      return this;
    }

    public Builder withRole(String role) {
      log.role = role;
      return this;
    }

    public Builder withIssuer(String issuer) {
      log.issuer = issuer;
      return this;
    }

    public Builder withDelegatorId(UUID id) {
      log.delegatorId = id;
      return this;
    }

    public Builder withDelegatorRole(String role) {
      log.delegatorRole = role;
      return this;
    }

    public Builder withApi(String api) {
      log.api = api;
      return this;
    }

    public Builder withHttpMethod(String method) {
      log.httpMethod = method;
      return this;
    }

    public Builder withAction(String op) {
      log.action = op;
      return this;
    }

    public Builder withOriginServer(String origin) {
      log.originServer = origin;
      return this;
    }

    public Builder withAssetId(UUID id) {
      log.assetId = id;
      return this;
    }

    public Builder withAssetName(String name) {
      log.assetName = name;
      return this;
    }

    public Builder withAssetShortDescription(String desc) {
      log.assetShortDescription = desc;
      return this;
    }

    public Builder withAssetType(String type) {
      log.assetType = type;
      return this;
    }

    public Builder withAssetAccessPolicy(String policy) {
      log.assetAccessPolicy = policy;
      return this;
    }

    public Builder withAssetOrgId(UUID id) {
      log.assetOrgId = id;
      return this;
    }

    public Builder withAssetOrgName(String name) {
      log.assetOrgName = name;
      return this;
    }

    public Builder withAssetOrgType(String type) {
      log.assetOrgType = type;
      return this;
    }

    public Builder withAssetProviderId(UUID id) {
      log.assetProviderId = id;
      return this;
    }

    public Builder withAssetProviderName(String name) {
      log.assetProviderName = name;
      return this;
    }

    public Builder withSizeBytes(Long size) {
      log.sizeBytes = size;
      return this;
    }

    public Builder withAmount(BigDecimal amount) {
      log.amount = amount;
      return this;
    }

    public Builder withRequestId(UUID id) {
      log.requestId = id;
      return this;
    }

    public Builder withLogType(String type) {
      log.logType = type;
      return this;
    }

    public Builder withIpAddress(String ip) {
      log.ipAddress = ip;
      return this;
    }

    public Builder withUserAgent(String ua) {
      log.userAgent = ua;
      return this;
    }

    public Builder withCreatedAt(String ts) {
      log.createdAt = ts;
      return this;
    }

    public Builder withContext(JsonObject ctx) {
      log.context = ctx;
      return this;
    }

    public Builder withSandboxType(String sandboxType) {
      log.sandboxType = sandboxType;
      return this;
    }

    public UserActivityAuditLogBuilder build() {
      return log;
    }
  }
}
