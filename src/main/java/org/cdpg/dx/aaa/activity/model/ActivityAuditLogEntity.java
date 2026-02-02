package org.cdpg.dx.aaa.activity.model;

import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.cdpg.dx.database.postgres.base.entity.BaseEntity;
import org.cdpg.dx.database.postgres.util.EntityUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.cdpg.dx.auditing.schema.ActivityAuditSchema.*;

public class ActivityAuditLogEntity implements BaseEntity<ActivityAuditLogEntity> {

  private static final Logger LOGGER = LogManager.getLogger(ActivityAuditLogEntity.class);

  private UUID id;
  private UUID userId;
  private UUID orgId;
  private String orgName;
  private String role;
  private Boolean isDelegate;
  private UUID delegatorId;
  private String delegatorRole;
  private UUID providerId;

  private String api;
  private String method;
  private String operation;
  private String originServer;
  private String issuer;

  private String entityType;
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
  private Boolean myActivityEnabled;

  public static ActivityAuditLogEntity fromJson(JsonObject json) {

    ActivityAuditLogEntity e = new ActivityAuditLogEntity();

    e.id = EntityUtil.parseUUID(json.getString(ID), ID);
    e.userId = EntityUtil.parseUUID(json.getString(USER_ID), USER_ID);
    e.orgId = EntityUtil.parseUUID(json.getString(ORG_ID), ORG_ID);
    e.orgName = json.getString(ORG_NAME);

    e.role = json.getString(ROLE);
    e.isDelegate = json.getBoolean(IS_DELEGATE);

    e.delegatorId = EntityUtil.parseUUID(json.getString(DELEGATOR_ID), DELEGATOR_ID);
    e.delegatorRole = json.getString(DELEGATOR_ROLE);

    e.providerId = EntityUtil.parseUUID(json.getString(PROVIDER_ID), PROVIDER_ID);

    e.api = json.getString(API);
    e.method = json.getString(METHOD);
    e.operation = json.getString(ACTION);
    e.originServer = json.getString(ORIGIN_SERVER);
    e.issuer = json.getString(ISSUER);

    e.entityType = json.getString(ENTITY_TYPE);
    e.entityId = EntityUtil.parseUUID(json.getString(ENTITY_ID), ENTITY_ID);
    e.entityName = json.getString(ENTITY_NAME);
    e.shortDescription = json.getString(SHORT_DESCRIPTION);

    e.ipAddress = json.getString(IP_ADDRESS);
    e.userAgent = json.getString(USER_AGENT);
    e.sizeBytes = json.getLong(SIZE_BYTES);

    e.createdAt = json.getString(CREATED_AT);
    e.epochMs = json.getLong(EPOCH_MS);
    e.ingestedAt = json.getString(INGESTED_AT);

    e.status = json.getString(STATUS);
    e.statusMessage = json.getString(STATUS_MESSAGE);

    e.details = json.getJsonObject(DETAILS);
    e.myActivityEnabled = json.getBoolean(MYACTIVITY_ENABLED, false);

    return e;
  }

