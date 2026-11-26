package org.cdpg.dx.common.util;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.cdpg.dx.auth.authorization.registry.SystemRoleScopeMap;
import org.cdpg.dx.auth.model.DxRole;
import org.cdpg.dx.auth.model.Scopes;

/**
 * Scope-based helpers for deciding whether a caller has administrative visibility over assets.
 *
 * <ul>
 *   <li>{@link Scopes#ASSET_MANAGEMENT} (platform admin) - every asset on the platform.
 *   <li>{@link Scopes#ORG_ASSET_MANAGEMENT} (org admin) - every asset of the caller's own
 *       organisation.
 * </ul>
 */
public final class AssetAccessScopes {

  private static final String SCOPES_CLAIM = "scopes";

  private AssetAccessScopes() {}

  /** Scopes carried in the token principal ({@code scopes} claim); empty when absent. */
  public static List<String> fromPrincipal(JsonObject principal) {
    if (principal == null) {
      return List.of();
    }
    JsonArray scopes = principal.getJsonArray(SCOPES_CLAIM, new JsonArray());
    return fromJsonArray(scopes);
  }

  public static List<String> fromJsonArray(JsonArray scopes) {
    List<String> result = new ArrayList<>();
    if (scopes != null) {
      scopes.forEach(s -> {
        if (s != null) {
          result.add(s.toString());
        }
      });
    }
    return result;
  }

  /**
   * Scopes granted by the given system roles. For internal callers (gRPC, delegation, token
   * issuance) that only know the user's roles, not the scopes of a presented token.
   */
  public static List<String> fromRoles(Collection<String> roles) {
    Set<String> scopes = new LinkedHashSet<>();
    if (roles != null) {
      for (String role : roles) {
        DxRole.fromString(role).ifPresent(r -> scopes.addAll(SystemRoleScopeMap.getScopes(r)));
      }
    }
    return new ArrayList<>(scopes);
  }

  public static boolean hasPlatformAssetScope(Collection<String> scopes) {
    return scopes != null && scopes.contains(Scopes.ASSET_MANAGEMENT);
  }

  public static boolean hasOrgAssetScope(Collection<String> scopes) {
    return scopes != null && scopes.contains(Scopes.ORG_ASSET_MANAGEMENT);
  }

  /**
   * True when the caller administers the item: platform-wide asset scope, or org asset scope
   * and the item belongs to the caller's organisation.
   */
  public static boolean isAssetAdminFor(
      Collection<String> scopes, String itemOrganisationId, String callerOrganisationId) {
    if (hasPlatformAssetScope(scopes)) {
      return true;
    }
    return hasOrgAssetScope(scopes)
        && itemOrganisationId != null
        && Objects.equals(itemOrganisationId, callerOrganisationId);
  }
}
