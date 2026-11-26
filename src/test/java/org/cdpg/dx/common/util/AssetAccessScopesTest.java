package org.cdpg.dx.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.List;
import org.cdpg.dx.auth.model.Scopes;
import org.junit.jupiter.api.Test;

class AssetAccessScopesTest {

  @Test
  void platformScopeAdministersAnyOrganisation() {
    assertThat(AssetAccessScopes.isAssetAdminFor(List.of(Scopes.ASSET_MANAGEMENT), "org-a", null))
        .isTrue();
  }

  @Test
  void orgScopeAdministersOnlyOwnOrganisation() {
    List<String> scopes = List.of(Scopes.ORG_ASSET_MANAGEMENT);
    assertThat(AssetAccessScopes.isAssetAdminFor(scopes, "org-a", "org-a")).isTrue();
    assertThat(AssetAccessScopes.isAssetAdminFor(scopes, "org-a", "org-b")).isFalse();
    assertThat(AssetAccessScopes.isAssetAdminFor(scopes, null, null)).isFalse();
  }

  @Test
  void ownAssetScopeIsNotAdmin() {
    assertThat(
            AssetAccessScopes.isAssetAdminFor(
                List.of(Scopes.OWN_ASSET_MANAGEMENT), "org-a", "org-a"))
        .isFalse();
    assertThat(AssetAccessScopes.isAssetAdminFor(null, "org-a", "org-a")).isFalse();
  }

  @Test
  void readsScopesClaimFromPrincipal() {
    JsonObject principal =
        new JsonObject().put("scopes", new JsonArray().add(Scopes.ORG_ASSET_MANAGEMENT));
    assertThat(AssetAccessScopes.fromPrincipal(principal))
        .containsExactly(Scopes.ORG_ASSET_MANAGEMENT);
    assertThat(AssetAccessScopes.fromPrincipal(new JsonObject())).isEmpty();
    assertThat(AssetAccessScopes.fromPrincipal(null)).isEmpty();
  }

  @Test
  void derivesScopesFromRoles() {
    assertThat(AssetAccessScopes.fromRoles(List.of("cos_admin")))
        .contains(Scopes.ASSET_MANAGEMENT);
    assertThat(AssetAccessScopes.fromRoles(List.of("org_admin")))
        .contains(Scopes.ORG_ASSET_MANAGEMENT)
        .doesNotContain(Scopes.ASSET_MANAGEMENT);
    assertThat(AssetAccessScopes.fromRoles(List.of("provider")))
        .doesNotContain(Scopes.ASSET_MANAGEMENT, Scopes.ORG_ASSET_MANAGEMENT);
    assertThat(AssetAccessScopes.fromRoles(null)).isEmpty();
  }
}
