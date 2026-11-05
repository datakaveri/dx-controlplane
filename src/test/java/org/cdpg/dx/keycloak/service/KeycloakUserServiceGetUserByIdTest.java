package org.cdpg.dx.keycloak.service;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import jakarta.ws.rs.NotFoundException;
import java.lang.reflect.Method;
import java.util.UUID;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.exception.KeycloakServiceException;
import org.cdpg.dx.common.util.BlockingExecutionUtil;
import org.cdpg.dx.keycloak.client.KeycloakClientProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.mockito.MockedStatic;

@ExtendWith(VertxExtension.class)
class KeycloakUserServiceGetUserByIdTest {
  private Keycloak keycloak;
  private KeycloakUserServiceImpl service;

  @BeforeEach
  void setUp(Vertx vertx) throws Exception {
    // initialize() is a no-op once set, so drop the executor bound to the previous test's Vertx.
    Method reset = BlockingExecutionUtil.class.getDeclaredMethod("reset");
    reset.setAccessible(true);
    reset.invoke(null);
    BlockingExecutionUtil.initialize(vertx);
    keycloak = mock(Keycloak.class, RETURNS_DEEP_STUBS);
    JsonObject config = new JsonObject().put("keycloakRealm", "realm");
    try (MockedStatic<KeycloakClientProvider> provider =
        mockStatic(KeycloakClientProvider.class)) {
      provider.when(() -> KeycloakClientProvider.getInstance(any())).thenReturn(keycloak);
      service = new KeycloakUserServiceImpl(config);
    }
  }

  private void lookupThrows(RuntimeException failure) {
    when(keycloak.realm(anyString()).users().get(anyString()).toRepresentation())
        .thenThrow(failure);
  }

  @Test
  void missingUserFailsAsNotFound(VertxTestContext ctx) {
    lookupThrows(new NotFoundException());

    service
        .getUserById(UUID.randomUUID())
        .onComplete(
            ctx.failing(
                err ->
                    ctx.verify(
                        () -> {
                          assertInstanceOf(DxNotFoundException.class, err);
                          ctx.completeNow();
                        })));
  }

  @Test
  void otherFailuresAreNotReportedAsMissingUser(VertxTestContext ctx) {
    lookupThrows(new RuntimeException("connection refused"));

    service
        .getUserById(UUID.randomUUID())
        .onComplete(
            ctx.failing(
                err ->
                    ctx.verify(
                        () -> {
                          assertInstanceOf(KeycloakServiceException.class, err);
                          ctx.completeNow();
                        })));
  }
}
