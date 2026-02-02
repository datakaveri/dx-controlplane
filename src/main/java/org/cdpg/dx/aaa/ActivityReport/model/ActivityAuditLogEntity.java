package org.cdpg.dx.aaa.ActivityReport.model;

import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.cdpg.dx.auditing.v2.Constant.UserActivityAuditSchema;
import org.cdpg.dx.database.postgres.base.entity.BaseEntity;
import org.cdpg.dx.database.postgres.util.EntityUtil;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.cdpg.dx.auditing.v2.Constant.UserActivityAuditSchema.*;

public class ActivityAuditLogEntity implements BaseEntity<ActivityAuditLogEntity> {

  private static final Logger LOGGER = LogManager.getLogger(ActivityAuditLogEntity.class);

  // -----------------------------
  // Columns
  // -----------------------------
  private UUID id;

  private UUID userId;
  private String userName;

  private UUID orgId;
  private String orgName;
  private String orgType;

  private UUID appId;
  private String role;
  private String issuer;

  private UUID delegatorId;
  private String delegatorRole;

  private String api;
  private String method;
  private String action;
  private String originServer;

  private UUID assetId;
  private String assetName;
  private String assetSortDescription;
  private String assetType;
  private String assetAccessPolicy;

  private UUID assetOrgId;
  private String assetOrgName;
  private String assetOrgType;

  private UUID assetProviderId;
  private String assetProviderName;

  private Long sizeBytes;
  private BigDecimal amount;

  private UUID requestId;

  private String logType;

  private String ipAddress;
  private String userAgent;

  private String createdAt;

  private JsonObject context;

  // --------------------------------------------
  // fromJson (RMQ / API consumer)
  // --------------------------------------------
  public static ActivityAuditLogEntity fromJson(JsonObject json) {

    ActivityAuditLogEntity e = new ActivityAuditLogEntity();

    e.id = EntityUtil.parseUUID(json.getString(ID), ID);

    e.userId = EntityUtil.parseUUID(json.getString(USER_ID), USER_ID);
    e.userName = json.getString(USER_NAME);

    e.orgId = EntityUtil.parseUUID(json.getString(ORG_ID), ORG_ID);
    e.orgName = json.getString(ORG_NAME);
    e.orgType = json.getString(ORG_TYPE);

    e.appId = EntityUtil.parseUUID(json.getString(APP_ID), APP_ID);
    e.role = json.getString(ROLE);
    e.issuer = json.getString(ISSUER);

    e.delegatorId = EntityUtil.parseUUID(json.getString(DELEGATOR_ID), DELEGATOR_ID);
    e.delegatorRole = json.getString(DELEGATOR_ROLE);

    e.api = json.getString(API);
    e.method = json.getString(HTTP_METHOD);
    e.action = json.getString(ACTION);
    e.originServer = json.getString(ORIGIN_SERVER);

    e.assetId = EntityUtil.parseUUID(json.getString(ASSET_ID), ASSET_ID);
    e.assetName = json.getString(ASSET_NAME);
    e.assetSortDescription = json.getString(ASSET_SORT_DESCRIPTION);
    e.assetType = json.getString(ASSET_TYPE);
    e.assetAccessPolicy = json.getString(ASSET_ACCESS_POLICY);

    e.assetOrgId = EntityUtil.parseUUID(json.getString(ASSET_ORG_ID), ASSET_ORG_ID);
    e.assetOrgName = json.getString(ASSET_ORG_NAME);
    e.assetOrgType = json.getString(ASSET_ORG_TYPE);

    e.assetProviderId = EntityUtil.parseUUID(json.getString(ASSET_PROVIDER_ID), ASSET_PROVIDER_ID);
    e.assetProviderName = json.getString(ASSET_PROVIDER_NAME);

    e.sizeBytes = json.getLong(SIZE_BYTES);
    e.sizeBytes = json.getLong(SIZE_BYTES);

    Object rawAmount = json.getValue(UserActivityAuditSchema.AMOUNT);
    e.amount = rawAmount instanceof Number ? new BigDecimal(rawAmount.toString()) : null;

    e.requestId = EntityUtil.parseUUID(json.getString(REQUEST_ID), REQUEST_ID);

    e.logType = json.getString(LOG_TYPE);

    e.ipAddress = json.getString(IP_ADDRESS);
    e.userAgent = json.getString(USER_AGENT);

    e.createdAt = json.getString(CREATED_AT);

    e.context = json.getJsonObject(CONTEXT);

    return e;
  }

