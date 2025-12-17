package org.cdpg.dx.aaa.organization.orchestration;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.delegation.service.DelegationService;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.aaa.user.service.UserService;
import org.cdpg.dx.auth.authorization.model.DxScope;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

import java.time.LocalDateTime;
import java.util.UUID;

// todo:  need to refactor this class according to new delegation model this is just a stub for now

public class OrganizationAccessOrchestrator {

  private final OrganizationService organizationService;
  private final DelegationService delegationService;
  private final UserService userService;
  private final KeycloakUserService keycloakUserService;

  public OrganizationAccessOrchestrator(
      OrganizationService organizationService,
      DelegationService delegationService,
      UserService userService,
      KeycloakUserService keycloakUserService) {
    this.organizationService = organizationService;
    this.delegationService = delegationService;
    this.userService = userService;
    this.keycloakUserService = keycloakUserService;
  }

  public Future<Void> assertOrgManagementAccess(
      UUID requesterId, UUID orgId, DxScope requiredScope) {

    return userService
        .getUserInfoByID(requesterId)
        .compose(
            dxUser -> {
              if (dxUser.roles().contains("org_admin") || dxUser.roles().contains("cos_admin")) {
                return Future.succeededFuture();
              }

              if (!dxUser.roles().contains("delegate")) {
                return Future.failedFuture(new DxForbiddenException("User has no org access"));
              }

              UUID delegatorId = UUID.fromString(dxUser.did());

              return delegationService
                  .getDelegationScopeByEntityId(orgId)
                  .compose(
                      scopes -> {
                        boolean allowed =
                            scopes.stream()
                                .anyMatch(
                                    s ->
                                        s.scope().equalsIgnoreCase(requiredScope.getScope())
                                            && s.entityId().equals(orgId)
                                            && LocalDateTime.now().isBefore(s.expiryAt()));

                        if (!allowed) {
                          return Future.failedFuture(
                              new DxForbiddenException("Delegation invalid or expired"));
                        }

                        return Future.succeededFuture();
                      });
            });
  }
}
