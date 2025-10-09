package org.cdpg.dx.aaa.token.service.impl;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.clientSecret.service.ClientcredetialService;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.item.util.GetItemRequest;
import org.cdpg.dx.aaa.token.model.AccessTokenRequest;
import org.cdpg.dx.aaa.token.model.ItemInfo;
import org.cdpg.dx.aaa.token.service.TokenService;
import org.cdpg.dx.aaa.token.util.TokenClaimsBuilder;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

public class TokenServiceImpl implements TokenService {

  private static final String JWT_ALGORITHM = "ES256";
  private final Logger LOGGER = LogManager.getLogger(TokenServiceImpl.class);

  private final JWTAuth provider;
  private final KeycloakUserService keycloakUserService;
  private final ClientcredetialService clientcredetialService;
  private final ItemService itemService;
  private final String issuer;
  private final JWTOptions options;
  private final int tokenExpirationMinutes;
  private final Vertx vertx;

  public TokenServiceImpl(
      JWTAuth provider,
      KeycloakUserService keycloakUserService,
      ClientcredetialService clientcredetialService,
      ItemService itemService,
      String issuer,
      int expirationMinutes,
      Vertx vertx) {
    this.provider = provider;
    this.keycloakUserService = keycloakUserService;
    this.clientcredetialService = clientcredetialService;
    this.itemService = itemService;
    this.issuer = issuer;
    this.tokenExpirationMinutes = expirationMinutes;
    this.vertx = vertx;
    this.options = new JWTOptions().setAlgorithm(JWT_ALGORITHM).setIssuer(issuer);
  }

  // ============================
  // DRIVER METHOD
  // ============================
  @Override
  public Future<JsonObject> createToken(AccessTokenRequest request) {
    if (request.clientId() == null || request.clientSecret() == null) {
      return Future.failedFuture("Missing clientId or clientSecret");
    }

    if (request.delegationId() != null && !request.delegationId().isBlank()) {
      return createDelegationToken(request);
    }

    if (request.itemId() != null && !request.itemId().isBlank()) {
      return createAccessToken(request);
    }

    return createIdentityToken(request);
  }

  // ============================
  // ACCESS TOKEN
  // ============================
  private Future<JsonObject> createAccessToken(AccessTokenRequest request) {
    return getDxUser(request.clientId(), request.clientSecret())
        .compose(
            user ->
                fetchItemInfo(user, request.itemId())
                    .compose(itemInfo -> generateJwtToken(user, itemInfo.toJson())));
  }

  // ============================
  // IDENTITY TOKEN
  // ============================
  private Future<JsonObject> createIdentityToken(AccessTokenRequest request) {
    return getDxUser(request.clientId(), request.clientSecret())
        .compose(user -> generateJwtToken(user, null));
  }

  // ============================
  // DELEGATION TOKEN
  // ============================
  private Future<JsonObject> createDelegationToken(AccessTokenRequest request) {
    return getDelegatedDxUser(request.clientId(), request.clientSecret(), request.delegationId())
        .compose(
            user ->
                fetchDelegationConstraints(request.delegationId())
                    .compose(
                        delegationConstraints ->
                            fetchItemInfo(user, request.itemId())
                                .map(
                                    itemInfo -> {
                                      // override constraints with delegation constraints
                                      JsonObject extraClaims = itemInfo.toJson();
                                      extraClaims.put("constraints", delegationConstraints);
                                      extraClaims.put("delegationId", request.delegationId());
                                      extraClaims.put(
                                          "did", delegationConstraints.getString("delegatorId"));
                                      return extraClaims;
                                    }))
                    .compose(extraClaims -> generateJwtToken(user, extraClaims)));
  }

  // ============================
  // HELPER METHODS
  // ============================
  private Future<DxUser> getDxUser(String clientId, String clientSecret) {
    String hashedClientId = hash(clientId);
    String hashedClientSecret = hash(clientSecret);
    return clientcredetialService
        .getUserIdByClientIdAndSecret(hashedClientId, hashedClientSecret)
        .compose(keycloakUserService::getUserById);
  }

  private Future<DxUser> getDelegatedDxUser(
      String clientId, String clientSecret, String delegationId) {
    // Option 1: fetch userId from clientId + clientSecret
    return getDxUser(clientId, clientSecret);
    // Option 2 (if delegation service provides user directly):
    // return delegationService.getUserByDelegationId(delegationId);
  }

  private Future<JsonObject> fetchDelegationConstraints(String delegationId) {
    // TODO: implement actual call to DelegationService
    return Future.succeededFuture(new JsonObject().put("access", "delegated"));
  }

  private Future<ItemInfo> fetchItemInfo(DxUser user, String itemId) {
    JsonObject claims =
        TokenClaimsBuilder.buildClaims(user, issuer, "CLAIM_AUDIENCE", tokenExpirationMinutes);
    String token = provider.generateToken(claims, options);

    GetItemRequest itemRequest = new GetItemRequest(user.sub().toString(), itemId);
    itemRequest.setToken(token);
    itemRequest.setRoles(user.roles());

    return itemService
        .getItemWithAccessChecks(itemRequest)
        .map(response -> ItemInfo.fromJson(response.getResponse()));
  }

  private Future<JsonObject> generateJwtToken(DxUser user, JsonObject extraClaims) {
    JsonObject claims =
        TokenClaimsBuilder.buildClaims(user, issuer, "CLAIM_AUDIENCE", tokenExpirationMinutes);
    if (extraClaims != null) {
      claims.mergeIn(extraClaims);
    }
    String token = provider.generateToken(claims, options);
    return Future.succeededFuture(
        new JsonObject()
            .put("access_token", token)
            .put("token_type", "jwt")
            .put("expires_in_minutes", tokenExpirationMinutes));
  }

  private String hash(String input) {
    return DigestUtils.sha512Hex(input.trim());
  }
}
