package org.cdpg.dx.auth.v2.lookup.local;

import io.vertx.core.Future;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.cdpg.dx.auth.v2.lookup.UserLookup;
import org.cdpg.dx.auth.v2.model.DxRole;
import org.cdpg.dx.auth.v2.model.UserSnapshot;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

/**
 * In-process {@link UserLookup} for dx-controlplane. Wraps {@link KeycloakUserService#getUserById}
 * and maps {@link DxUser} → {@link UserSnapshot}.
 *
 * <p>Role strings from Keycloak are translated via {@link DxRole#fromKeycloakName}. Strings that
 * don't map to a v2 system role (e.g. legacy {@code delegate} variants) are silently dropped —
 * the v2 model recognises only the five system roles.
 */
public final class LocalUserLookup implements UserLookup {

  private final KeycloakUserService userService;

  public LocalUserLookup(KeycloakUserService userService) {
    this.userService = Objects.requireNonNull(userService, "userService");
  }

  @Override
  public Future<Optional<UserSnapshot>> findBySub(String sub) {
    UUID uuid;
    try {
      uuid = UUID.fromString(sub);
    } catch (IllegalArgumentException e) {
      return Future.succeededFuture(Optional.empty());
    }

    return userService
        .getUserById(uuid)
        .map(this::toSnapshot)
        .recover(err -> {
          if (err instanceof DxNotFoundException) {
            return Future.succeededFuture(Optional.empty());
          }
          return Future.failedFuture(err);
        });
  }

  private Optional<UserSnapshot> toSnapshot(DxUser user) {
    if (user == null) {
      return Optional.empty();
    }
    boolean enabled = user.account_enabled() == null || user.account_enabled();
    return Optional.of(
        new UserSnapshot(
            user.sub() != null ? user.sub().toString() : null,
            user.organisationId(),
            mapRoles(user.roles()),
            !enabled));
  }

  private static Set<DxRole> mapRoles(List<String> legacyRoleNames) {
    if (legacyRoleNames == null || legacyRoleNames.isEmpty()) {
      return Set.of();
    }
    Set<DxRole> out = new HashSet<>();
    for (String name : legacyRoleNames) {
      DxRole.fromKeycloakName(name).ifPresent(out::add);
    }
    return out;
  }
}