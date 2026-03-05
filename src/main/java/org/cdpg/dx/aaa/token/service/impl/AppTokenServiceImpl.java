package org.cdpg.dx.aaa.token.service.impl;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.appCredentials.model.AppConstraints;
import org.cdpg.dx.aaa.appCredentials.model.AppCredentials;
import org.cdpg.dx.aaa.appCredentials.service.AppCredentialsService;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.item.util.GetItemRequest;
import org.cdpg.dx.aaa.token.model.AppTokenRequest;
import org.cdpg.dx.aaa.token.model.ItemInfo;
import org.cdpg.dx.aaa.token.service.AppTokenService;
import org.cdpg.dx.aaa.token.util.TokenClaimsBuilder;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.exception.DxUnauthorizedException;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

import java.time.*;
import java.util.*;

public class AppTokenServiceImpl implements AppTokenService {

  private static final Logger LOGGER = LogManager.getLogger(AppTokenServiceImpl.class);
  private static final String JWT_ALGORITHM = "ES256";

  private final JWTAuth jwtAuth;
  private final AppCredentialsService appCredentialsService;
  private final KeycloakUserService keycloakUserService;
  private final ItemService itemService;
  private final JWTOptions jwtOptions;
  private final String issuer;
  private final int tokenExpirationMinutes;

  public AppTokenServiceImpl(
      JWTAuth jwtAuth,
      KeycloakUserService keycloakUserService,
      AppCredentialsService appCredentialsService,
      ItemService itemService,
      String issuer,
      int tokenExpirationMinutes,
      Vertx vertx) {

    this.jwtAuth = jwtAuth;
    this.keycloakUserService = keycloakUserService;
    this.appCredentialsService = appCredentialsService;
    this.itemService = itemService;
    this.issuer = issuer;
    this.tokenExpirationMinutes = tokenExpirationMinutes;
    this.jwtOptions = new JWTOptions().setAlgorithm(JWT_ALGORITHM).setIssuer(issuer);
  }

  @Override
  public Future<JsonObject> createToken(AppTokenRequest request,String itemId) {

    if (request == null || request.appId() == null || request.appSecret() == null) {
      return Future.failedFuture("invalid_app_credentials");
    }

    UUID appId = request.appId();
    String inputSecret = request.appSecret().trim();

    return appCredentialsService
      .getAppById(appId)
      .compose(app ->
        validateApp(app, inputSecret)
          .compose(validatedApp ->
            appCredentialsService.getAppConstraintsById(appId)
              .compose(appConstraints ->
                issueAppToken(validatedApp, appConstraints,itemId)
              )
          )
      );
  }

  /* -------------------------------------------------
   * Validation
   * ------------------------------------------------- */

  private Future<AppCredentials> validateApp(AppCredentials app, String inputSecret) {

    if (app == null) {
      return Future.failedFuture(new DxUnauthorizedException("invalid app credentials"));
    }

    if (app.revokedAt() != null) {
      return Future.failedFuture(new DxForbiddenException("App has been revoked"));
    }

    if (!"active".equalsIgnoreCase(app.status())) {
      return Future.failedFuture(new DxForbiddenException("App is not active"));
    }

    Instant expiryInstant =
        LocalDateTime.parse(app.expiryAt()).atZone(ZoneId.of("Asia/Kolkata")).toInstant();

    if (expiryInstant.isBefore(Instant.now())) {
      return Future.failedFuture(new DxUnauthorizedException("App has been expired"));
    }

    String hashedInputSecret = hash(inputSecret);
    if (!hashedInputSecret.equals(app.appSecret())) {
      return Future.failedFuture(new DxUnauthorizedException("invalid credentials"));
    }

    return Future.succeededFuture(app);
  }

  /* -------------------------------------------------
   * Token issuance
   * ------------------------------------------------- */

