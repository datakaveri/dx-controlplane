package org.cdpg.dx.aaa.grpc;

import io.grpc.stub.StreamObserver;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.appCredentials.model.AppConstraints;
import org.cdpg.dx.aaa.appCredentials.model.AppCredentials;
import org.cdpg.dx.aaa.appCredentials.service.AppCredentialsService;
import org.cdpg.dx.aaa.delegation.service.DelegationService;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.item.util.GetItemRequest;
import org.cdpg.dx.auth.appid.v1.AppIdPrincipalProto;
import org.cdpg.dx.auth.appid.v1.AppIdVerificationServiceGrpc;
import org.cdpg.dx.auth.appid.v1.CheckItemAccessRequest;
import org.cdpg.dx.auth.appid.v1.CheckItemAccessResponse;
import org.cdpg.dx.auth.appid.v1.VerifyAppIdRequest;
import org.cdpg.dx.auth.appid.v1.VerifyAppIdResponse;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

public class AppIdVerificationGrpcService
    extends AppIdVerificationServiceGrpc.AppIdVerificationServiceImplBase {

  private static final Logger LOGGER = LogManager.getLogger(AppIdVerificationGrpcService.class);

  private final Context vertxContext;
  private final AppCredentialsService appCredentialsService;
  private final ItemService itemService;
  private final DelegationService delegationService;
  private final KeycloakUserService keycloakUserService;

  public AppIdVerificationGrpcService(
      Vertx vertx,
      AppCredentialsService appCredentialsService,
      ItemService itemService,
      DelegationService delegationService,
      KeycloakUserService keycloakUserService) {
    this.vertxContext = vertx.getOrCreateContext();
    this.appCredentialsService = appCredentialsService;
    this.itemService = itemService;
    this.delegationService = delegationService;
    this.keycloakUserService = keycloakUserService;
  }

  /* ── VerifyAppId ─────────────────────────────────────────────────────── */

  @Override
  public void verifyAppId(
      VerifyAppIdRequest request, StreamObserver<VerifyAppIdResponse> observer) {
    String appIdStr = request.getAppId();
    String appSecret = request.getAppSecret();

    UUID appId;
    try {
      appId = UUID.fromString(appIdStr);
    } catch (IllegalArgumentException e) {
      respond(observer, failVerify("INVALID_CREDENTIALS"));
      return;
    }

    vertxContext.runOnContext(
        ignored ->
            appCredentialsService
                .getAppById(appId)
                .compose(app -> validateApp(app, appSecret))
                .compose(app -> buildVerifyResponse(app, appId))
                .onSuccess(response -> respond(observer, response))
                .onFailure(
                    err -> {
                      LOGGER.error(
                          "AppId verification failed for {}: {}", appIdStr, err.getMessage());
                      respond(observer, failVerify(mapErrorCode(err)));
                    }));
  }

  private Future<VerifyAppIdResponse> buildVerifyResponse(AppCredentials app, UUID appId) {
    return appCredentialsService
        .getAppConstraintsById(appId)
        .compose(
            constraints -> {
              List<String> scopes =
                  constraints.stream()
                      .map(AppConstraints::scope)
                      .filter(s -> s != null && !s.isBlank())
                      .distinct()
                      .collect(Collectors.toList());

              long expiresAtEpoch = 0;
              if (app.expiryAt() != null) {
                expiresAtEpoch =
                    LocalDateTime.parse(app.expiryAt())
                        .atZone(ZoneId.of("Asia/Kolkata"))
                        .toInstant()
                        .getEpochSecond();
              }

              final long finalExpiresAtEpoch = expiresAtEpoch;
              final List<String> finalScopes = scopes;

              // Fetch owner's organisationId from Keycloak to build a DxUser-equivalent principal.
              // If Keycloak is unavailable we still return a valid response with empty
              // organisationId.
              return keycloakUserService
                  .getUserById(app.userId())
                  .recover(
                      err -> {
                        LOGGER.warn(
                            "Could not fetch user info for userId={}: {}",
                            app.userId(),
                            err.getMessage());
                        return Future.succeededFuture(null);
                      })
                  .map(
                      dxUser -> {
                        String organisationId =
                            (dxUser != null && dxUser.organisationId() != null)
                                ? dxUser.organisationId()
                                : "";

                        AppIdPrincipalProto principal =
                            AppIdPrincipalProto.newBuilder()
                                .setAppId(app.appId().toString())
                                .setUserId(app.userId().toString())
                                .addRoles(app.role() != null ? app.role() : "consumer")
                                .addAllScopes(finalScopes)
                                .setExpiresAtEpoch(finalExpiresAtEpoch)
                                .setOrganisationId(organisationId)
                                .build();

                        return VerifyAppIdResponse.newBuilder()
                            .setSuccess(true)
                            .setPrincipal(principal)
                            .build();
                      });
            });
  }

  /* ── CheckItemAccess ─────────────────────────────────────────────────── */

  @Override
  public void checkItemAccess(
      CheckItemAccessRequest request, StreamObserver<CheckItemAccessResponse> observer) {
    String userId = request.getUserId();
    String entityId = request.getEntityId();
    String did = request.getDid();
    boolean hasDid = did != null && !did.isBlank();

    if (userId == null || userId.isBlank()) {
      respond(observer, failAccess("INVALID_CREDENTIALS"));
      return;
    }

    vertxContext.runOnContext(
        ignored -> {
          Future<CheckItemAccessResponse> work;
          if (hasDid) {
            // Mirror the HTTP /cat/item/access delegation check:
            // userId = delegator (app owner), did = delegate (acting on their behalf)
            work =
                delegationService
                    .checkItemAccess(userId, did)
                    .compose(
                        delegResult -> {
                          JsonArray allowed = delegResult.getJsonArray("result");
                          if (allowed == null
                              || (!allowed.contains("*") && !allowed.contains(entityId))) {
                            return Future.failedFuture("DELEGATION_NO_ACCESS");
                          }
                          return fetchItemMetadata(entityId, userId);
                        });
          } else {
            work = fetchItemMetadata(entityId, userId);
          }
          work.onSuccess(response -> respond(observer, response))
              .onFailure(
                  err -> {
                    LOGGER.error(
                        "CheckItemAccess failed userId={} entityId={} did={}: {}",
                        userId,
                        entityId,
                        did,
                        err.getMessage());
                    respond(observer, failAccess("NO_ACCESS"));
                  });
        });
  }

  private Future<CheckItemAccessResponse> fetchItemMetadata(String entityId, String userId) {
    GetItemRequest req = new GetItemRequest(entityId, userId);
    return itemService
        .getItemWithAccessChecks(req)
        .map(
            response -> {
              if (response == null || response.getResponse() == null) {
                throw new RuntimeException("No item response");
              }
              JsonArray results = response.getResponse().getJsonArray("results");
              if (results == null || results.isEmpty()) {
                throw new RuntimeException("Item not found");
              }
              JsonObject item = results.getJsonObject(0);
              String iid = item.getString("id", "");
              String accessPolicy = item.getString("accessPolicy", "");
              Object resourceServer = item.getValue("resourceServer");
              Object policies = item.getValue("policies");
              String resourceServerJson = resourceServer != null ? resourceServer.toString() : "[]";
              String policiesJson = policies != null ? policies.toString() : "[]";

              return CheckItemAccessResponse.newBuilder()
                  .setSuccess(true)
                  .setIid(iid)
                  .setAccessPolicy(accessPolicy)
                  .setResourceServerJson(resourceServerJson)
                  .setPoliciesJson(policiesJson)
                  .build();
            });
  }

  /* ── Validation ──────────────────────────────────────────────────────── */

  private Future<AppCredentials> validateApp(AppCredentials app, String inputSecret) {
    if (app.revokedAt() != null) {
      return Future.failedFuture("REVOKED");
    }
    if (!"active".equalsIgnoreCase(app.status())) {
      return Future.failedFuture("REVOKED");
    }
    if (app.expiryAt() != null) {
      Instant expiry =
          LocalDateTime.parse(app.expiryAt()).atZone(ZoneId.of("Asia/Kolkata")).toInstant();
      if (expiry.isBefore(Instant.now())) {
        return Future.failedFuture("EXPIRED");
      }
    }
    if (!DigestUtils.sha512Hex(inputSecret).equals(app.appSecret())) {
      return Future.failedFuture("INVALID_CREDENTIALS");
    }
    return Future.succeededFuture(app);
  }

  /* ── Helpers ─────────────────────────────────────────────────────────── */

  private <T> void respond(StreamObserver<T> observer, T response) {
    observer.onNext(response);
    observer.onCompleted();
  }

  private VerifyAppIdResponse failVerify(String errorCode) {
    return VerifyAppIdResponse.newBuilder().setSuccess(false).setErrorCode(errorCode).build();
  }

  private CheckItemAccessResponse failAccess(String errorCode) {
    return CheckItemAccessResponse.newBuilder().setSuccess(false).setErrorCode(errorCode).build();
  }

  private String mapErrorCode(Throwable err) {
    String msg = err.getMessage();
    if (msg != null) {
      if (msg.contains("REVOKED")) return "REVOKED";
      if (msg.contains("EXPIRED")) return "EXPIRED";
    }
    return "INVALID_CREDENTIALS";
  }
}
