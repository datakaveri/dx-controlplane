package org.cdpg.dx.common.util;

import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import java.util.Objects;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.user.service.UserService;
import org.cdpg.dx.common.exception.DxBadRequestException;

/**
 * Utility for resolving delegator identity from request context.
 *
 * <p>Many endpoints support a {@code delegatorId} query parameter that allows a delegate to act on
 * behalf of another user. This class consolidates the repeated logic for extracting the delegator
 * ID, overriding the acting user ID, and resolving the delegator's organisation.
 */
public final class DelegatorResolver {

  private static final Logger LOGGER = LogManager.getLogger(DelegatorResolver.class);
  private static final String DELEGATOR_ID_PARAM = "delegatorId";

  private DelegatorResolver() {}

  /**
   * Extracts the delegator ID from the query parameter, if present.
   *
   * @return the delegator UUID, or {@code null} if the parameter is absent
   */
  public static UUID getDelegatorId(RoutingContext ctx) {
    String delegatorStr = ctx.queryParams().get(DELEGATOR_ID_PARAM);
    return delegatorStr != null ? UUID.fromString(delegatorStr) : null;
  }

  /**
   * Returns the effective acting user ID — the delegator ID if present, otherwise the
   * authenticated user's subject.
   */
  public static UUID resolveActingUserId(RoutingContext ctx) {
    UUID delegatorId = getDelegatorId(ctx);
    if (delegatorId != null) {
      return delegatorId;
    }
    return UUID.fromString(ctx.user().subject());
  }

  /**
   * Resolves the organisation ID of the acting user, handling delegator lookups.
   *
   * <p>When no delegator is specified, the org ID is taken from the authenticated user's JWT
   * principal. When a delegator is specified, the org ID is looked up from the delegator's user
   * info.
   *
   * @param ctx the routing context
   * @param userService the user service for delegator lookups
   * @param expectedOrgIdParam the org ID path/query parameter to validate against (nullable — if
   *     null, no cross-check is performed)
   * @return a future containing the resolved organisation UUID
   */
  public static Future<UUID> resolveOrgId(
      RoutingContext ctx, UserService userService, String expectedOrgIdParam) {

    UUID delegatorId = getDelegatorId(ctx);

    if (delegatorId == null) {
      return resolveOrgIdFromPrincipal(ctx, expectedOrgIdParam);
    } else {
      return resolveOrgIdFromDelegator(userService, delegatorId, expectedOrgIdParam);
    }
  }

  private static Future<UUID> resolveOrgIdFromPrincipal(
      RoutingContext ctx, String expectedOrgIdParam) {

    String orgIdStr = ctx.user().principal().getString("organisation_id");

    if (orgIdStr == null || orgIdStr.trim().isEmpty()) {
      return Future.failedFuture(
          new DxBadRequestException(
              "The user is acting as a delegate."
                  + " Please specify the delegatorId in the request"));
    }

    if (expectedOrgIdParam != null && !Objects.equals(orgIdStr, expectedOrgIdParam)) {
      return Future.failedFuture(
          new DxBadRequestException(
              "The org id of the user and the query parameter are not same"));
    }

    return Future.succeededFuture(UUID.fromString(orgIdStr));
  }

  private static Future<UUID> resolveOrgIdFromDelegator(
      UserService userService, UUID delegatorId, String expectedOrgIdParam) {

    return userService
        .getUserInfoByID(delegatorId)
        .compose(
            res -> {
              if (res == null) {
                return Future.failedFuture(
                    new DxBadRequestException("Delegator is not valid"));
              }

              String orgIdStr = res.organisationId();

              if (orgIdStr == null || orgIdStr.trim().isEmpty()) {
                return Future.failedFuture(
                    new DxBadRequestException(
                        "Delegator is not part of any organisation"));
              }

              if (expectedOrgIdParam != null
                  && !Objects.equals(orgIdStr, expectedOrgIdParam)) {
                return Future.failedFuture(
                    new DxBadRequestException(
                        "The org id of the delegator and the query parameter are not same"));
              }

              LOGGER.info("Resolved orgId: {}", orgIdStr);
              return Future.succeededFuture(UUID.fromString(orgIdStr));
            });
  }
}
