package org.cdpg.dx.aaa.grpc.apis;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.grpc.auth.ServiceAuthInterceptor;
import org.cdpg.dx.apis.user.v1.GetUserRequest;
import org.cdpg.dx.apis.user.v1.GetUserResponse;
import org.cdpg.dx.apis.user.v1.GrantRolesRequest;
import org.cdpg.dx.apis.user.v1.GrantRolesResponse;
import org.cdpg.dx.apis.user.v1.RevokeRolesRequest;
import org.cdpg.dx.apis.user.v1.RevokeRolesResponse;
import org.cdpg.dx.apis.user.v1.UserServiceGrpc;

/**
 * Serves the shared {@code dx.apis.user.v1.UserService} contract (dx-common
 * src/main/proto/dx/apis/user/v1/user.proto), which dx-user-go serves too.
 *
 * <p>Role changes are limited per caller: {@code roleGrants} maps a calling service's client id
 * (the token's azp, set by {@link ServiceAuthInterceptor}) to the roles it may grant and revoke.
 * A caller with no entry may change no role.
 */
public class UserApiService extends UserServiceGrpc.UserServiceImplBase {

  private static final Logger LOGGER = LogManager.getLogger(UserApiService.class);

  private final Context vertxContext;
  private final RealmUsers users;
  private final Map<String, Set<String>> roleGrants;

  public UserApiService(
      Vertx vertx, JsonObject config, Map<String, Set<String>> roleGrants) {
    this(vertx, new RealmUsers(config), roleGrants);
  }

  UserApiService(Vertx vertx, RealmUsers users, Map<String, Set<String>> roleGrants) {
    this.vertxContext = vertx.getOrCreateContext();
    this.users = users;
    this.roleGrants = Map.copyOf(roleGrants);
  }

  @Override
  public void getUser(GetUserRequest request, StreamObserver<GetUserResponse> observer) {
    String userId = request.getUserId();
    if (!ItemApiService.isUuid(userId)) {
      observer.onError(GrpcErrors.invalid("user_id must be a UUID"));
      return;
    }
    run(
        "GetUser",
        observer,
        () -> users.get(userId).map(u -> GetUserResponse.newBuilder().setUser(u).build()));
  }

  @Override
  public void grantRoles(GrantRolesRequest request, StreamObserver<GrantRolesResponse> observer) {
    if (!allowed(request.getUserId(), request.getRolesList(), observer)) {
      return;
    }
    run(
        "GrantRoles",
        observer,
        () ->
            users
                .grant(request.getUserId(), request.getRolesList())
                .map(roles -> GrantRolesResponse.newBuilder().addAllRoles(roles).build()));
  }

  @Override
  public void revokeRoles(
      RevokeRolesRequest request, StreamObserver<RevokeRolesResponse> observer) {
    if (!allowed(request.getUserId(), request.getRolesList(), observer)) {
      return;
    }
    run(
        "RevokeRoles",
        observer,
        () ->
            users
                .revoke(request.getUserId(), request.getRolesList())
                .map(roles -> RevokeRolesResponse.newBuilder().addAllRoles(roles).build()));
  }

  /**
   * Validates a role change and checks the caller's allowlist; on refusal it has already answered.
   * Must run on the gRPC thread, where the interceptor's caller identity is in the Context.
   */
  private boolean allowed(String userId, List<String> roles, StreamObserver<?> observer) {
    if (!ItemApiService.isUuid(userId)) {
      observer.onError(GrpcErrors.invalid("user_id must be a UUID"));
      return false;
    }
    if (roles.isEmpty()) {
      observer.onError(GrpcErrors.invalid("at least one role is required"));
      return false;
    }
    String caller = ServiceAuthInterceptor.CALLER_SERVICE_KEY.get();
    Set<String> may = caller == null ? Set.of() : roleGrants.getOrDefault(caller, Set.of());
    for (String role : roles) {
      if (!may.contains(role)) {
        observer.onError(
            Status.PERMISSION_DENIED
                .withDescription("caller " + caller + " may not change role " + role)
                .asRuntimeException());
        return false;
      }
    }
    return true;
  }

  private <T> void run(
      String rpc, StreamObserver<T> observer, Supplier<Future<T>> call) {
    vertxContext.runOnContext(
        ignored ->
            call.get()
                .onSuccess(
                    resp -> {
                      observer.onNext(resp);
                      observer.onCompleted();
                    })
                .onFailure(
                    err -> {
                      LOGGER.error("{} failed: {}", rpc, err.getMessage());
                      observer.onError(GrpcErrors.toStatus(err));
                    }));
  }
}
