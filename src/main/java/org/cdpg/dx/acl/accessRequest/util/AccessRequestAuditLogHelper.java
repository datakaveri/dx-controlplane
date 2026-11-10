package org.cdpg.dx.acl.accessRequest.util;

import io.vertx.ext.web.RoutingContext;
import java.util.UUID;
import org.cdpg.dx.acl.accessRequest.dao.model.AccessRequestDto;
import org.cdpg.dx.acl.accessRequest.model.AccessRequestAuditOperation;
import org.cdpg.dx.auditing.v2.model.UserActivityAuditLogBuilder;
import org.cdpg.dx.auditing.v2.util.AuditLogHelper;

public final class AccessRequestAuditLogHelper {

  private AccessRequestAuditLogHelper() {
  }

  public static UserActivityAuditLogBuilder buildAudit(
      RoutingContext ctx,
      AccessRequestDto dto,
      AccessRequestAuditOperation operation) {

    return AuditLogHelper.createBaseAudit(ctx)
        .withLogType("ASSET")
        .withOriginServer("ACL_APD")
        .withAction(operation.value())
        .withAssetId(safeUuid(dto.getItemId()))
        .withAssetName(dto.getAssetName())
        .withAssetType(dto.getAssetType())
        .withRequestId(UUID.fromString(dto.getRequestId()))
        .withAssetShortDescription(dto.getShortDescription())
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

