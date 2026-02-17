package org.cdpg.dx.aaa.asset.util;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.cdpg.dx.aaa.kyc.util.KYCAuditOperation;
import org.cdpg.dx.auditing.v2.model.UserActivityAuditLogBuilder;
import org.cdpg.dx.auditing.v2.util.AuditLogHelper;

import java.util.UUID;

import static org.cdpg.dx.aaa.asset.util.Constants.ASSET_ID;
import static org.cdpg.dx.aaa.asset.util.Constants.ASSET_REQUEST_ID;
import static org.cdpg.dx.aaa.credit.util.Constants.CREDIT_REQUEST_ID;

public final class AssetAuthAuditLogHelper {

  private AssetAuthAuditLogHelper() {
  }

  public static UserActivityAuditLogBuilder buildAudit(
    RoutingContext ctx,
    JsonObject body,
    AssetAuthAuditOperation operation
  ) {

    return AuditLogHelper.createBaseAudit(ctx)
      .withLogType("USER_ACTION")
      .withOriginServer("AAA")
      .withAction(operation.value())
      .withRequestId(
        body != null && body.getString(ASSET_REQUEST_ID) != null
          ? safeUuid(body.getString(ASSET_REQUEST_ID))
          : null)

      /* -------- Request / workflow -------- */
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

