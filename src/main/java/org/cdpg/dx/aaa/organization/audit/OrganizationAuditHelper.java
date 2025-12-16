package org.cdpg.dx.aaa.organization.audit;

import io.vertx.ext.web.RoutingContext;
import io.vertx.core.json.JsonObject;
import java.util.UUID;

import org.cdpg.dx.auditing.enums.EntityType;
import org.cdpg.dx.auditing.enums.Operation;
import org.cdpg.dx.auditing.enums.OriginServer;
import org.cdpg.dx.auditing.model.ActivityAuditLogBuilder;
import org.cdpg.dx.auditing.util.AuditLogHelper;

/**
 * OrganizationAuditHelper
 *
 * <p>Responsible ONLY for building audit logs for Organization domain. Does NOT publish or persist
 * logs.
 */
public final class OrganizationAuditHelper {

  private OrganizationAuditHelper() {
    // utility class
  }

  /* -------------------------------------------------
   * Organization creation request
   * ------------------------------------------------- */

  /** User submits organization creation request */
  public static ActivityAuditLogBuilder buildOrgCreateRequestAudit(
      RoutingContext ctx, UUID requestId, String orgName) {
    return AuditLogHelper.createBaseAudit(ctx)
        .withOrigin(OriginServer.AAA)
        .withOperation(Operation.SUBMIT)
        .withEntityType(EntityType.ORG_REQUEST)
        .withEntityId(requestId)
        .withEntityName(orgName)
        .build();
  }

  public static ActivityAuditLogBuilder buildDeletedOrgCreateRequestAudit(
      RoutingContext ctx, UUID requestId) {
    return AuditLogHelper.createBaseAudit(ctx)
        .withOrigin(OriginServer.AAA)
        .withOperation(Operation.DELETE)
        .withEntityType(EntityType.ORG_REQUEST)
        .withEntityId(requestId)
        .build();
  }

  /** COS_ADMIN approves organization creation request */
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

  /** COS_ADMIN rejects organization creation request */
  public static ActivityAuditLogBuilder buildOrgCreateRejectAudit(
      RoutingContext ctx, UUID requestId, String orgName, String reason) {
    return AuditLogHelper.createBaseAudit(ctx)
        .withOrigin(OriginServer.AAA)
        .withOperation(Operation.REJECT)
        .withEntityType(EntityType.ORG_REQUEST)
        .withEntityId(requestId)
        .withEntityName(orgName)
        .withDetails(new JsonObject().put("reason", reason))
        .build();
  }

  /** Actual organization created */
  public static ActivityAuditLogBuilder buildOrganizationCreatedAudit(
      RoutingContext ctx, UUID orgId, String orgName, UUID requestId) {
    return AuditLogHelper.createBaseAudit(ctx)
        .withOrigin(OriginServer.AAA)
        .withOperation(Operation.CREATE)
        .withEntityType(EntityType.ORGANIZATION)
        .withEntityId(orgId)
        .withEntityName(orgName)
        .withDetails(new JsonObject().put("sourceRequestId", requestId))
        .build();
  }

  /* -------------------------------------------------
   * Join organization
   * ------------------------------------------------- */

  /** User requests to join organization */
  public static ActivityAuditLogBuilder buildJoinOrgRequestAudit(
      RoutingContext ctx, UUID requestId, UUID orgId, String orgName) {
    return AuditLogHelper.createBaseAudit(ctx)
        .withOrigin(OriginServer.AAA)
        .withOperation(Operation.SUBMIT)
        .withEntityType(EntityType.ORG_REQUEST)
        .withEntityId(requestId)
        .withEntityName(orgName)
        .withDetails(new JsonObject().put("orgId", orgId))
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
        .withDetails(new JsonObject().put("orgId", orgId))
        .build();
  }

  /* -------------------------------------------------
   * Become provider
   * ------------------------------------------------- */

  /** User requests to become provider */
  public static ActivityAuditLogBuilder buildBecomeProviderRequestAudit(
      RoutingContext ctx, UUID requestId, UUID orgId, String orgName) {
    return AuditLogHelper.createBaseAudit(ctx)
        .withOrigin(OriginServer.AAA)
        .withOperation(Operation.SUBMIT)
        .withEntityType(EntityType.ORG_REQUEST)
        .withEntityId(requestId)
        .withEntityName(orgName)
        .withDetails(new JsonObject().put("requestedRole", "provider"))
        .build();
  }

  /** Provider role granted */
  public static ActivityAuditLogBuilder buildProviderGrantedAudit(
      RoutingContext ctx, UUID orgId, String orgName) {
    return AuditLogHelper.createBaseAudit(ctx)
        .withOrigin(OriginServer.AAA)
        .withOperation(Operation.UPDATE)
        .withEntityType(EntityType.ORGANIZATION)
        .withEntityId(orgId)
        .withEntityName(orgName)
        .withDetails(new JsonObject().put("grantedRole", "provider"))
        .build();
  }

  /** Organization updated */
  public static ActivityAuditLogBuilder buildOrganizationUpdateAudit(
      RoutingContext ctx, UUID orgId, String orgName, JsonObject updatedFields) {
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
      RoutingContext ctx, UUID orgId, String orgName, String reason) {
    return AuditLogHelper.createBaseAudit(ctx)
        .withOrigin(OriginServer.AAA)
        .withOperation(Operation.DELETE)
        .withEntityType(EntityType.ORGANIZATION)
        .withEntityId(orgId)
        .withEntityName(orgName)
        .withDetails(reason != null ? new JsonObject().put("reason", reason) : null)
        .build();
  }
}
