package org.cdpg.dx.auth.authentication.lookup.local;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.Optional;
import org.cdpg.dx.aaa.delegation.service.DelegationService;
import org.cdpg.dx.auth.model.DelegationRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("LocalDelegationLookup Tests")
class LocalDelegationLookupTest {

  private static JsonObject grant(String delegator, String delegatee, String status, String... scopes) {
    JsonArray constraints = new JsonArray();
    for (String s : scopes) {
      constraints.add(new JsonObject().put("scope", s).put("entity_id", "*").put("entity_type", "*"));
    }
    return new JsonObject()
        .put("delegation_id", "deleg-1")
        .put("delegator_id", delegator)
        .put("delegate_id", delegatee)
        .put("status", status)
        .put("constraints", constraints);
  }

  @Nested
  @DisplayName("Happy path")
  class Happy {

    @Test
    @DisplayName("single matching active delegation — scopes extracted from constraints")
    void singleMatch() {
      DelegationService svc = mock(DelegationService.class);
      when(svc.getAllDelegationsOfDelegate("bob"))
          .thenReturn(
              Future.succeededFuture(List.of(grant("alice", "bob", "active", "data-access"))));

      Optional<DelegationRecord> r =
          new LocalDelegationLookup(svc).findActive("alice", "bob").result();

      assertTrue(r.isPresent());
      DelegationRecord d = r.get();
      assertEquals("alice", d.delegatorSub());
      assertEquals("bob", d.delegateeSub());
      assertEquals(java.util.Set.of("data-access"), d.scopes());
      assertTrue(d.active());
      assertFalse(d.fullDelegation());
    }

    @Test
    @DisplayName("picks the grant whose delegator_id matches — ignores unrelated grants")
    void filtersOutWrongDelegator() {
      DelegationService svc = mock(DelegationService.class);
      when(svc.getAllDelegationsOfDelegate("bob"))
          .thenReturn(
              Future.succeededFuture(
                  List.of(
                      grant("carol", "bob", "active", "asset-publish"),
                      grant("alice", "bob", "active", "data-access"))));

      DelegationRecord d =
          new LocalDelegationLookup(svc).findActive("alice", "bob").result().get();
      assertEquals(java.util.Set.of("data-access"), d.scopes());
    }

    @Test
    @DisplayName("multiple scopes → all collected")
    void multipleScopes() {
      DelegationService svc = mock(DelegationService.class);
      when(svc.getAllDelegationsOfDelegate("bob"))
          .thenReturn(
              Future.succeededFuture(
                  List.of(grant("alice", "bob", "active", "data-access", "asset-publish"))));

      DelegationRecord d =
          new LocalDelegationLookup(svc).findActive("alice", "bob").result().get();
      assertEquals(java.util.Set.of("data-access", "asset-publish"), d.scopes());
    }

    @Test
    @DisplayName("wildcard scope '*' sets fullDelegation=true — delegatee gets all of delegator's current scopes")
    void wildcardMeansFullDelegation() {
      DelegationService svc = mock(DelegationService.class);
      when(svc.getAllDelegationsOfDelegate("bob"))
          .thenReturn(Future.succeededFuture(List.of(grant("alice", "bob", "active", "*"))));

      DelegationRecord d =
          new LocalDelegationLookup(svc).findActive("alice", "bob").result().get();
      assertTrue(d.scopes().isEmpty());
      assertTrue(d.fullDelegation());
    }
  }

  @Nested
  @DisplayName("Empty / no-match")
  class NoMatch {

    @Test
    @DisplayName("service returns empty list → Optional.empty")
    void emptyList() {
      DelegationService svc = mock(DelegationService.class);
      when(svc.getAllDelegationsOfDelegate("bob"))
          .thenReturn(Future.succeededFuture(List.of()));

      Optional<DelegationRecord> r =
          new LocalDelegationLookup(svc).findActive("alice", "bob").result();
      assertTrue(r.isEmpty());
    }

    @Test
    @DisplayName("no grant matches delegator → Optional.empty")
    void noDelegatorMatch() {
      DelegationService svc = mock(DelegationService.class);
      when(svc.getAllDelegationsOfDelegate("bob"))
          .thenReturn(
              Future.succeededFuture(List.of(grant("carol", "bob", "active", "data-access"))));

      assertTrue(new LocalDelegationLookup(svc).findActive("alice", "bob").result().isEmpty());
    }

    @Test
    @DisplayName("matching delegator but inactive status → Optional.empty")
    void inactiveStatus() {
      DelegationService svc = mock(DelegationService.class);
      when(svc.getAllDelegationsOfDelegate("bob"))
          .thenReturn(
              Future.succeededFuture(List.of(grant("alice", "bob", "revoked", "data-access"))));

      assertTrue(new LocalDelegationLookup(svc).findActive("alice", "bob").result().isEmpty());
    }

    @Test
    @DisplayName("null inputs → Optional.empty without service call")
    void nullInputs() {
      DelegationService svc = mock(DelegationService.class);
      assertTrue(new LocalDelegationLookup(svc).findActive(null, "bob").result().isEmpty());
      assertTrue(new LocalDelegationLookup(svc).findActive("alice", null).result().isEmpty());
      verifyNoInteractions(svc);
    }
  }

  @Nested
  @DisplayName("Transport failures")
  class Transport {

    @Test
    @DisplayName("service failure propagates")
    void serviceFailure() {
      DelegationService svc = mock(DelegationService.class);
      RuntimeException boom = new RuntimeException("db down");
      when(svc.getAllDelegationsOfDelegate("bob")).thenReturn(Future.failedFuture(boom));

      Future<Optional<DelegationRecord>> f =
          new LocalDelegationLookup(svc).findActive("alice", "bob");
      assertTrue(f.failed());
      assertSame(boom, f.cause());
    }
  }
}