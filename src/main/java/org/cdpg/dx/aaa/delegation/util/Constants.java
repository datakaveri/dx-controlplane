package org.cdpg.dx.aaa.delegation.util;

public final class Constants {

  public static final String DELEGATION_GRANT_TABLE="delegation_grant_table";
  public static final String DELEGATION_REQUEST_TABLE="delegation_request_table";

  public static final String DELEGATION_SCOPE_CONSTRAINT_TABLE="delegation_scope_constraint_table";

  public static final String ISSUED_TOKEN_TABLE="issued_token_table";


  //   COMMON COLUMN NAMES
  public static final String ID = "id";
  public static final String DELEGATION_ID = "delegation_id";
  public static final String DELEGATOR_ID = "delegator_id";
  public static final String DELEGATE_ID = "delegate_id";
  public static final String JUSTIFICATION = "justification";
  public static final String EXPIRY_AT = "expiry_at";
  public static final String STATUS = "status";
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
  public static final String STATIC = "static";
  public static final String PENDING = "PENDING";
  public static final String APPROVED = "APPROVED";
  public static final String REJECTED = "REJECTED";

}
