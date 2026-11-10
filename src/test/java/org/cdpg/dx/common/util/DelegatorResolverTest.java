package org.cdpg.dx.common.util;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import java.util.UUID;
import org.cdpg.dx.aaa.user.service.UserService;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.model.DxUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DelegatorResolverTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID DELEGATOR_ID = UUID.randomUUID();
  private static final UUID ORG_ID = UUID.randomUUID();

  private RoutingContext mockCtx(String delegatorIdParam, String subjectId) {
    RoutingContext ctx = mock(RoutingContext.class);
    MultiMap queryParams = MultiMap.caseInsensitiveMultiMap();
    if (delegatorIdParam != null) {
      queryParams.add("delegatorId", delegatorIdParam);
    }
    when(ctx.queryParams()).thenReturn(queryParams);

    User user = mock(User.class);
    when(user.subject()).thenReturn(subjectId);
    when(ctx.user()).thenReturn(user);

    return ctx;
  }

  @Nested
  @DisplayName("getDelegatorId()")
  class GetDelegatorId {

    @Test
    @DisplayName("returns null when delegatorId query param is absent")
    void returnsNull_whenAbsent() {
      RoutingContext ctx = mockCtx(null, USER_ID.toString());
      assertThat(DelegatorResolver.getDelegatorId(ctx)).isNull();
    }

    @Test
    @DisplayName("returns UUID when delegatorId query param is present")
    void returnsUuid_whenPresent() {
      RoutingContext ctx = mockCtx(DELEGATOR_ID.toString(), USER_ID.toString());
      assertThat(DelegatorResolver.getDelegatorId(ctx)).isEqualTo(DELEGATOR_ID);
    }

    @Test
    @DisplayName("throws IllegalArgumentException for invalid UUID format")
    void throws_whenInvalidUuid() {
      RoutingContext ctx = mockCtx("not-a-uuid", USER_ID.toString());
      assertThatThrownBy(() -> DelegatorResolver.getDelegatorId(ctx))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("resolveActingUserId()")
  class ResolveActingUserId {

    @Test
    @DisplayName("returns authenticated user ID when no delegator is specified")
    void returnsAuthUserId_whenNoDelegator() {
      RoutingContext ctx = mockCtx(null, USER_ID.toString());
      assertThat(DelegatorResolver.resolveActingUserId(ctx)).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("returns delegator ID when delegator is specified")
    void returnsDelegatorId_whenSpecified() {
      RoutingContext ctx = mockCtx(DELEGATOR_ID.toString(), USER_ID.toString());
      assertThat(DelegatorResolver.resolveActingUserId(ctx)).isEqualTo(DELEGATOR_ID);
    }
  }

  @Nested
  @DisplayName("resolveOrgId()")
  class ResolveOrgId {

    @Test
    @DisplayName("resolves org from principal when no delegator")
    void resolvesFromPrincipal_whenNoDelegator() {
      RoutingContext ctx = mockCtx(null, USER_ID.toString());
      User user = ctx.user();
      JsonObject principal = new JsonObject().put("organisation_id", ORG_ID.toString());
      when(user.principal()).thenReturn(principal);

      UserService userService = mock(UserService.class);

      Future<UUID> result = DelegatorResolver.resolveOrgId(ctx, userService, null);

      assertThat(result.succeeded()).isTrue();
      assertThat(result.result()).isEqualTo(ORG_ID);
    }

    @Test
    @DisplayName("fails with DxBadRequestException when principal has no org ID")
    void fails_whenPrincipalHasNoOrgId() {
      RoutingContext ctx = mockCtx(null, USER_ID.toString());
      User user = ctx.user();
      JsonObject principal = new JsonObject();
      when(user.principal()).thenReturn(principal);

      UserService userService = mock(UserService.class);

      Future<UUID> result = DelegatorResolver.resolveOrgId(ctx, userService, null);

      assertThat(result.failed()).isTrue();
      assertThat(result.cause()).isInstanceOf(DxBadRequestException.class);
    }

    @Test
    @DisplayName("fails when principal org ID does not match expected param")
    void fails_whenOrgIdMismatch() {
      RoutingContext ctx = mockCtx(null, USER_ID.toString());
      User user = ctx.user();
      JsonObject principal = new JsonObject().put("organisation_id", ORG_ID.toString());
      when(user.principal()).thenReturn(principal);

      UserService userService = mock(UserService.class);
      String differentOrgId = UUID.randomUUID().toString();

      Future<UUID> result = DelegatorResolver.resolveOrgId(ctx, userService, differentOrgId);

      assertThat(result.failed()).isTrue();
      assertThat(result.cause()).isInstanceOf(DxBadRequestException.class);
    }

    @Test
    @DisplayName("resolves org from delegator's user info when delegator specified")
    void resolvesFromDelegator_whenSpecified() {
      RoutingContext ctx = mockCtx(DELEGATOR_ID.toString(), USER_ID.toString());

      DxUser delegatorUser = mock(DxUser.class);
      when(delegatorUser.organisationId()).thenReturn(ORG_ID.toString());

      UserService userService = mock(UserService.class);
      when(userService.getUserInfoByID(DELEGATOR_ID)).thenReturn(Future.succeededFuture(delegatorUser));

      Future<UUID> result = DelegatorResolver.resolveOrgId(ctx, userService, null);

      assertThat(result.succeeded()).isTrue();
      assertThat(result.result()).isEqualTo(ORG_ID);
    }

    @Test
    @DisplayName("fails when delegator is not found (null user)")
    void fails_whenDelegatorNotFound() {
      RoutingContext ctx = mockCtx(DELEGATOR_ID.toString(), USER_ID.toString());

      UserService userService = mock(UserService.class);
      when(userService.getUserInfoByID(DELEGATOR_ID)).thenReturn(Future.succeededFuture(null));

      Future<UUID> result = DelegatorResolver.resolveOrgId(ctx, userService, null);

      assertThat(result.failed()).isTrue();
      assertThat(result.cause()).isInstanceOf(DxBadRequestException.class);
      assertThat(result.cause().getMessage()).contains("Delegator is not valid");
    }

    @Test
    @DisplayName("fails when delegator has no organisation")
    void fails_whenDelegatorHasNoOrg() {
      RoutingContext ctx = mockCtx(DELEGATOR_ID.toString(), USER_ID.toString());

      DxUser delegatorUser = mock(DxUser.class);
      when(delegatorUser.organisationId()).thenReturn(null);

      UserService userService = mock(UserService.class);
      when(userService.getUserInfoByID(DELEGATOR_ID)).thenReturn(Future.succeededFuture(delegatorUser));

      Future<UUID> result = DelegatorResolver.resolveOrgId(ctx, userService, null);

      assertThat(result.failed()).isTrue();
      assertThat(result.cause()).isInstanceOf(DxBadRequestException.class);
      assertThat(result.cause().getMessage()).contains("not part of any organisation");
    }
  }
}
