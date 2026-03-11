package org.cdpg.dx.common.util.resolver;

import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.delegation.OrgOwnershipValidator;
import org.cdpg.dx.aaa.user.service.UserService;
import org.cdpg.dx.common.exception.DxBadRequestException;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class DelegateStrategy implements DelegatorStrategy {

  private static final Logger LOGGER = LogManager.getLogger(DelegateStrategy.class);


  private final UserService userService;
  private final OrgOwnershipValidator orgOwnershipValidator;
  private final UUID delegatorId;

  public DelegateStrategy(
    UserService userService,
    OrgOwnershipValidator orgOwnershipValidator,
    UUID delegatorId) {
    this.userService = userService;
    this.orgOwnershipValidator = orgOwnershipValidator;
    this.delegatorId = delegatorId;
  }

  @Override
  public Future<UUID> resolveUserId(RoutingContext ctx) {
    return Future.succeededFuture(delegatorId);
  }

  @Override
  public Future<UUID> resolveOrgId(RoutingContext ctx, String orgIdParam) {
    return userService
      .getUserInfoByID(delegatorId)
      .compose(res -> {

        if (res == null) {
          return Future.failedFuture(
            new DxBadRequestException("Delegator is not valid"));
        }

        String orgIdStr = res.organisationId();
        if (orgIdStr == null || orgIdStr.isBlank()) {
          return Future.failedFuture(
            new DxBadRequestException("Delegator is not part of any organisation"));
        }

        LOGGER.info("orgIdParam is:{}" ,orgIdParam);
        LOGGER.info("orgIdStr is:{}" ,orgIdStr);

        if (!Objects.equals(orgIdStr, orgIdParam)) {
          return Future.failedFuture(
            new DxBadRequestException(
              "The org id of the delegator and the query parameter are not same"));
        }

        return orgOwnershipValidator
          .validateOrgOwnership(delegatorId, List.of(orgIdParam))
          .map(v -> UUID.fromString(orgIdStr));
      });
  }
}
