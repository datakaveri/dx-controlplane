package org.cdpg.dx.aaa.delegation.util;

import java.util.Map;

public final class Constants {

  public static final String DELEGATION_GRANT_TABLE="delegation_grants";
  public static final String DELEGATION_REQUEST_TABLE="delegation_update_requests";

  public static final String DELEGATION_SCOPE_CONSTRAINT_TABLE="delegation_scope_constraints";

  public static final String ISSUED_TOKEN_TABLE="issued_tokens";


  //   COMMON COLUMN NAMES
  public static final String ID = "id";
  public static final String DELEGATION_ID = "delegation_id";
  public static final String DELEGATOR_ID = "delegator_id";
  public static final String DELEGATE_ID = "delegate_id";
  public static final String JUSTIFICATION = "justification";
  public static final String EXPIRY_AT = "expiry_at";
  public static final String CREATED_AT = "created_at";
  public static final String REVOKED_AT = "revoked_at";
  public static final String REVIEWED_AT = "reviewed_at";
  public static final String REVIEWER_ID = "reviewer_id";

//     DELEGATION REQUEST FIELDS
  public static final String REQUEST_ID = "request_id";
  public static final String REQUESTER_ID = "requester_id";
  public static final String REQUESTED_SCOPES = "requested_scopes";
  public static final String REQUESTED_RESOURCES = "requested_resources";
  public static final String REQUESTED_EXPIRY = "requested_expiry";

//     DELEGATION GRANT FIELDS
  public static final String GRANT_STATUS = "status";
  public static final String GRANT_CREATED_AT = "created_at";
  public static final String GRANT_REVOKED_AT = "revoked_at";

//     SCOPE CONSTRAINT FIELDS
  public static final String SCOPE_CONSTRAINT_ID = "id";
  public static final String SCOPE = "scope";
  public static final String ENTITY_ID = "entity_id";

//     ISSUED TOKEN FIELDS
  public static final String JTI = "jti";
  public static final String SCOPES = "scopes";
  public static final String EXPIRES_AT = "expires_at";
  public static final String REVOKED = "revoked";

//     STATIC STRINGS / CONSTANT VALUES
  public static final String STATUS = "status";

  public static final String PENDING = "pending";
  public static final String APPROVED = "approved";

  public static final String ACTIVE = "active";
  public static final String REJECTED = "rejected";
  public static final String EXPIRED = "expired";


  public static final Map<String, String> ALLOWED_FILTER_MAP_FOR_DELEGATION_GRANT = Map.of(
    "delegationId", DELEGATION_ID,
    "delegatorId", DELEGATOR_ID,
    "delegateId", DELEGATE_ID,
    "status", STATUS);

  public static final Map<String,String> API_TO_DB_DELEGATION_GRANT = Map.ofEntries(
    Map.entry("delegationId", DELEGATION_ID),
    Map.entry("delegatorId",DELEGATOR_ID),
    Map.entry("delegateId",DELEGATE_ID),
    Map.entry("createdAt",CREATED_AT),
    Map.entry("revokedAt",REVOKED_AT),
    Map.entry("expiryAt",EXPIRY_AT),
    Map.entry("justification",JUSTIFICATION),
    Map.entry("status",STATUS));


}
