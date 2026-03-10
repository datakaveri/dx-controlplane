package org.cdpg.dx.common.util.resolver;

import io.vertx.ext.web.RoutingContext;
import org.cdpg.dx.aaa.delegation.OrgOwnershipValidator;
import org.cdpg.dx.aaa.user.service.UserService;

import java.util.UUID;

public class DelegatorStrategyFactory {

  private final UserService userService;
  private final OrgOwnershipValidator orgOwnershipValidator;

  public DelegatorStrategyFactory(
    UserService userService,
    OrgOwnershipValidator orgOwnershipValidator) {
    this.userService = userService;
    this.orgOwnershipValidator = orgOwnershipValidator;
  }

  public DelegatorStrategy create(RoutingContext ctx) {
    String delegatorStr = ctx.queryParams().get("delegatorId");

    if (delegatorStr != null) {
      return new DelegateStrategy(
        userService,
        orgOwnershipValidator,
        UUID.fromString(delegatorStr));
    }

    return new NonDelegateStrategy();
  }
}
