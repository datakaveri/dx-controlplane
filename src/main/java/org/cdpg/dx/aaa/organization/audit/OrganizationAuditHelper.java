package org.cdpg.dx.aaa.organization.audit;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import java.util.UUID;

import org.cdpg.dx.auditing.enums.EntityType;
import org.cdpg.dx.auditing.enums.Operation;
import org.cdpg.dx.auditing.enums.OriginServer;
import org.cdpg.dx.auditing.model.ActivityAuditLogBuilder;
import org.cdpg.dx.auditing.util.AuditLogHelper;

import static org.cdpg.dx.aaa.organization.audit.OrganizationAuditConstants.*;

/**
 * OrganizationAuditHelper
 *
 * <p>Responsible ONLY for building audit logs for Organization domain. Does NOT publish or persist
 * logs.
 *
 * <p>Default behavior: - myActivityEnabled = false - Explicitly enabled ONLY for end-user initiated
 * actions
 */
public final class OrganizationAuditHelper {

  private OrganizationAuditHelper() {}

  /* =================================================
   * Organization creation request
   * ================================================= */

  /** User submits organization creation request (My Activity) */
  public static ActivityAuditLogBuilder buildOrgCreateRequestAudit(
      RoutingContext ctx, UUID requestId, String orgName) {

    return AuditLogHelper.createBaseAudit(ctx)
        .withOrigin(OriginServer.AAA)
        .withOperation(Operation.SUBMIT)
        .withEntityType(EntityType.ORG_REQUEST)
        .withEntityId(requestId)
        .withEntityName(orgName)
        .withMyActivityEnabled(true)
        .build();
  }

  /** User deletes their org creation request (My Activity) */
  public static ActivityAuditLogBuilder buildDeletedOrgCreateRequestAudit(
      RoutingContext ctx, UUID requestId) {

    return AuditLogHelper.createBaseAudit(ctx)
        .withOrigin(OriginServer.AAA)
        .withOperation(Operation.DELETE)
        .withEntityType(EntityType.ORG_REQUEST)
        .withEntityId(requestId)
        .withMyActivityEnabled(true)
        .build();
  }

  /** COS_ADMIN approves or rejects org creation request (Admin activity) */
  public static ActivityAuditLogBuilder buildOrgCreateOrgUpdateAudit(
      RoutingContext ctx, UUID requestId, String status) {

    Operation operation = status.equalsIgnoreCase("granted") ? Operation.APPROVE : Operation.REJECT;

    return AuditLogHelper.createBaseAudit(ctx)
        .withOrigin(OriginServer.AAA)
        .withOperation(operation)
        .withEntityType(EntityType.ORG_REQUEST)
        .withEntityId(requestId)
        .build();
  }

  /** COS_ADMIN deletes org creation request */
  public static ActivityAuditLogBuilder buildOrgCreateDeleteAudit(
      RoutingContext ctx, UUID requestId, String reason) {

    return AuditLogHelper.createBaseAudit(ctx)
        .withOrigin(OriginServer.AAA)
        .withOperation(Operation.DELETE)
        .withEntityType(EntityType.ORG_REQUEST)
        .withEntityId(requestId)
        .withDetails(new JsonObject().put(REASON, reason))
        .build();
  }

  /** COS_ADMIN lists org creation requests */
  public static ActivityAuditLogBuilder buildGetOrgCreateListAudit(RoutingContext ctx) {
    return AuditLogHelper.createBaseAudit(ctx)
        .withOrigin(OriginServer.AAA)
        .withOperation(Operation.LIST)
        .withEntityType(EntityType.ORG_REQUEST)
        .build();
  }

  /** Organization entity created (system result of approval) */
  public static ActivityAuditLogBuilder buildOrganizationCreatedAudit(
      RoutingContext ctx, UUID orgId, String orgName, UUID requestId) {

    return AuditLogHelper.createBaseAudit(ctx)
        .withOrigin(OriginServer.AAA)
        .withOperation(Operation.CREATE)
        .withEntityType(EntityType.ORGANIZATION)
        .withEntityId(orgId)
        .withEntityName(orgName)
        .withDetails(new JsonObject().put(SOURCE_REQUEST_ID, requestId))
        .build();
  }

  /* =================================================
   * Join organization
   * ================================================= */

  /** User submits join organization request (My Activity) */
  public static ActivityAuditLogBuilder buildJoinOrgRequestAudit(
      RoutingContext ctx, UUID requestId, UUID orgId, String requestedRole) {

    JsonObject details =
        new JsonObject()
            .put(REQUEST_TYPE, JOIN_ORGANIZATION)
            .put(REQUESTED_ROLE, requestedRole)
            .put(ORG_ID, orgId);

    return AuditLogHelper.createBaseAudit(ctx)
        .withOrigin(OriginServer.AAA)
        .withOperation(Operation.SUBMIT)
        .withEntityType(EntityType.ORG_REQUEST)
        .withEntityId(requestId)
        .withDetails(details)
        .withMyActivityEnabled(true)
        .build();
  }

