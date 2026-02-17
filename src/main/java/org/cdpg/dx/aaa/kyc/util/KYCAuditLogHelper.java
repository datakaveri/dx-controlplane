package org.cdpg.dx.aaa.kyc.util;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.cdpg.dx.aaa.credit.models.CreditRequestAuditOperation;
import org.cdpg.dx.auditing.v2.model.UserActivityAuditLogBuilder;
import org.cdpg.dx.auditing.v2.util.AuditLogHelper;

import java.math.BigDecimal;
import java.util.UUID;

import static org.cdpg.dx.aaa.credit.util.Constants.*;

public final class KYCAuditLogHelper {

  private KYCAuditLogHelper() {
  }

  public static UserActivityAuditLogBuilder buildAudit(
    RoutingContext ctx,
    KYCAuditOperation operation
  ) {

    return AuditLogHelper.createBaseAudit(ctx)
      .withLogType("USER_ACTION")
      .withOriginServer("AAA")
      .withAction(operation.value())

      /* -------- Request / workflow -------- */
      .build();
  }

}

