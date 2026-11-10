package org.cdpg.dx.auditing.schema;

/**
 * Single source of truth for Activity Audit Log schema. Used by DB entities, audit builders, RMQ
 * producers/consumers.
 */
public final class ActivityAuditSchema {

  // Identity
  public static final String ID = "id";
  public static final String USER_ID = "user_id";
  public static final String ORG_ID = "org_id";
  public static final String ORG_NAME = "org_name";
  public static final String ROLE = "role";
  public static final String IS_DELEGATE = "is_delegate";
  public static final String DELEGATOR_ID = "delegator_id";
  public static final String DELEGATOR_ROLE = "delegator_role";
  public static final String PROVIDER_ID = "provider_id";

  // Request / action
  public static final String API = "api";
  public static final String METHOD = "method";
  public static final String ACTION = "action";
  public static final String ORIGIN_SERVER = "origin_server";
  public static final String ISSUER = "issuer";

  // Entity
  public static final String ENTITY_TYPE = "entity_type";
  public static final String ENTITY_ID = "entity_id";
  public static final String ENTITY_NAME = "entity_name";
  public static final String SHORT_DESCRIPTION = "short_description";

  // Network / client
  public static final String IP_ADDRESS = "ip_address";
  public static final String USER_AGENT = "user_agent";
  public static final String SIZE_BYTES = "size_bytes";

  // Time
  public static final String CREATED_AT = "created_at";
  public static final String EPOCH_MS = "epoch_ms";
  public static final String INGESTED_AT = "ingested_at";

  // Status
  public static final String STATUS = "status";
  public static final String STATUS_MESSAGE = "status_message";

  // Extra
  public static final String DETAILS = "details";
  public static final String MYACTIVITY_ENABLED = "myactivity_enabled";

  private ActivityAuditSchema() {}
}
