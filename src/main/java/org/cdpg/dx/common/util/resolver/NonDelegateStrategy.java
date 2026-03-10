package org.cdpg.dx.common.util.resolver;

import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import org.cdpg.dx.common.exception.DxBadRequestException;

import java.util.Objects;
import java.util.UUID;

public class NonDelegateStrategy implements DelegatorStrategy {

  @Override
  public Future<UUID> resolveUserId(RoutingContext ctx) {
    return Future.succeededFuture(
      UUID.fromString(ctx.user().subject()));
  }

  @Override
  public Future<UUID> resolveOrgId(RoutingContext ctx, String orgIdParam) {
    String orgIdStr = ctx.user().principal().getString("organisation_id");

    if (orgIdStr == null || orgIdStr.isBlank()) {
      return Future.failedFuture(
        new DxBadRequestException(
          "The user is acting as a delegate. Please specify the delegatorId in the request"));
    }

    if (!Objects.equals(orgIdStr, orgIdParam)) {
      return Future.failedFuture(
        new DxBadRequestException(
          "The org id of the user and the query parameter are not same"));
    }

    return Future.succeededFuture(UUID.fromString(orgIdStr));
  }
}
