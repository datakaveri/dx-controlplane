package org.cdpg.dx.auth.authorization.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import java.util.List;
import org.cdpg.dx.auth.authorization.model.DxRole;
import org.cdpg.dx.auth.authorization.model.DxScope;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.exception.DxUnauthorizedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthorizationHandlerTest {

  @Mock private RoutingContext ctx;

  @Mock private User user;

  @Nested
  @DisplayName("forRoles")
  class ForRolesTests {

    @Test
    @DisplayName("should call ctx.next() when user has the required role")
    void userHasRequiredRole_callsNext() {
      JsonObject principal =
          new JsonObject()
              .put(
                  "realm_access",
                  new JsonObject().put("roles", new JsonArray().add("consumer")));

      when(ctx.user()).thenReturn(user);
      when(user.principal()).thenReturn(principal);

      Handler<RoutingContext> handler = AuthorizationHandler.forRoles(DxRole.CONSUMER);
      handler.handle(ctx);

      verify(ctx).next();
      verify(ctx, never()).fail(any(Throwable.class));
    }

    @Test
    @DisplayName("should call ctx.fail() with DxForbiddenException when user lacks required role")
    void userMissingRole_callsFailWith403() {
      JsonObject principal =
          new JsonObject()
              .put(
                  "realm_access",
                  new JsonObject().put("roles", new JsonArray().add("consumer")));

      when(ctx.user()).thenReturn(user);
      when(user.principal()).thenReturn(principal);

      Handler<RoutingContext> handler = AuthorizationHandler.forRoles(DxRole.PROVIDER);
      handler.handle(ctx);

      ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
      verify(ctx).fail(captor.capture());
      assertThat(captor.getValue()).isInstanceOf(DxForbiddenException.class);
      assertThat(captor.getValue().getMessage())
          .isEqualTo("User does not have the required role.");
      verify(ctx, never()).next();
    }

    @Test
    @DisplayName("should call ctx.fail() with DxUnauthorizedException when user is null")
    void userNotAuthenticated_callsFailWith401() {
      when(ctx.user()).thenReturn(null);

      Handler<RoutingContext> handler = AuthorizationHandler.forRoles(DxRole.CONSUMER);
      handler.handle(ctx);

      ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
      verify(ctx).fail(captor.capture());
      assertThat(captor.getValue()).isInstanceOf(DxUnauthorizedException.class);
      assertThat(captor.getValue().getMessage()).isEqualTo("User not authenticated.");
      verify(ctx, never()).next();
    }

    @Test
    @DisplayName(
        "should call ctx.next() when multiple roles are allowed and user has at least one")
    void multipleAllowedRoles_userHasOne_callsNext() {
      JsonObject principal =
          new JsonObject()
              .put(
                  "realm_access",
                  new JsonObject().put("roles", new JsonArray().add("provider")));

      when(ctx.user()).thenReturn(user);
      when(user.principal()).thenReturn(principal);

      Handler<RoutingContext> handler =
          AuthorizationHandler.forRoles(DxRole.CONSUMER, DxRole.PROVIDER, DxRole.COS_ADMIN);
      handler.handle(ctx);

      verify(ctx).next();
      verify(ctx, never()).fail(any(Throwable.class));
    }

    @Test
    @DisplayName("should store matched roles in context when role matches")
    @SuppressWarnings("unchecked")
    void userHasRole_storesMatchedRolesInContext() {
      JsonObject principal =
          new JsonObject()
              .put(
                  "realm_access",
                  new JsonObject()
                      .put("roles", new JsonArray().add("consumer").add("provider")));

      when(ctx.user()).thenReturn(user);
      when(user.principal()).thenReturn(principal);
      when(ctx.put(any(String.class), any())).thenReturn(ctx);

      Handler<RoutingContext> handler =
          AuthorizationHandler.forRoles(DxRole.CONSUMER, DxRole.PROVIDER);
      handler.handle(ctx);

      ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
      verify(ctx).put(keyCaptor.capture(), valueCaptor.capture());

      assertThat(keyCaptor.getValue()).isEqualTo("allowedRoles");
      assertThat((List<String>) valueCaptor.getValue()).containsExactlyInAnyOrder("consumer", "provider");
      verify(ctx).next();
    }

    @Test
    @DisplayName("should fail with DxForbiddenException when realm_access is missing")
    void noRealmAccess_callsFailWith403() {
      JsonObject principal = new JsonObject();

      when(ctx.user()).thenReturn(user);
      when(user.principal()).thenReturn(principal);

      Handler<RoutingContext> handler = AuthorizationHandler.forRoles(DxRole.CONSUMER);
      handler.handle(ctx);

      ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
      verify(ctx).fail(captor.capture());
      assertThat(captor.getValue()).isInstanceOf(DxForbiddenException.class);
      assertThat(captor.getValue().getMessage()).isEqualTo("No roles assigned to the user.");
      verify(ctx, never()).next();
    }

    @Test
    @DisplayName(
        "should fail with GPU upgrade message when only COMPUTE role is required and user lacks it")
    void computeOnlyRole_userLacks_failsWithGpuMessage() {
      JsonObject principal =
          new JsonObject()
              .put(
                  "realm_access",
                  new JsonObject().put("roles", new JsonArray().add("consumer")));

      when(ctx.user()).thenReturn(user);
      when(user.principal()).thenReturn(principal);

      Handler<RoutingContext> handler = AuthorizationHandler.forRoles(DxRole.COMPUTE);
      handler.handle(ctx);

      ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
      verify(ctx).fail(captor.capture());
      assertThat(captor.getValue()).isInstanceOf(DxForbiddenException.class);
      assertThat(captor.getValue().getMessage())
          .isEqualTo("Please upgrade your role to access GPU-based compute.");
      verify(ctx, never()).next();
    }
  }

  @Nested
  @DisplayName("forDelegationScopes")
  class ForDelegationScopesTests {

    @Test
    @DisplayName("should call ctx.next() when delegate user has the required scope")
    void delegateUserHasScope_callsNext() {
      // A delegate user: realm_access.roles only contains "delegate"
      JsonObject principal =
          new JsonObject()
              .put(
                  "realm_access",
                  new JsonObject().put("roles", new JsonArray().add("delegate")))
              .put("delegation_scope", new JsonArray().add("data_access"));

      when(ctx.user()).thenReturn(user);
      when(user.principal()).thenReturn(principal);

      Handler<RoutingContext> handler =
          AuthorizationHandler.forDelegationScopes(DxScope.DATA_ACCESS);
      handler.handle(ctx);

      verify(ctx).next();
      verify(ctx, never()).fail(any(Throwable.class));
    }

    @Test
    @DisplayName(
        "should skip delegation scope check and call ctx.next() for primary (non-delegate) user")
    void primaryUser_skipsCheckAndCallsNext() {
      // A primary user has a non-delegate role in realm_access
      JsonObject principal =
          new JsonObject()
              .put(
                  "realm_access",
                  new JsonObject().put("roles", new JsonArray().add("consumer")));

      when(ctx.user()).thenReturn(user);
      when(user.principal()).thenReturn(principal);

      Handler<RoutingContext> handler =
          AuthorizationHandler.forDelegationScopes(DxScope.DATA_ACCESS);
      handler.handle(ctx);

      verify(ctx).next();
      verify(ctx, never()).fail(any(Throwable.class));
    }

    @Test
    @DisplayName(
        "should call ctx.fail() with DxForbiddenException when delegate user lacks required scope")
    void delegateMissingScope_callsFailWith403() {
      JsonObject principal =
          new JsonObject()
              .put(
                  "realm_access",
                  new JsonObject().put("roles", new JsonArray().add("delegate")))
              .put("delegation_scope", new JsonArray().add("user_management"));

      when(ctx.user()).thenReturn(user);
      when(user.principal()).thenReturn(principal);

      Handler<RoutingContext> handler =
          AuthorizationHandler.forDelegationScopes(DxScope.DATA_ACCESS);
      handler.handle(ctx);

      ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
      verify(ctx).fail(captor.capture());
      assertThat(captor.getValue()).isInstanceOf(DxForbiddenException.class);
      assertThat(captor.getValue().getMessage())
          .isEqualTo("User does not have the required scope.");
      verify(ctx, never()).next();
    }

    @Test
    @DisplayName(
        "should call ctx.fail() with DxForbiddenException when delegate has no delegation_scope claim")
    void delegateNoDelegationScope_callsFailWith403() {
      // Delegate user with no delegation_scope field at all
      JsonObject principal =
          new JsonObject()
              .put(
                  "realm_access",
                  new JsonObject().put("roles", new JsonArray().add("delegate")));

      when(ctx.user()).thenReturn(user);
      when(user.principal()).thenReturn(principal);

      Handler<RoutingContext> handler =
          AuthorizationHandler.forDelegationScopes(DxScope.DATA_ACCESS);
      handler.handle(ctx);

      ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
      verify(ctx).fail(captor.capture());
      assertThat(captor.getValue()).isInstanceOf(DxForbiddenException.class);
      assertThat(captor.getValue().getMessage())
          .isEqualTo("No delegation scope assigned to the user.");
      verify(ctx, never()).next();
    }

    @Test
    @DisplayName("should call ctx.fail() with DxUnauthorizedException when user is null")
    void userNotAuthenticated_callsFailWith401() {
      when(ctx.user()).thenReturn(null);

      Handler<RoutingContext> handler =
          AuthorizationHandler.forDelegationScopes(DxScope.DATA_ACCESS);
      handler.handle(ctx);

      ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
      verify(ctx).fail(captor.capture());
      assertThat(captor.getValue()).isInstanceOf(DxUnauthorizedException.class);
      verify(ctx, never()).next();
    }

    @Test
    @DisplayName("should store matched scopes in context when delegation scope matches")
    @SuppressWarnings("unchecked")
    void delegateHasScope_storesMatchedScopesInContext() {
      JsonObject principal =
          new JsonObject()
              .put(
                  "realm_access",
                  new JsonObject().put("roles", new JsonArray().add("delegate")))
              .put(
                  "delegation_scope",
                  new JsonArray().add("data_access").add("user_management"));

      when(ctx.user()).thenReturn(user);
      when(user.principal()).thenReturn(principal);
      when(ctx.put(any(String.class), any())).thenReturn(ctx);

      Handler<RoutingContext> handler =
          AuthorizationHandler.forDelegationScopes(
              DxScope.DATA_ACCESS, DxScope.USER_MANAGEMENT);
      handler.handle(ctx);

      ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
      verify(ctx).put(keyCaptor.capture(), valueCaptor.capture());

      assertThat(keyCaptor.getValue()).isEqualTo("allowedScopes");
      assertThat((List<String>) valueCaptor.getValue())
          .containsExactlyInAnyOrder("data_access", "user_management");
      verify(ctx).next();
    }
  }

  @Nested
  @DisplayName("KycVerification")
  class KycVerificationTests {

    @Test
    @DisplayName("should call ctx.next() when KYC is required and user is verified")
    void kycRequired_userVerified_callsNext() {
      JsonObject principal = new JsonObject().put("kyc_verified", true);

      when(ctx.user()).thenReturn(user);
      when(user.principal()).thenReturn(principal);

      Handler<RoutingContext> handler = AuthorizationHandler.KycVerification(true);
      handler.handle(ctx);

      verify(ctx).next();
      verify(ctx, never()).fail(any(Throwable.class));
    }

    @Test
    @DisplayName(
        "should call ctx.fail() with DxForbiddenException when KYC is required but user is not verified")
    void kycRequired_userNotVerified_callsFailWith403() {
      JsonObject principal = new JsonObject().put("kyc_verified", false);

      when(ctx.user()).thenReturn(user);
      when(user.principal()).thenReturn(principal);

      Handler<RoutingContext> handler = AuthorizationHandler.KycVerification(true);
      handler.handle(ctx);

      ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
      verify(ctx).fail(captor.capture());
      assertThat(captor.getValue()).isInstanceOf(DxForbiddenException.class);
      assertThat(captor.getValue().getMessage()).isEqualTo("User's KYC is not verified.");
      verify(ctx, never()).next();
    }

    @Test
    @DisplayName("should always call ctx.next() when KYC is not required")
    void kycNotRequired_alwaysCallsNext() {
      // When isKycRequired is false, the handler is just RoutingContext::next
      Handler<RoutingContext> handler = AuthorizationHandler.KycVerification(false);
      handler.handle(ctx);

      verify(ctx).next();
      verify(ctx, never()).fail(any(Throwable.class));
    }

    @Test
    @DisplayName(
        "should call ctx.fail() with DxUnauthorizedException when KYC is required but user is null")
    void kycRequired_userNull_callsFailWith401() {
      when(ctx.user()).thenReturn(null);

      Handler<RoutingContext> handler = AuthorizationHandler.KycVerification(true);
      handler.handle(ctx);

      ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
      verify(ctx).fail(captor.capture());
      assertThat(captor.getValue()).isInstanceOf(DxUnauthorizedException.class);
      assertThat(captor.getValue().getMessage()).isEqualTo("User not authenticated.");
      verify(ctx, never()).next();
    }

    @Test
    @DisplayName(
        "should call ctx.fail() with DxForbiddenException when KYC is required but principal lacks kyc_verified claim")
    void kycRequired_missingKycClaim_callsFailWith403() {
      JsonObject principal = new JsonObject();

      when(ctx.user()).thenReturn(user);
      when(user.principal()).thenReturn(principal);

      Handler<RoutingContext> handler = AuthorizationHandler.KycVerification(true);
      handler.handle(ctx);

      ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
      verify(ctx).fail(captor.capture());
      assertThat(captor.getValue()).isInstanceOf(DxForbiddenException.class);
      assertThat(captor.getValue().getMessage())
          .isEqualTo("Missing KYC verification status.");
      verify(ctx, never()).next();
    }
  }
}