  // --------------------------------------------
  // DAO insert map
  // --------------------------------------------
  @Override
  public Map<String, Object> toNonEmptyFieldsMap() {

    Map<String, Object> map = new HashMap<>();

    EntityUtil.putIfNonEmpty(map, ID, safe(id));

    EntityUtil.putIfNonEmpty(map, USER_ID, safe(userId));
    EntityUtil.putIfNonEmpty(map, USER_NAME, userName);

    EntityUtil.putIfNonEmpty(map, ORG_ID, safe(orgId));
    EntityUtil.putIfNonEmpty(map, ORG_NAME, orgName);
    EntityUtil.putIfNonEmpty(map, ORG_TYPE, orgType);

    EntityUtil.putIfNonEmpty(map, APP_ID, safe(appId));
    EntityUtil.putIfNonEmpty(map, ROLE, role);
    EntityUtil.putIfNonEmpty(map, ISSUER, issuer);

    EntityUtil.putIfNonEmpty(map, DELEGATOR_ID, safe(delegatorId));
    EntityUtil.putIfNonEmpty(map, DELEGATOR_ROLE, delegatorRole);

    EntityUtil.putIfNonEmpty(map, API, api);
    EntityUtil.putIfNonEmpty(map, HTTP_METHOD, method);
    EntityUtil.putIfNonEmpty(map, ACTION, action);
    EntityUtil.putIfNonEmpty(map, ORIGIN_SERVER, originServer);

    EntityUtil.putIfNonEmpty(map, ASSET_ID, safe(assetId));
    EntityUtil.putIfNonEmpty(map, ASSET_NAME, assetName);
    EntityUtil.putIfNonEmpty(map, ASSET_SORT_DESCRIPTION, assetSortDescription);
    EntityUtil.putIfNonEmpty(map, ASSET_TYPE, assetType);
    EntityUtil.putIfNonEmpty(map, ASSET_ACCESS_POLICY, assetAccessPolicy);

    EntityUtil.putIfNonEmpty(map, ASSET_ORG_ID, safe(assetOrgId));
    EntityUtil.putIfNonEmpty(map, ASSET_ORG_NAME, assetOrgName);
    EntityUtil.putIfNonEmpty(map, ASSET_ORG_TYPE, assetOrgType);

    EntityUtil.putIfNonEmpty(map, ASSET_PROVIDER_ID, safe(assetProviderId));
    EntityUtil.putIfNonEmpty(map, ASSET_PROVIDER_NAME, assetProviderName);

    EntityUtil.putIfNonEmpty(map, SIZE_BYTES, sizeBytes);
    EntityUtil.putIfNonEmpty(map, AMOUNT, amount);

    EntityUtil.putIfNonEmpty(map, REQUEST_ID, safe(requestId));
    EntityUtil.putIfNonEmpty(map, LOG_TYPE, logType);

    EntityUtil.putIfNonEmpty(map, IP_ADDRESS, ipAddress);
    EntityUtil.putIfNonEmpty(map, USER_AGENT, userAgent);

    EntityUtil.putIfNonEmpty(map, CREATED_AT, createdAt);
    EntityUtil.putIfNonEmpty(map, CONTEXT, context);

    return map;
  }

