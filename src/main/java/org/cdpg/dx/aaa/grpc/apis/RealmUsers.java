package org.cdpg.dx.aaa.grpc.apis;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.cdpg.dx.apis.user.v1.User;
import org.cdpg.dx.common.util.BlockingExecutionUtil;
import org.cdpg.dx.keycloak.client.KeycloakClientProvider;
import org.cdpg.dx.keycloak.config.KeycloakConstants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

/**
 * The Keycloak reads and role changes behind {@link UserApiService}, written to the shared
 * contract's rules so the Java and Go servers agree: roles are the realm roles assigned directly
 * (not composites), without Keycloak's defaults (default-roles-*, offline_access,
 * uma_authorization), sorted.
 *
 * <p>It uses the process-wide Keycloak admin client ({@link KeycloakClientProvider}) and leaves
 * {@code KeycloakUserService} as it is.
 */
class RealmUsers {

  private static final Set<String> KEYCLOAK_DEFAULT_ROLES =
      Set.of("offline_access", "uma_authorization");

  private final Keycloak keycloak;
  private final String realm;

  RealmUsers(JsonObject config) {
    this.keycloak = KeycloakClientProvider.getInstance(config);
    this.realm = config.getString("keycloakRealm");
  }

  /** The user, or a failure whose cause is jakarta.ws.rs.NotFoundException. */
  Future<User> get(String userId) {
    return BlockingExecutionUtil.runBlocking(() -> load(userId));
  }

  /** Assigns the roles the user lacks; fails with IllegalArgumentException for an unknown role. */
  Future<List<String>> grant(String userId, Collection<String> roles) {
    return BlockingExecutionUtil.runBlocking(
        () -> {
          RoleMappingResource mappings = keycloak.realm(realm).users().get(userId).roles();
          List<String> held = directRoles(mappings);
          List<RoleRepresentation> add =
              roles.stream()
                  .filter(r -> !held.contains(r))
                  .distinct()
                  .map(this::realmRole)
                  .toList();
          if (!add.isEmpty()) {
            mappings.realmLevel().add(add);
          }
          return directRoles(mappings);
        });
  }

  /** Removes the roles the user holds; fails with IllegalArgumentException for an unknown role. */
  Future<List<String>> revoke(String userId, Collection<String> roles) {
    return BlockingExecutionUtil.runBlocking(
        () -> {
          RoleMappingResource mappings = keycloak.realm(realm).users().get(userId).roles();
          List<String> held = directRoles(mappings);
          List<RoleRepresentation> remove =
              roles.stream()
                  .distinct()
                  .map(this::realmRole) // validates every role, held or not
                  .filter(r -> held.contains(r.getName()))
                  .toList();
          if (!remove.isEmpty()) {
            mappings.realmLevel().remove(remove);
          }
          return directRoles(mappings);
        });
  }

  private User load(String userId) {
    var resource = keycloak.realm(realm).users().get(userId);
    UserRepresentation u = resource.toRepresentation();
    return User.newBuilder()
        .setId(nz(u.getId()))
        .setUsername(nz(u.getUsername()))
        .setEmail(nz(u.getEmail()))
        .setFirstName(nz(u.getFirstName()))
        .setLastName(nz(u.getLastName()))
        .addAllRoles(directRoles(resource.roles()))
        .setOrganisationId(attr(u.getAttributes(), KeycloakConstants.ORGANISATION_ID))
        .setOrganisationName(attr(u.getAttributes(), KeycloakConstants.ORGANISATION_NAME))
        .setEnabled(Boolean.TRUE.equals(u.isEnabled()))
        .build();
  }

  private List<String> directRoles(RoleMappingResource mappings) {
    // Keycloak's composite default role is "default-roles-<realm name at creation>", which
    // need not match keycloakRealm (e.g. an imported or renamed realm), so match the prefix.
    return mappings.realmLevel().listAll().stream()
        .map(RoleRepresentation::getName)
        .filter(n -> !n.startsWith("default-roles-") && !KEYCLOAK_DEFAULT_ROLES.contains(n))
        .sorted()
        .toList();
  }

  private RoleRepresentation realmRole(String name) {
    try {
      return keycloak.realm(realm).roles().get(name).toRepresentation();
    } catch (jakarta.ws.rs.NotFoundException e) {
      throw new IllegalArgumentException("unknown role: " + name);
    }
  }

  private static String attr(Map<String, List<String>> attrs, String key) {
    if (attrs == null || attrs.get(key) == null || attrs.get(key).isEmpty()) {
      return "";
    }
    return nz(attrs.get(key).get(0));
  }

  private static String nz(String s) {
    return s == null ? "" : s;
  }
}