  /** ORG_ADMIN lists join requests */
  public static ActivityAuditLogBuilder buildViewJoinOrgRequestsAudit(
      RoutingContext ctx, UUID orgId) {

    return AuditLogHelper.createBaseAudit(ctx)
        .withOrigin(OriginServer.AAA)
        .withOperation(Operation.LIST)
        .withEntityType(EntityType.ORG_REQUEST)
        .withEntityId(orgId)
        .build();
  }

  /** User views own join requests (My Activity) */
  public static ActivityAuditLogBuilder buildViewUserJoinOrgRequestsAudit(
      RoutingContext ctx, UUID userId) {

    return AuditLogHelper.createBaseAudit(ctx)
        .withOrigin(OriginServer.AAA)
        .withOperation(Operation.LIST)
        .withEntityType(EntityType.ORG_REQUEST)
        .withEntityId(userId)
        .withMyActivityEnabled(true)
        .build();
  }

  /** ORG_ADMIN approves join request */
  public static ActivityAuditLogBuilder buildJoinOrgApproveAudit(
      RoutingContext ctx, UUID requestId, UUID orgId, String orgName) {

    return AuditLogHelper.createBaseAudit(ctx)
        .withOrigin(OriginServer.AAA)
        .withOperation(Operation.APPROVE)
        .withEntityType(EntityType.ORG_REQUEST)
        .withEntityId(requestId)
        .withEntityName(orgName)
        .withDetails(new JsonObject().put(ORG_ID, orgId).put(ORG_NAME, orgName))
        .build();
  }

  /** ORG_ADMIN rejects join request */
  public static ActivityAuditLogBuilder buildJoinOrgRejectAudit(
      RoutingContext ctx, UUID requestId, UUID orgId, String orgName, String reason) {

    return AuditLogHelper.createBaseAudit(ctx)
        .withOrigin(OriginServer.AAA)
        .withOperation(Operation.REJECT)
        .withEntityType(EntityType.ORG_REQUEST)
        .withEntityId(requestId)
        .withEntityName(orgName)
        .withDetails(new JsonObject().put(ORG_ID, orgId).put(REASON, reason))
        .build();
  }

  /** User withdraws join request (My Activity) */
  public static ActivityAuditLogBuilder buildWithdrawJoinOrgRequestAudit(
      RoutingContext ctx, UUID requestId, UUID orgId) {

    return AuditLogHelper.createBaseAudit(ctx)
        .withOrigin(OriginServer.AAA)
        .withOperation(Operation.WITHDRAW)
        .withEntityType(EntityType.ORG_REQUEST)
        .withEntityId(requestId)
        .withDetails(new JsonObject().put(ORG_ID, orgId))
        .withMyActivityEnabled(true)
        .build();
  }

  /* -------------------------------------------------
   * View organization by ID
   * ------------------------------------------------- */

  /**
   * User or admin views organization details
   *
   * <p>My Activity: - YES (user explicitly opened organization details)
   */
  public static ActivityAuditLogBuilder buildViewOrganizationAudit(
      RoutingContext ctx, UUID orgId, String orgName) {

    return AuditLogHelper.createBaseAudit(ctx)
        .withOrigin(OriginServer.AAA)
        .withOperation(Operation.VIEW)
        .withEntityType(EntityType.ORGANIZATION)
        .withEntityId(orgId)
        .withEntityName(orgName)
        .withMyActivityEnabled(true)
        .build();
  }

  /* =================================================
   * Organization users & roles
   * ================================================= */

  /** ORG_ADMIN lists organization users */
  public static ActivityAuditLogBuilder buildViewOrganizationUsersAudit(
      RoutingContext ctx, UUID orgId) {

    return AuditLogHelper.createBaseAudit(ctx)
        .withOrigin(OriginServer.AAA)
        .withOperation(Operation.LIST)
        .withEntityType(EntityType.ORGANIZATION)
        .withEntityId(orgId)
        .build();
  }

  /** ORG_ADMIN views organization user info */
  public static ActivityAuditLogBuilder buildViewOrganizationUserInfoAudit(
      RoutingContext ctx, UUID orgId, UUID targetUserId) {

    return AuditLogHelper.createBaseAudit(ctx)
        .withOrigin(OriginServer.AAA)
        .withOperation(Operation.VIEW)
        .withEntityType(EntityType.ORGANIZATION)
        .withEntityId(orgId)
        .withDetails(new JsonObject().put(TARGET_USER_ID, targetUserId))
        .build();
  }

  /** ORG_ADMIN updates user role */
  public static ActivityAuditLogBuilder buildUpdateOrganizationUserRoleAudit(
      RoutingContext ctx, UUID orgId, UUID targetUserId, String newRole) {

    return AuditLogHelper.createBaseAudit(ctx)
        .withOrigin(OriginServer.AAA)
        .withOperation(Operation.UPDATE)
        .withEntityType(EntityType.ORGANIZATION)
        .withEntityId(orgId)
        .withDetails(new JsonObject().put(TARGET_USER_ID, targetUserId).put(UPDATED_ROLE, newRole))
        .build();
  }

