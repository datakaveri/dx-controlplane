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
import org.cdpg.dx.aaa.clientSecret.service.ClientcredetialService;
import org.cdpg.dx.aaa.delegation.models.DelegationScopeConstraint;
import org.cdpg.dx.aaa.delegation.service.DelegationService;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.item.util.GetItemRequest;
import org.cdpg.dx.aaa.token.model.AccessTokenRequest;
import org.cdpg.dx.aaa.token.model.ItemInfo;
import org.cdpg.dx.aaa.token.service.TokenService;
import org.cdpg.dx.aaa.token.util.TokenClaimsBuilder;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

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
  private final DelegationService delegationService;

  public TokenServiceImpl(
      JWTAuth provider,
      KeycloakUserService keycloakUserService,
      ClientcredetialService clientcredetialService,
      ItemService itemService,
      String issuer,
      int expirationMinutes,
      DelegationService delegationService,
      Vertx vertx) {
    this.provider = provider;
    this.keycloakUserService = keycloakUserService;
    this.clientcredetialService = clientcredetialService;
    this.itemService = itemService;
    this.issuer = issuer;
    this.tokenExpirationMinutes = expirationMinutes;
    this.vertx = vertx;
    this.delegationService = delegationService;
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
                fetchDelegationConstraints(request.delegationId(),user.sub().toString())
                    .compose(
                        delegationConstraints ->
                            fetchItemInfo(user, request.itemId())
                                .map(
                                    itemInfo -> {

                                      LOGGER.info("after itemInfo");
                                      JsonObject extraClaims = itemInfo.toJson();

                                      extraClaims.put("scopes", delegationConstraints.getJsonArray("scopes"));

                                      if (delegationConstraints.containsKey("entityIds")) {
                                        extraClaims.put("constraints", delegationConstraints.getJsonArray("entityIds"));
                                      }

                                      extraClaims.put("delegationId", request.delegationId());
                                      extraClaims.put(
                                          "did", delegationConstraints.getString("delegateId"));

                                      LOGGER.info("extraClaims: {}",extraClaims);
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

  private Future<JsonObject> fetchDelegationConstraints(String delegationId, String userId) {
    UUID delegationUUID = UUID.fromString(delegationId);
    UUID userUUID = UUID.fromString(userId);

    return delegationService.getDelegationScopeConstraints(delegationUUID)
      .compose(scopeConstraints -> {
        if (scopeConstraints == null || scopeConstraints.isEmpty()) {
          return Future.failedFuture(
            new DxNotFoundException("No scope constraints found for delegation ID: " + delegationId)
          );
        }


        // Assuming all constraints share same delegation info
        UUID delegatorId = null;
        UUID delegateId = null;
        String status = "active";

        // Extract scopes and entityIds
        Set<String> scopes = new HashSet<>();
//        Set<String> entityIds = new HashSet<>();

        for (DelegationScopeConstraint constraint : scopeConstraints) {
//          LOGGER.info("Scope Constraints is {}",constraint.toJson());
          scopes.add(constraint.scope());

          if ("data_access".equalsIgnoreCase(constraint.scope())) {
            if (constraint.entityId() == null) {
              return Future.failedFuture(
                new DxBadRequestException("Entity ID cannot be null for data_access scope")
              );
            }
//            entityIds.add(constraint.entityId().toString());
          }

        }

        return delegationService.getDelegationGrantById(delegationUUID)
          .compose(grant -> {
            if (grant == null) {
              return Future.failedFuture(
                new DxNotFoundException("Delegation grant not found for ID: " + delegationId)
              );
            }

            if (!"active".equalsIgnoreCase(grant.status())) {
              return Future.failedFuture(
                new DxForbiddenException("Delegation " + delegationId + " is not active")
              );
            }

            if (!grant.delegateId().equals(userUUID)) {
              return Future.failedFuture(
                new DxForbiddenException("Delegation does not belong to this delegate")
              );
            }

            JsonObject response = new JsonObject()
              .put("delegationId", grant.delegationId().toString())
              .put("delegatorId", grant.delegatorId().toString())
              .put("delegateId", grant.delegateId().toString())
              .put("scopes", new JsonArray(new ArrayList<>(scopes)));

//            if (!entityIds.isEmpty()) {
//              response.put("entityIds", new JsonArray(new ArrayList<>(entityIds)));
//            }

            LOGGER.info("Delegation constraints: {}", response);
            return Future.succeededFuture(response);
          });
      })
      .recover(err -> {
        LOGGER.error("Error fetching delegation constraints for ID {}: {}", delegationId, err.getMessage());
        return Future.failedFuture(err);
      });
  }




  private Future<ItemInfo> fetchItemInfo(DxUser user, String itemId) {

    LOGGER.info("itemId:{}",itemId);
    JsonObject claims =
        TokenClaimsBuilder.buildClaims(user, issuer, "CLAIM_AUDIENCE", tokenExpirationMinutes);
    String token = provider.generateToken(claims, options);

    LOGGER.info("claims :{}",claims);

    GetItemRequest itemRequest = new GetItemRequest(itemId, user.sub().toString());
    itemRequest.setToken(token);
    itemRequest.setRoles(user.roles());

    LOGGER.info("error here ? ");

    return itemService
        .getItemWithAccessChecks(itemRequest)
        .map(response -> ItemInfo.fromJson(response.getResponse()));
  }

  private Future<JsonObject> generateJwtToken(DxUser user, JsonObject extraClaims) {
      LOGGER.debug("itemInfo: {}", extraClaims);
    JsonObject claims =
        TokenClaimsBuilder.buildClaims(user, issuer, "CLAIM_AUDIENCE", tokenExpirationMinutes);
    if (extraClaims != null ) {
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
