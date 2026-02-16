package org.cdpg.dx.aaa.credit.util;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.cdpg.dx.aaa.credit.models.CreditRequest;
import org.cdpg.dx.aaa.credit.models.CreditRequestAuditOperation;
import org.cdpg.dx.acl.accessRequest.dao.model.AccessRequestDto;
import org.cdpg.dx.acl.accessRequest.model.AccessRequestAuditOperation;
import org.cdpg.dx.auditing.v2.model.UserActivityAuditLogBuilder;
import org.cdpg.dx.auditing.v2.util.AuditLogHelper;
import org.checkerframework.checker.units.qual.C;

import static org.cdpg.dx.aaa.credit.models.CreditRequestAuditOperation.CREDIT;
import static org.cdpg.dx.aaa.credit.util.Constants.*;

public final class CreditRequestAuditLogHelper {

  private CreditRequestAuditLogHelper() {
  }

  public static UserActivityAuditLogBuilder buildAudit(
    RoutingContext ctx,
    JsonObject body,
    CreditRequestAuditOperation operation
  ) {

    return AuditLogHelper.createBaseAudit(ctx)
      .withLogType("COMPUTE")
      .withOriginServer("AAA")
      .withAction(operation.value())

      /* -------- Request / workflow -------- */
      .withRequestId(safeUuid(body.getString(CREDIT_REQUEST_ID)))

      /* -------- Credit / transaction -------- */
      .withAmount(
        body.getDouble(BALANCE) != null
          ? BigDecimal.valueOf(body.getDouble(BALANCE))
          : body.getDouble(AMOUNT) != null
          ? BigDecimal.valueOf(body.getDouble(AMOUNT))
          : null

      )
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

