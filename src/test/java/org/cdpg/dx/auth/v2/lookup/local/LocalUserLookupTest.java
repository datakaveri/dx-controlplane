package org.cdpg.dx.auth.v2.lookup.local;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.cdpg.dx.auth.v2.model.DxRole;
import org.cdpg.dx.auth.v2.model.UserSnapshot;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LocalUserLookup Tests")
class LocalUserLookupTest {

  private static DxUser user(UUID sub, String orgId, List<String> roles, Boolean enabled) {
    return new DxUser(
        roles,
        orgId,
        "org-name",
        sub,
        true,
        true,
        "name",
        "pref",
        "given",
        "family",
        "email@x.com",
        List.of(),
        new JsonObject(),
        null,
        new JsonObject(),
        null,
        null,
        null,
        enabled,
        null,
        null,
        new JsonArray(),
        null,
        null);
  }

  @Test
  @DisplayName("maps DxUser → UserSnapshot, translates legacy role strings")
  void happyPath() {
    KeycloakUserService svc = mock(KeycloakUserService.class);
    UUID sub = UUID.randomUUID();
    when(svc.getUserById(sub))
        .thenReturn(Future.succeededFuture(user(sub, "org-a", List.of("org_admin", "consumer"), true)));

    LocalUserLookup lookup = new LocalUserLookup(svc);
    Optional<UserSnapshot> result = lookup.findBySub(sub.toString()).result();

    assertTrue(result.isPresent());
    UserSnapshot s = result.get();
    assertEquals(sub.toString(), s.sub());
    assertEquals("org-a", s.organisationId());
    assertEquals(Set.of(DxRole.ORG_ADMIN, DxRole.CONSUMER), s.roles());
    assertFalse(s.disabled());
  }

  @Test
  @DisplayName("account_enabled=false → disabled=true")
  void disabledMapping() {
    KeycloakUserService svc = mock(KeycloakUserService.class);
    UUID sub = UUID.randomUUID();
    when(svc.getUserById(sub))
        .thenReturn(Future.succeededFuture(user(sub, "org-a", List.of(), false)));

    Optional<UserSnapshot> result = new LocalUserLookup(svc).findBySub(sub.toString()).result();
    assertTrue(result.get().disabled());
  }

  @Test
  @DisplayName("account_enabled=null → disabled=false (treat unset as enabled)")
  void nullEnabledTreatedAsEnabled() {
    KeycloakUserService svc = mock(KeycloakUserService.class);
    UUID sub = UUID.randomUUID();
    when(svc.getUserById(sub))
        .thenReturn(Future.succeededFuture(user(sub, "org-a", List.of(), null)));

    assertFalse(new LocalUserLookup(svc).findBySub(sub.toString()).result().get().disabled());
  }

  @Test
  @DisplayName("unknown legacy role names are dropped silently")
  void unknownRolesDropped() {
    KeycloakUserService svc = mock(KeycloakUserService.class);
    UUID sub = UUID.randomUUID();
    when(svc.getUserById(sub))
        .thenReturn(
            Future.succeededFuture(
                user(sub, "org-a", List.of("delegate", "consumerDelegate", "consumer"), true)));

    UserSnapshot s = new LocalUserLookup(svc).findBySub(sub.toString()).result().get();
    assertEquals(Set.of(DxRole.CONSUMER), s.roles());
  }

  @Test
  @DisplayName("non-UUID sub → Optional.empty without calling service")
  void invalidUuidShortCircuits() {
    KeycloakUserService svc = mock(KeycloakUserService.class);
    Optional<UserSnapshot> result = new LocalUserLookup(svc).findBySub("not-a-uuid").result();
    assertTrue(result.isEmpty());
    verifyNoInteractions(svc);
  }

  @Test
  @DisplayName("DxNotFoundException from service → Optional.empty")
  void notFoundBecomesEmpty() {
    KeycloakUserService svc = mock(KeycloakUserService.class);
    UUID sub = UUID.randomUUID();
    when(svc.getUserById(sub))
        .thenReturn(Future.failedFuture(new DxNotFoundException("no such user")));

    Optional<UserSnapshot> result = new LocalUserLookup(svc).findBySub(sub.toString()).result();
    assertTrue(result.isEmpty());
  }

  @Test
  @DisplayName("other service failures propagate as failed Future")
  void otherErrorPropagates() {
    KeycloakUserService svc = mock(KeycloakUserService.class);
    UUID sub = UUID.randomUUID();
    RuntimeException boom = new RuntimeException("keycloak down");
    when(svc.getUserById(sub)).thenReturn(Future.failedFuture(boom));

    Future<Optional<UserSnapshot>> f = new LocalUserLookup(svc).findBySub(sub.toString());
    assertTrue(f.failed());
    assertSame(boom, f.cause());
  }
}