  /** ORG_ADMIN removes user from org */
  public static ActivityAuditLogBuilder buildRemoveOrganizationUserAudit(
      RoutingContext ctx, UUID orgId, UUID targetUserId) {

    return AuditLogHelper.createBaseAudit(ctx)
        .withOrigin(OriginServer.AAA)
        .withOperation(Operation.DELETE)
        .withEntityType(EntityType.ORGANIZATION)
        .withEntityId(orgId)
        .withDetails(new JsonObject().put(TARGET_USER_ID, targetUserId))
        .build();
  }

  /* =================================================
   * Provider role
   * ================================================= */

  /** User submits provider role request (My Activity) */
  public static ActivityAuditLogBuilder buildProviderRoleRequestSubmitAudit(
      RoutingContext ctx, UUID requestId, UUID orgId) {

    return AuditLogHelper.createBaseAudit(ctx)
        .withOrigin(OriginServer.AAA)
        .withOperation(Operation.SUBMIT)
        .withEntityType(EntityType.ORG_REQUEST)
        .withEntityId(requestId)
        .withDetails(new JsonObject().put(ORG_ID, orgId).put(REQUESTED_ROLE, ROLE_PROVIDER))
        .withMyActivityEnabled(true)
        .build();
  }

  /** ORG_ADMIN approves provider role request */
  public static ActivityAuditLogBuilder buildProviderRoleApproveAudit(
      RoutingContext ctx, UUID requestId, UUID orgId) {

    return AuditLogHelper.createBaseAudit(ctx)
        .withOrigin(OriginServer.AAA)
        .withOperation(Operation.APPROVE)
        .withEntityType(EntityType.ORG_REQUEST)
        .withEntityId(requestId)
        .withDetails(new JsonObject().put(ORG_ID, orgId))
        .build();
  }

  /** ORG_ADMIN rejects provider role request */
  public static ActivityAuditLogBuilder buildProviderRoleRejectAudit(
      RoutingContext ctx, UUID requestId, UUID orgId, String reason) {

    return AuditLogHelper.createBaseAudit(ctx)
        .withOrigin(OriginServer.AAA)
        .withOperation(Operation.REJECT)
        .withEntityType(EntityType.ORG_REQUEST)
        .withEntityId(requestId)
        .withDetails(new JsonObject().put(ORG_ID, orgId).put(REASON, reason))
        .build();
  }

  /** User withdraws provider role request (My Activity) */
  public static ActivityAuditLogBuilder buildProviderRoleWithdrawAudit(
      RoutingContext ctx, UUID requestId, UUID orgId) {

    return AuditLogHelper.createBaseAudit(ctx)
        .withOrigin(OriginServer.AAA)
        .withOperation(Operation.WITHDRAW)
        .withEntityType(EntityType.ORG_REQUEST)
        .withEntityId(requestId)
        .withDetails(new JsonObject().put(ORG_ID, orgId))
        .withMyActivityEnabled(true)
        .build();
  }

  /** Provider role granted on organization */
  public static ActivityAuditLogBuilder buildProviderRoleGrantedAudit(
      RoutingContext ctx, UUID orgId, UUID userId) {

    return AuditLogHelper.createBaseAudit(ctx)
        .withOrigin(OriginServer.AAA)
        .withOperation(Operation.UPDATE)
        .withEntityType(EntityType.ORGANIZATION)
        .withEntityId(orgId)
        .withDetails(new JsonObject().put(GRANTED_ROLE, ROLE_PROVIDER).put(TARGET_USER_ID, userId))
        .build();
  }

  /* =================================================
   * Organization update / delete
   * ================================================= */

  /** Organization updated */
  public static ActivityAuditLogBuilder buildOrganizationUpdateAudit(
      RoutingContext ctx, UUID orgId, String orgName, JsonObject updatedFields) {

    // TODO: confirm whether org update should appear in My Activity
    return AuditLogHelper.createBaseAudit(ctx)
        .withOrigin(OriginServer.AAA)
        .withOperation(Operation.UPDATE)
        .withEntityType(EntityType.ORGANIZATION)
        .withEntityId(orgId)
        .withEntityName(orgName)
        .withDetails(
            updatedFields != null ? new JsonObject().put("updatedFields", updatedFields) : null)
        .build();
  }

  /** Organization deleted */
  public static ActivityAuditLogBuilder buildOrganizationDeleteAudit(
      RoutingContext ctx, UUID orgId, String reason) {

    // TODO: decide visibility in My Activity
    return AuditLogHelper.createBaseAudit(ctx)
        .withOrigin(OriginServer.AAA)
        .withOperation(Operation.DELETE)
        .withEntityType(EntityType.ORGANIZATION)
        .withEntityId(orgId)
        .withDetails(reason != null ? new JsonObject().put(REASON, reason) : null)
        .build();
  }
}
