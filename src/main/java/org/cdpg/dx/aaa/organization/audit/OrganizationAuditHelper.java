package org.cdpg.dx.aaa.organization.audit;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.math.BigDecimal;
import java.util.UUID;

import org.cdpg.dx.aaa.organization.models.OrganisationAuditOperation;
import org.cdpg.dx.auditing.enums.EntityType;
import org.cdpg.dx.auditing.enums.Operation;
import org.cdpg.dx.auditing.enums.OriginServer;
import org.cdpg.dx.auditing.model.ActivityAuditLogBuilder;
import org.cdpg.dx.auditing.v2.model.UserActivityAuditLogBuilder;
import org.cdpg.dx.auditing.v2.util.AuditLogHelper;

import static org.cdpg.dx.aaa.common.Constants.ID;
import static org.cdpg.dx.aaa.credit.util.Constants.*;
import static org.cdpg.dx.aaa.credit.util.Constants.AMOUNT;

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

  /** Organisation related auditing(My Activity) */
  public static UserActivityAuditLogBuilder buildOrganisationAudit(
      RoutingContext ctx, JsonObject body, OrganisationAuditOperation operation) {

    return AuditLogHelper.createBaseAudit(ctx)
      .withLogType("USER_ACTION")
      .withOriginServer("AAA")
      .withRequestId(
        body != null && body.getString(ID) != null
          ? safeUuid(body.getString(ID))
          : null
      )

      .withAction(operation.value())
      .build();
  }


  private static UUID safeUuid(String id) {
    try {
      return id == null ? null : UUID.fromString(id);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
