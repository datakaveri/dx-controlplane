package org.cdpg.dx.aaa.grpc.apis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.grpc.Context;
import io.grpc.Status;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.cdpg.dx.aaa.grpc.auth.ServiceAuthInterceptor;
import org.cdpg.dx.apis.user.v1.GetUserRequest;
import org.cdpg.dx.apis.user.v1.GetUserResponse;
import org.cdpg.dx.apis.user.v1.GrantRolesRequest;
import org.cdpg.dx.apis.user.v1.GrantRolesResponse;
import org.cdpg.dx.apis.user.v1.RevokeRolesRequest;
import org.cdpg.dx.apis.user.v1.RevokeRolesResponse;
import org.cdpg.dx.apis.user.v1.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserApiServiceTest {

  private static final String USER = "0d0b1a52-6c0b-4f4e-9d52-1f0b8f5b6a11";
  private static final String MARKETPLACE = "svc-dx-marketplace";

  private Vertx vertx;
  private RealmUsers users;
  private UserApiService svc;

  @BeforeEach
  void setUp() {
    vertx = Vertx.vertx();
    users = mock(RealmUsers.class);
    svc = new UserApiService(vertx, users, Map.of(MARKETPLACE, Set.of("mor_admin", "seller")));
  }

  @AfterEach
  void tearDown() {
    vertx.close();
  }

  /** Runs as the given calling service, the way ServiceAuthInterceptor sets it. */
  private static void as(String caller, Runnable call) {
    Context.current().withValue(ServiceAuthInterceptor.CALLER_SERVICE_KEY, caller).run(call);
  }

  @Test
  void getUserReturnsTheUser() throws Exception {
    User u = User.newBuilder().setId(USER).addRoles("consumer").build();
    when(users.get(USER)).thenReturn(Future.succeededFuture(u));

    Recorder<GetUserResponse> r = new Recorder<>();
    svc.getUser(GetUserRequest.newBuilder().setUserId(USER).build(), r);

    assertThat(r.value().getUser()).isEqualTo(u);
  }

  @Test
  void getUserMapsKeycloak404ToNotFound() throws Exception {
    when(users.get(USER))
        .thenReturn(Future.failedFuture(new jakarta.ws.rs.NotFoundException()));

    Recorder<GetUserResponse> r = new Recorder<>();
    svc.getUser(GetUserRequest.newBuilder().setUserId(USER).build(), r);

    assertThat(r.code()).isEqualTo(Status.Code.NOT_FOUND);
  }

  @Test
  void grantsAnAllowedRole() throws Exception {
    when(users.grant(USER, List.of("seller")))
        .thenReturn(Future.succeededFuture(List.of("consumer", "seller")));

    Recorder<GrantRolesResponse> r = new Recorder<>();
    as(
        MARKETPLACE,
        () ->
            svc.grantRoles(
                GrantRolesRequest.newBuilder().setUserId(USER).addRoles("seller").build(), r));

    assertThat(r.value().getRolesList()).containsExactly("consumer", "seller");
  }

  @Test
  void refusesARoleOutsideTheCallersAllowlist() throws Exception {
    Recorder<GrantRolesResponse> r = new Recorder<>();
    as(
        MARKETPLACE,
        () ->
            svc.grantRoles(
                GrantRolesRequest.newBuilder()
                    .setUserId(USER)
                    .addRoles("seller")
                    .addRoles("cos_admin")
                    .build(),
                r));

    assertThat(r.code()).isEqualTo(Status.Code.PERMISSION_DENIED);
    verifyNoInteractions(users);
  }

  @Test
  void refusesACallerWithNoAllowlist() throws Exception {
    Recorder<RevokeRolesResponse> r = new Recorder<>();
    as(
        "svc-dx-dataplane",
        () ->
            svc.revokeRoles(
                RevokeRolesRequest.newBuilder().setUserId(USER).addRoles("seller").build(), r));

    assertThat(r.code()).isEqualTo(Status.Code.PERMISSION_DENIED);
    verifyNoInteractions(users);
  }

  @Test
  void revokesAnAllowedRole() throws Exception {
    when(users.revoke(USER, List.of("seller"))).thenReturn(Future.succeededFuture(List.of()));

    Recorder<RevokeRolesResponse> r = new Recorder<>();
    as(
        MARKETPLACE,
        () ->
            svc.revokeRoles(
                RevokeRolesRequest.newBuilder().setUserId(USER).addRoles("seller").build(), r));

    assertThat(r.value().getRolesList()).isEmpty();
    verify(users).revoke(USER, List.of("seller"));
  }

  @Test
  void validatesTheRequest() throws Exception {
    Recorder<GrantRolesResponse> noRoles = new Recorder<>();
    as(MARKETPLACE, () -> svc.grantRoles(GrantRolesRequest.newBuilder().setUserId(USER).build(), noRoles));
    assertThat(noRoles.code()).isEqualTo(Status.Code.INVALID_ARGUMENT);

    Recorder<GetUserResponse> badId = new Recorder<>();
    svc.getUser(GetUserRequest.newBuilder().setUserId("x").build(), badId);
    assertThat(badId.code()).isEqualTo(Status.Code.INVALID_ARGUMENT);
  }
}