  private Future<JsonObject> issueAppToken(AppCredentials app, List<AppConstraints> appConstraints, String itemId) {

    return keycloakUserService
      .getUserById(app.userId())
      .compose(user -> {

        JsonObject userJson = user.toJson();
        LOGGER.info("user info: {}",userJson);
        JsonObject extraClaims = new JsonObject();
        extraClaims.put("appId", app.appId().toString());

        Set<String> userRoles = new HashSet<>(
          userJson.getJsonArray("roles", new JsonArray())
            .stream()
            .map(Object::toString)
            .toList()
        );

        LOGGER.info("user roles : {}", userRoles);

        List<String> allScopes = appConstraints.stream()
          .map(AppConstraints::scope)
          .filter(s -> s != null && !s.isEmpty())
          .distinct()
          .toList();

        LOGGER.info("all scopes : {}",allScopes);

        Set<String> finalScopes = new HashSet<>();
        Set<String> finalRoles = new HashSet<>();
        finalRoles.add("consumer");

        boolean needsItemCheck = false;

        for (String scope : allScopes) {
          switch (scope.toLowerCase()) {

            case "*" -> {
              String highestRole = getHighestRole(userRoles);
              finalRoles.addAll(userRoles);
              LOGGER.info("final roles: {}", finalRoles);
              LOGGER.info("highestRole: {}", highestRole);
              switch (highestRole) {
                case "cos_admin" -> finalScopes.addAll(List.of(
                  "cos_admin_access",
                  "asset_management",
                  "compute_management",
                  "user_management",
                  "credit_management",
                  "publish"));
                case "org_admin" -> finalScopes.addAll(List.of(
                  "org_admin_access",
                  "asset_management",
                  "user_management",
                  "publish"));
                case "provider" -> finalScopes.addAll(List.of(
                  "asset_management"));
                case "compute" -> finalScopes.addAll(List.of(
                  "credit_management"));
                case "consumer" ->
                  {
                    finalScopes.addAll(List.of(
                  "data_access"));
                    needsItemCheck = true; // * includes data_access
                  }
              }
            }

            case "cos_admin_access" -> {
              finalScopes.addAll(List.of("cos_admin_access", "user_management", "asset_management", "compute_management", "data_access"));
              finalRoles.addAll(List.of("cos_admin", "org_admin", "provider", "consumer", "compute"));
            }

            case "org_admin_access" -> {
              finalScopes.addAll(List.of("org_admin_access", "asset_management", "user_management", "data_access"));
              finalRoles.addAll(List.of("org_admin", "consumer"));
              finalRoles.remove("compute");
            }

            case "user_management" -> {
              finalScopes.add("user_management");
              finalRoles.addAll(List.of("org_admin", "consumer"));
            }

            case "asset_management" -> {
              finalScopes.add("asset_management");
              finalRoles.add("provider");
            }

            case "compute_management" -> {
              finalScopes.add("compute_management");
              finalRoles.add("compute");
            }

            case "data_access" -> {
              finalScopes.add("data_access");
              finalRoles.add("consumer");
              needsItemCheck = true;
            }
          }
        }

        extraClaims.put("scope", new JsonArray(new ArrayList<>(finalScopes)));
        extraClaims.put("realm_access", new JsonObject()
          .put("roles", new JsonArray(new ArrayList<>(finalRoles))));

        // If data_access scope is involved and an itemId is provided, fetch and embed item info
        if (needsItemCheck) {
          LOGGER.info("Inside getting token through app Id only");
          String resolvedItemId = resolveItemId(appConstraints, itemId);
          if (resolvedItemId != null && !resolvedItemId.isBlank()) {
            LOGGER.info("Inside body of item!");
            return fetchAndValidateItemForApp(user, resolvedItemId, appConstraints)
              .compose(itemInfo -> {
                extraClaims.mergeIn(itemInfo.toJson());
                LOGGER.info("App token extra claims with item info: {}", extraClaims);
                return generateJwtToken(user, extraClaims);
              });
          }
        }


        // No itemId provided — skip item check, issue token with scopes only
        return generateJwtToken(user, extraClaims);
      });
  }