  @Override
  public JsonObject toJson() {
    JsonObject json = new JsonObject();

    putIfNonNull(json, "id", safe(id));
    putIfNonNull(json, "userId", safe(userId));
    putIfNonNull(json, "userName", userName);

    putIfNonNull(json, "orgId", safe(orgId));
    putIfNonNull(json, "orgName", orgName);
    putIfNonNull(json, "orgType", orgType);

    putIfNonNull(json, "appId", safe(appId));
    putIfNonNull(json, "role", role);
    putIfNonNull(json, "issuer", issuer);

    putIfNonNull(json, "delegatorId", safe(delegatorId));
    putIfNonNull(json, "delegatorRole", delegatorRole);

    putIfNonNull(json, "api", api);
    putIfNonNull(json, "method", method);
    putIfNonNull(json, "action", action);
    putIfNonNull(json, "originServer", originServer);

    putIfNonNull(json, "assetId", safe(assetId));
    putIfNonNull(json, "assetName", assetName);
    putIfNonNull(json, "assetShortDescription", assetSortDescription);
    putIfNonNull(json, "assetType", assetType);
    putIfNonNull(json, "assetAccessPolicy", assetAccessPolicy);

    putIfNonNull(json, "assetOrgId", safe(assetOrgId));
    putIfNonNull(json, "assetOrgName", assetOrgName);
    putIfNonNull(json, "assetOrgType", assetOrgType);

    putIfNonNull(json, "assetProviderId", safe(assetProviderId));
    putIfNonNull(json, "assetProviderName", assetProviderName);

    putIfNonNull(json, "sizeBytes", sizeBytes);
    putIfNonNull(json, "amount", amount);

    putIfNonNull(json, "requestId", safe(requestId));
    putIfNonNull(json, "logType", logType);

    putIfNonNull(json, "ipAddress", ipAddress);
    putIfNonNull(json, "userAgent", userAgent);

    putIfNonNull(json, "createdAt", createdAt);
    putIfNonNull(json, "context", context);

    return json;
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getAssetId() {
    return assetId;
  }

  public void setAssetId(UUID assetId) {
    this.assetId = assetId;
  }

  public String getAssetName() {
    return assetName;
  }

  public void setAssetName(String assetName) {
    this.assetName = assetName;
  }

  public String getAssetSortDescription() {
    return assetSortDescription;
  }

  public void setAssetSortDescription(String assetSortDescription) {
    this.assetSortDescription = assetSortDescription;
  }

  public String getAssetType() {
    return assetType;
  }

  public void setAssetType(String assetType) {
    this.assetType = assetType;
  }

  public String getAssetAccessPolicy() {
    return assetAccessPolicy;
  }

  public void setAssetAccessPolicy(String assetAccessPolicy) {
    this.assetAccessPolicy = assetAccessPolicy;
  }

  public UUID getAssetOrgId() {
    return assetOrgId;
  }

  public void setAssetOrgId(UUID assetOrgId) {
    this.assetOrgId = assetOrgId;
  }

  public String getAssetOrgName() {
    return assetOrgName;
  }

  public void setAssetOrgName(String assetOrgName) {
    this.assetOrgName = assetOrgName;
  }

  public String getAssetOrgType() {
    return assetOrgType;
  }

  public void setAssetOrgType(String assetOrgType) {
    this.assetOrgType = assetOrgType;
  }

  public UUID getAssetProviderId() {
    return assetProviderId;
  }

  public void setAssetProviderId(UUID assetProviderId) {
    this.assetProviderId = assetProviderId;
  }

  public String getAssetProviderName() {
    return assetProviderName;
  }

  public void setAssetProviderName(String assetProviderName) {
    this.assetProviderName = assetProviderName;
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public String getUserName() {
    return userName;
  }

  public void setUserName(String userName) {
    this.userName = userName;
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

  public String getOrgType() {
    return orgType;
  }

  public void setOrgType(String orgType) {
    this.orgType = orgType;
  }

  public UUID getAppId() {
    return appId;
  }

  public void setAppId(UUID appId) {
    this.appId = appId;
  }

  public String getRole() {
    return role;
  }

  public void setRole(String role) {
    this.role = role;
  }

  public String getIssuer() {
    return issuer;
  }

  public void setIssuer(String issuer) {
    this.issuer = issuer;
  }

  public UUID getDelegatorId() {
    return delegatorId;
  }

  public void setDelegatorId(UUID delegatorId) {
    this.delegatorId = delegatorId;
  }

  public String getDelegatorRole() {
    return delegatorRole;
  }

  public void setDelegatorRole(String delegatorRole) {
    this.delegatorRole = delegatorRole;
  }

  public String getApi() {
    return api;
  }

  public void setApi(String api) {
    this.api = api;
  }

  public String getMethod() {
    return method;
  }

  public void setMethod(String method) {
    this.method = method;
  }

  public String getAction() {
    return action;
  }

  public void setAction(String action) {
    this.action = action;
  }

  public String getOriginServer() {
    return originServer;
  }

  public void setOriginServer(String originServer) {
    this.originServer = originServer;
  }

  public Long getSizeBytes() {
    return sizeBytes;
  }

  public void setSizeBytes(Long sizeBytes) {
    this.sizeBytes = sizeBytes;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = amount;
  }

  public UUID getRequestId() {
    return requestId;
  }

  public void setRequestId(UUID requestId) {
    this.requestId = requestId;
  }

  public String getLogType() {
    return logType;
  }

  public void setLogType(String logType) {
    this.logType = logType;
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

  public String getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(String createdAt) {
    this.createdAt = createdAt;
  }

  public JsonObject getContext() {
    return context;
  }

  public void setContext(JsonObject context) {
    this.context = context;
  }

  private static String safe(Object o) {
    return o == null ? null : o.toString();
  }

  @Override
  public String getTableName() {
    return "user_activity_audit_log";
  }

  private static void putIfNonNull(JsonObject json, String key, Object value) {
    if (value != null) {
      json.put(key, value);
    }
  }
}