  // --------------------------------------------
  // DAO insert
  // --------------------------------------------
  @Override
  public Map<String, Object> toNonEmptyFieldsMap() {

    Map<String, Object> map = new HashMap<>();

    EntityUtil.putIfNonEmpty(map, ID, safe(id));
    EntityUtil.putIfNonEmpty(map, USER_ID, safe(userId));
    EntityUtil.putIfNonEmpty(map, ORG_ID, safe(orgId));
    EntityUtil.putIfNonEmpty(map, ORG_NAME, orgName);
    EntityUtil.putIfNonEmpty(map, ROLE, role);
    EntityUtil.putIfNonEmpty(map, IS_DELEGATE, isDelegate);
    EntityUtil.putIfNonEmpty(map, DELEGATOR_ID, safe(delegatorId));
    EntityUtil.putIfNonEmpty(map, DELEGATOR_ROLE, delegatorRole);
    EntityUtil.putIfNonEmpty(map, PROVIDER_ID, safe(providerId));

    EntityUtil.putIfNonEmpty(map, API, api);
    EntityUtil.putIfNonEmpty(map, METHOD, method);
    EntityUtil.putIfNonEmpty(map, ACTION, operation);
    EntityUtil.putIfNonEmpty(map, ORIGIN_SERVER, originServer);
    EntityUtil.putIfNonEmpty(map, ISSUER, issuer);

    EntityUtil.putIfNonEmpty(map, ENTITY_TYPE, entityType);
    EntityUtil.putIfNonEmpty(map, ENTITY_ID, safe(entityId));
    EntityUtil.putIfNonEmpty(map, ENTITY_NAME, entityName);
    EntityUtil.putIfNonEmpty(map, SHORT_DESCRIPTION, shortDescription);

    EntityUtil.putIfNonEmpty(map, IP_ADDRESS, ipAddress);
    EntityUtil.putIfNonEmpty(map, USER_AGENT, userAgent);
    EntityUtil.putIfNonEmpty(map, SIZE_BYTES, sizeBytes);

    EntityUtil.putIfNonEmpty(map, CREATED_AT, createdAt);
    EntityUtil.putIfNonEmpty(map, EPOCH_MS, epochMs);
    EntityUtil.putIfNonEmpty(map, INGESTED_AT, ingestedAt);

    EntityUtil.putIfNonEmpty(map, STATUS, status);
    EntityUtil.putIfNonEmpty(map, STATUS_MESSAGE, statusMessage);
    EntityUtil.putIfNonEmpty(map, DETAILS, details);
    EntityUtil.putIfNonEmpty(map, MYACTIVITY_ENABLED, myActivityEnabled);

    return map;
  }

  @Override
  public JsonObject toJson() {
    JsonObject json = new JsonObject();

    putIfNonNull(json, "id", id != null ? id.toString() : null);
    putIfNonNull(json, "userId", userId != null ? userId.toString() : null);
    putIfNonNull(json, "orgId", orgId != null ? orgId.toString() : null);
    putIfNonNull(json, "orgName", orgName);

    putIfNonNull(json, "role", role);
    putIfNonNull(json, "isDelegate", isDelegate);

    putIfNonNull(json, "delegatorId", delegatorId != null ? delegatorId.toString() : null);
    putIfNonNull(json, "delegatorRole", delegatorRole);

    putIfNonNull(json, "providerId", providerId != null ? providerId.toString() : null);

    putIfNonNull(json, "api", api);
    putIfNonNull(json, "method", method);
    putIfNonNull(json, "action", operation);
    putIfNonNull(json, "originServer", originServer);
    putIfNonNull(json, "issuer", issuer);

    putIfNonNull(json, "entityType", entityType);
    putIfNonNull(json, "entityId", entityId != null ? entityId.toString() : null);
    putIfNonNull(json, "entityName", entityName);
    putIfNonNull(json, "shortDescription", shortDescription);

    putIfNonNull(json, "ipAddress", ipAddress);
    putIfNonNull(json, "userAgent", userAgent);
    putIfNonNull(json, "sizeBytes", sizeBytes);

    putIfNonNull(json, "createdAt", createdAt);
    putIfNonNull(json, "epochMs", epochMs);
    putIfNonNull(json, "ingestedAt", ingestedAt);

    putIfNonNull(json, "status", status);
    putIfNonNull(json, "statusMessage", statusMessage);

    putIfNonNull(json, "details", details);
    putIfNonNull(json, "myActivityEnabled", myActivityEnabled);

    return json;
  }

  private static String safe(Object o) {
    return o == null ? null : o.toString();
  }

  @Override
  public String getTableName() {
    return "activity_audit_log";
  }

  public static void putIfNonNull(JsonObject json, String key, Object value) {
    if (value != null) {
      json.put(key, value);
    }
  }
}
