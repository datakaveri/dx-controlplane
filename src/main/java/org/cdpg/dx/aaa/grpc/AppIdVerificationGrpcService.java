package org.cdpg.dx.aaa.grpc;

import io.grpc.stub.StreamObserver;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.appCredentials.model.AppConstraints;
import org.cdpg.dx.aaa.appCredentials.model.AppCredentials;
import org.cdpg.dx.aaa.appCredentials.service.AppCredentialsService;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.item.util.GetItemRequest;
import org.cdpg.dx.auth.appid.v1.AppIdPrincipalProto;
import org.cdpg.dx.auth.appid.v1.AppIdVerificationServiceGrpc;
import org.cdpg.dx.auth.appid.v1.VerifyAppIdRequest;
import org.cdpg.dx.auth.appid.v1.VerifyAppIdResponse;

public class AppIdVerificationGrpcService
    extends AppIdVerificationServiceGrpc.AppIdVerificationServiceImplBase {

  private static final Logger LOGGER =
      LogManager.getLogger(AppIdVerificationGrpcService.class);

  private final Context vertxContext;
  private final AppCredentialsService appCredentialsService;
  private final ItemService itemService;

  public AppIdVerificationGrpcService(
      Vertx vertx,
      AppCredentialsService appCredentialsService,
      ItemService itemService) {
    this.vertxContext = vertx.getOrCreateContext();
    this.appCredentialsService = appCredentialsService;
    this.itemService = itemService;
  }

  @Override
  public void verifyAppId(
      VerifyAppIdRequest request,
      StreamObserver<VerifyAppIdResponse> responseObserver) {

    String appIdStr = request.getAppId();
    String appSecret = request.getAppSecret();

    UUID appId;
    try {
      appId = UUID.fromString(appIdStr);
    } catch (IllegalArgumentException e) {
      respond(responseObserver, failResponse("INVALID_CREDENTIALS"));
      return;
    }

    // Dispatch to Vert.x event loop so service proxies (PostgresService etc.) work correctly
    vertxContext.runOnContext(ignored ->
        appCredentialsService
            .getAppById(appId)
            .compose(app -> validateApp(app, appSecret))
            .compose(app ->
                appCredentialsService
                    .getAppConstraintsById(appId)
                    .compose(constraints -> buildResponse(app, constraints)))
            .onSuccess(response -> respond(responseObserver, response))
            .onFailure(err -> {
              LOGGER.error("AppId verification failed for {}: {}", appIdStr, err.getMessage());
              respond(responseObserver, failResponse(mapErrorCode(err)));
            }));
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
      Instant expiry = LocalDateTime.parse(app.expiryAt())
          .atZone(ZoneId.of("Asia/Kolkata"))
          .toInstant();
      if (expiry.isBefore(Instant.now())) {
        return Future.failedFuture("EXPIRED");
      }
    }
    if (!DigestUtils.sha512Hex(inputSecret).equals(app.appSecret())) {
      return Future.failedFuture("INVALID_CREDENTIALS");
    }
    return Future.succeededFuture(app);
  }

  /* ── Response building ───────────────────────────────────────────────── */

  private Future<VerifyAppIdResponse> buildResponse(
      AppCredentials app, List<AppConstraints> constraints) {

    List<String> scopes = constraints.stream()
        .map(AppConstraints::scope)
        .filter(s -> s != null && !s.isBlank())
        .distinct()
        .collect(Collectors.toList());

    // Collect specific UUID entity IDs under data_access scope for metadata enrichment
    List<String> dataAccessEntityIds = constraints.stream()
        .filter(c -> "data_access".equalsIgnoreCase(c.scope()))
        .map(AppConstraints::entityId)
        .filter(eid -> eid != null && isValidUUID(eid))
        .distinct()
        .collect(Collectors.toList());

    if (dataAccessEntityIds.isEmpty()) {
      return Future.succeededFuture(successResponse(app, scopes, Map.of()));
    }

    List<Future<Map.Entry<String, String>>> metaFutures = dataAccessEntityIds.stream()
        .map(entityId -> fetchEntityMetadata(entityId, app.userId().toString()))
        .collect(Collectors.toList());

    return CompositeFuture.all(new ArrayList<>(metaFutures))
        .map(cf -> {
          Map<String, String> entityMetadataMap = new HashMap<>();
          for (Future<Map.Entry<String, String>> f : metaFutures) {
            Map.Entry<String, String> entry = f.result();
            if (entry != null) {
              entityMetadataMap.put(entry.getKey(), entry.getValue());
            }
          }
          return successResponse(app, scopes, entityMetadataMap);
        });
  }

  private Future<Map.Entry<String, String>> fetchEntityMetadata(
      String entityId, String userId) {

    GetItemRequest req = new GetItemRequest(entityId, userId);
    return itemService
        .getItemWithAccessChecks(req)
        .map(response -> {
          if (response == null || response.getResponse() == null) {
            return null;
          }
          JsonArray results = response.getResponse().getJsonArray("results");
          if (results == null || results.isEmpty()) {
            return null;
          }
          JsonObject item = results.getJsonObject(0);
          JsonObject metadata = new JsonObject()
              .put("iid", item.getString("id"))
              .put("accessPolicy", item.getString("accessPolicy"))
              .put("resourceServer", item.getValue("resourceServer"))
              .put("policies", item.getValue("policies"));
          return Map.entry(entityId, metadata.encode());
        })
        .recover(err -> {
          LOGGER.warn("Could not fetch item metadata for entityId {}: {}",
              entityId, err.getMessage());
          return Future.succeededFuture(null);
        });
  }

  private VerifyAppIdResponse successResponse(
      AppCredentials app,
      List<String> scopes,
      Map<String, String> entityMetadataMap) {

    long expiresAtEpoch = 0;
    if (app.expiryAt() != null) {
      expiresAtEpoch = LocalDateTime.parse(app.expiryAt())
          .atZone(ZoneId.of("Asia/Kolkata"))
          .toInstant()
          .getEpochSecond();
    }

    AppIdPrincipalProto principal = AppIdPrincipalProto.newBuilder()
        .setAppId(app.appId().toString())
        .setOwnerId(app.userId().toString())
        .addRoles(app.role() != null ? app.role() : "consumer")
        .addAllScopes(scopes)
        .putAllEntityMetadataMap(entityMetadataMap)
        .setExpiresAtEpoch(expiresAtEpoch)
        .build();

    return VerifyAppIdResponse.newBuilder()
        .setSuccess(true)
        .setPrincipal(principal)
        .build();
  }

  private VerifyAppIdResponse failResponse(String errorCode) {
    return VerifyAppIdResponse.newBuilder()
        .setSuccess(false)
        .setErrorCode(errorCode)
        .build();
  }

  /* ── Helpers ─────────────────────────────────────────────────────────── */

  private void respond(
      StreamObserver<VerifyAppIdResponse> observer,
      VerifyAppIdResponse response) {
    observer.onNext(response);
    observer.onCompleted();
  }

  private String mapErrorCode(Throwable err) {
    String msg = err.getMessage();
    if (msg != null) {
      if (msg.contains("REVOKED")) return "REVOKED";
      if (msg.contains("EXPIRED")) return "EXPIRED";
    }
    return "INVALID_CREDENTIALS";
  }

  private boolean isValidUUID(String value) {
    try {
      UUID.fromString(value);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