  private Future<ItemInfo> fetchAndValidateItemForApp(DxUser user, String itemId, List<AppConstraints> appConstraints) {

    GetItemRequest itemRequest = new GetItemRequest(itemId, user.sub().toString());

    return itemService
      .getItemWithAccessChecks(itemRequest)
      .compose(response -> {

        if (response == null || response.getResponse() == null) {
          LOGGER.error("Item not found or access denied for itemId: {}", itemId);
          return Future.failedFuture(new DxNotFoundException("Item not found or access denied for id: " + itemId));
        }

        JsonArray resultArray = response.getResponse().getJsonArray("results");
        if (resultArray == null || resultArray.isEmpty()) {
          LOGGER.error("Empty results for itemId: {}", itemId);
          return Future.failedFuture(new DxNotFoundException("No results found for item id: " + itemId));
        }

        JsonObject item = resultArray.getJsonObject(0);
        LOGGER.info("Item info: {}", item);

        ItemInfo info = ItemInfo.fromJson(item);
        LOGGER.info("Item info parsed: {}", info.toJson());

        // Validate: if constraints specify item IDs, ensure this itemId is among them
        boolean constraintHasItemId = appConstraints.stream()
          .anyMatch(c -> c.entityId() != null && !c.entityId().isBlank());

        if (constraintHasItemId) {
          // If any constraint grants wildcard access, skip item-level check
          boolean wildcardAccess = appConstraints.stream()
            .anyMatch(c -> "*".equals(c.entityId()));

          if (!wildcardAccess) {
            boolean itemAllowed = appConstraints.stream()
              .anyMatch(c -> itemId.equals(c.entityId()));

            if (!itemAllowed) {
              LOGGER.error("Item {} not permitted by app constraints", itemId);
              return Future.failedFuture(new DxForbiddenException("Item not permitted by app constraints"));
            }
          }
        }

        return Future.succeededFuture(info);
      });
  }

  private String resolveItemId(List<AppConstraints> appConstraints, String requestItemId) {
    Set<String> dataItemTypes = Set.of("adex:Apps", "adex:DataBank", "adex:AiModel");

    return appConstraints.stream()
      .filter(c -> "data_access".equalsIgnoreCase(c.scope()))
      .filter(c -> dataItemTypes.contains(c.entityType()))
      .filter(c -> c.entityId() != null && isValidUUID(c.entityId()))
      .map(AppConstraints::entityId)
      .findFirst()
      .orElseGet(() -> {
        LOGGER.info("No valid constraint itemId found, using request body itemId: {}", requestItemId);
        return requestItemId;
      });
  }

  private boolean isValidUUID(String value) {
    try {
      UUID.fromString(value);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  private Future<JsonObject> generateJwtToken(DxUser user, JsonObject extraClaims) {

    JsonObject claims =
        TokenClaimsBuilder.buildClaims(user, issuer, "CLAIM_AUDIENCE", tokenExpirationMinutes);

    LOGGER.info("extra claims: {}",extraClaims);
    claims.remove("realm_access");
    claims.mergeIn(extraClaims);
    LOGGER.info("claims : {}",claims);

    String token = jwtAuth.generateToken(claims, jwtOptions);

    return Future.succeededFuture(
        new JsonObject()
            .put("access_token", token)
            .put("token_type", "Bearer")
            .put("expires_in_minutes", tokenExpirationMinutes));
  }

  private String getHighestRole(Set<String> roles) {
    if (roles.contains("cos_admin")) return "cos_admin";
    if (roles.contains("org_admin")) return "org_admin";
    if (roles.contains("provider")) return "provider";
    return "consumer";
  }

  /* -------------------------------------------------
   * Utilities
   * ------------------------------------------------- */

  private String hash(String input) {
    return DigestUtils.sha512Hex(input);
  }
}
