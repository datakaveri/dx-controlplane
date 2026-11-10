package org.cdpg.dx.aaa.token.service.impl;

import io.vertx.core.CompositeFuture;
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
import org.cdpg.dx.aaa.delegation.DelegationAccessEvaluator;
import org.cdpg.dx.aaa.delegation.models.DelegationGrant;
import org.cdpg.dx.aaa.delegation.models.DelegationScopeConstraint;
import org.cdpg.dx.aaa.delegation.service.DelegationService;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.item.util.GetItemRequest;
import org.cdpg.dx.aaa.token.model.AccessTokenRequest;
import org.cdpg.dx.aaa.token.model.DelegationValidationResult;
import org.cdpg.dx.aaa.token.model.ItemInfo;
import org.cdpg.dx.aaa.token.service.TokenService;
import org.cdpg.dx.aaa.token.util.TokenClaimsBuilder;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

import java.time.LocalDateTime;
import java.util.*;

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
  private final DelegationAccessEvaluator delegationAccessEvaluator;

  public TokenServiceImpl(
      JWTAuth provider,
      KeycloakUserService keycloakUserService,
      ClientcredetialService clientcredetialService,
      ItemService itemService,
      String issuer,
      int expirationMinutes,
      DelegationService delegationService,
      DelegationAccessEvaluator delegationAccessEvaluator,
      Vertx vertx) {
    this.provider = provider;
    this.keycloakUserService = keycloakUserService;
    this.clientcredetialService = clientcredetialService;
    this.itemService = itemService;
    this.issuer = issuer;
    this.tokenExpirationMinutes = expirationMinutes;
    this.vertx = vertx;
    this.delegationAccessEvaluator = delegationAccessEvaluator;
    this.delegationService = delegationService;
    this.options = new JWTOptions().setAlgorithm(JWT_ALGORITHM).setIssuer(issuer);
  }

  // ============================
  // DRIVER METHOD
  // ============================
  @Override
  public Future<JsonObject> createToken(AccessTokenRequest request) {
    boolean hasAuthenticatedUser = request.authenticatedUser() != null;
    boolean hasClientCredentials = request.clientId() != null && request.clientSecret() != null;
    if (hasAuthenticatedUser && hasClientCredentials) {
      return Future.failedFuture(
          new DxBadRequestException(
              "Ambiguous request: provide either an Authorization bearer token or clientId/clientSecret, not both"));
    }
    if (!hasAuthenticatedUser && !hasClientCredentials) {
      return Future.failedFuture(
          new DxBadRequestException(
              "Missing clientId or clientSecret, or a valid Authorization bearer token"));
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
    LOGGER.info("Creating access token!");
    return resolveDxUser(request)
        .compose(
            user ->
                fetchItemInfo(user, request.itemId())
                    .compose(itemInfo -> generateJwtToken(user, itemInfo.toJson())));
  }

  // ============================
  // IDENTITY TOKEN
  // ============================
  private Future<JsonObject> createIdentityToken(AccessTokenRequest request) {
    return resolveDxUser(request).compose(user -> generateJwtToken(user, null));
  }

  // ============================
  // DELEGATION TOKEN
  // ============================
  private Future<JsonObject> createDelegationToken(AccessTokenRequest request) {
    LOGGER.info("Creating delegation token!");
    return resolveDxUser(request)
        .compose(
            user ->
                fetchDelegationConstraints(request.delegationId(),user.sub().toString())
                    .compose(
                        delegationConstraints ->
                            fetchItemInfoWithDelegationFallback(user, request.itemId())
                                .map(
                                    itemInfo -> {

                                      LOGGER.info("after itemInfo");
                                      JsonObject extraClaims = itemInfo.toJson();

                                      extraClaims.put("scopes", delegationConstraints.getJsonArray("scopes"));
//                                      extraClaims.put("delegationId", request.delegationId());
                                      extraClaims.put(
                                          "did", delegationConstraints.getString("delegatorId"));
                                      extraClaims.put(
                                        "dilr", delegationConstraints.getString("delegatorId"));

                                      LOGGER.info("extraClaims: {}",extraClaims);
                                      return extraClaims;
                                    }))
                    .compose(extraClaims -> generateJwtToken(user, extraClaims)));
  }

  // ============================
  // HELPER METHODS
  // ============================
  private Future<DxUser> resolveDxUser(AccessTokenRequest request) {
    if (request.authenticatedUser() != null) {
      // Already resolved by the router's "optionalAuth" handler from a verified
      // Authorization bearer token - no further Keycloak call needed.
      return Future.succeededFuture(request.authenticatedUser());
    }
    return getDxUser(request.clientId(), request.clientSecret());
  }

  private Future<DxUser> getDxUser(String clientId, String clientSecret) {
    String hashedClientId = hash(clientId);
    String hashedClientSecret = hash(clientSecret);
    return clientcredetialService
        .getUserIdByClientIdAndSecret(hashedClientId, hashedClientSecret)
        .compose(keycloakUserService::getUserById);
  }

  private Future<JsonObject> fetchDelegationConstraints(String delegationId, String userId) {
    UUID delegationUUID = UUID.fromString(delegationId);
    UUID userUUID = UUID.fromString(userId);

    return delegationService.getDelegationScopeConstraints(delegationUUID.toString())
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

        for (JsonObject constraint : scopeConstraints) {
//          LOGGER.info("Scope Constraints is {}",constraint.toJson());
          scopes.add(constraint.getString("scope"));

          if ("data-access".equalsIgnoreCase(constraint.getString("scope"))) {
            if (constraint.getString("entity_id") == null) {
              return Future.failedFuture(
                new DxBadRequestException("Entity ID cannot be null for data-access scope")
              );
            }
//            entityIds.add(constraint.entityId().toString());
          }

        }

        return delegationService.getDelegationGrantById(delegationUUID.toString())
          .compose(grant -> {
            if (grant == null) {
              return Future.failedFuture(
                new DxNotFoundException("Delegation grant not found for ID: " + delegationId)
              );
            }

            if (!"active".equalsIgnoreCase(grant.getString("status"))) {
              return Future.failedFuture(
                new DxForbiddenException("Delegation " + delegationId + " is not active")
              );
            }

            if (!grant.getValue("delegate_id").equals(userUUID)) {
              return Future.failedFuture(
                new DxForbiddenException("Delegation does not belong to this delegate")
              );
            }

            JsonObject response = new JsonObject()
              .put("delegationId", grant.getValue("delegation_id").toString())
              .put("delegatorId", grant.getValue("delegator_id").toString())
              .put("delegateId", grant.getValue("delegate_id").toString())
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




  /**
   * Resolves DIRECT access to the item only - no delegation fallback. Fails with
   * DxNotFoundException if the item doesn't exist, or with whatever access-denial error
   * itemService raises (e.g. DxForbiddenException/DxUnauthorizedException) if it exists but
   * isn't directly accessible to this user.
   */
  private Future<ItemInfo> fetchItemInfo(DxUser user, String itemId) {
    LOGGER.info("Fetching item info");
    LOGGER.info("itemId:{}",itemId);
    JsonObject claims =
        TokenClaimsBuilder.buildClaims(user, issuer, "CLAIM_AUDIENCE", tokenExpirationMinutes);
    String token = provider.generateToken(claims, options);

    LOGGER.info("claims1 :{}",claims);

    GetItemRequest itemRequest = new GetItemRequest(itemId, user.sub().toString());
    itemRequest.setToken(token);
    itemRequest.setRoles(user.roles());

    return itemService
      .getItemWithAccessChecks(itemRequest)
      .compose(response -> {
        JsonArray resultArray = response != null ? response.getResponse().getJsonArray("results") : null;
        JsonObject item =
            resultArray != null && !resultArray.isEmpty() ? resultArray.getJsonObject(0) : null;

        if (item == null) {
          LOGGER.warn("Item with ID {} does not exist", itemId);
          return Future.failedFuture(new DxNotFoundException("Item not found: " + itemId));
        }

        LOGGER.info("Item info: {}",item);
        ItemInfo info = ItemInfo.fromJson(item);
        LOGGER.info("Item info to json: {}",info.toJson());
        LOGGER.info("Item {} exists for user Id (direct access)", itemId);
        return Future.succeededFuture(info);
      });
  }

  /**
   * Same as fetchItemInfo, but for the explicit delegation-token flow: if direct access is
   * denied (item exists but not directly accessible), falls back to checking delegation access.
   * A genuinely nonexistent item is never retried via delegation.
   */
  private Future<ItemInfo> fetchItemInfoWithDelegationFallback(DxUser user, String itemId) {
    return fetchItemInfo(user, itemId)
        .recover(err -> {
          if (err instanceof DxNotFoundException) {
            return Future.failedFuture(err);
          }
          return handleDelegationAccess(user, itemId)
              .map(delegationDetails -> {
                LOGGER.info("Delegation access granted for item {} after failure from direct access", itemId);
                LOGGER.debug("ExtraClaims :{}",delegationDetails);
                return ItemInfo.fromJson(delegationDetails);
              });
        });
  }

//  private Future<DelegationValidationResult> checkDelegationForItem(DxUser user, String itemIdStr) {
//    LOGGER.info("Inside check Delegator Access for item!");
//    UUID userId = user.sub();
//    UUID itemId = UUID.fromString(itemIdStr);
//    LocalDateTime now = LocalDateTime.now();
//
//    return delegationService.getAllDelegationScopeConstraints(itemId)
//      .compose(ar -> {
//        if (ar == null)
//          return Future.failedFuture("No delegation exists for this itemId");
//
//        DelegationScopeConstraint ds = ar.getFirst();
//
//        UUID resDelegationId = ds.delegationId();
//
//        return delegationService.getDelegationGrantById(resDelegationId).compose(
//            grant -> {
//              if (grant == null)
//                return Future.failedFuture("delegation id does not exists in delegation grant table !");
//
//                UUID delegateId = grant.delegateId();
//                UUID delegatorId = grant.delegatorId();
//
//                if (!delegateId.equals(userId)) {
//                  return Future.failedFuture("This user does not have access to the item ID!");
//                }
//
//                if (grant.expiryAt() != null && grant.expiryAt().isBefore(now)) {
//                  return Future.failedFuture("Delegation for this item has expired");
//                }
//
//                LOGGER.info("Valid delegation found for delegate {} (delegator {}) for item {}",
//                  delegateId, delegatorId, itemId);
//
//                return keycloakUserService.getUserById(delegatorId)
//                  .compose(delegatorUser -> {
//                    List<String> delegatorRoles = delegatorUser.roles();
//                    LOGGER.info("Fetched delegator {} roles: {}", delegatorId, delegatorRoles);
//
//                    GetItemRequest itemRequest = new GetItemRequest(itemIdStr, delegatorId.toString());
//                    itemRequest.setRoles(delegatorRoles);
//
//                    return itemService.getItemWithAccessChecks(itemRequest)
//                      .compose(response -> {
//                        if (response == null || response.getResponse() == null) {
//                          return Future.failedFuture("Delegator does not have access to the item");
//                        }
//
//                        LOGGER.info("Delegator {} has access to item {}", delegatorId, itemId);
//
//                        JsonArray resultArray = response.getResponse().getJsonArray("results");
//                        JsonObject item = resultArray.getJsonObject(0);
////                        ItemInfo info = ItemInfo.fromJson(item);
//
//                        ItemInfo info = ItemInfo.fromJson(item);
//                        return Future.succeededFuture(
//                          new DelegationValidationResult(resDelegationId, delegatorId, delegateId, info)
//                        );
//                      });
//                  });
//              });
//            })
//          .recover(err -> {
//            LOGGER.error("Error while checking delegation for item {}: {}", itemIdStr, err.getMessage());
//            return Future.failedFuture(err);
//          });
//  }

  private Future<JsonObject> handleDelegationAccess(DxUser user, String itemId) {

    LOGGER.info("Inside handle Delegation Access!");

    return delegationAccessEvaluator
      .validateItemAccess(user, itemId)
      .compose(res -> {

        LOGGER.info(
          "Delegation verified for user {} on item {}. Generating delegation token...",
          user.sub(), itemId
        );

        ItemInfo info = res.itemInfo();
        JsonObject extraClaims = info.toJson();

        UUID delegatorId = res.delegatorId();
        UUID delegateId = res.delegateId();

        return keycloakUserService.getUserById(delegatorId)
          .map(delegatorUser -> {

            extraClaims.put("did", delegatorId.toString());
            extraClaims.put("drl", new JsonArray(delegatorUser.roles()));

            LOGGER.info(
              "Delegation token claims built successfully: {}",
              extraClaims.encodePrettily()
            );

            return extraClaims;
          });
      })
      .recover(err -> {

        LOGGER.error(
          "Delegation access check failed for user {} on item {}: {}",
          user.sub(), itemId, err.getMessage()
        );

        return Future.failedFuture(
          new DxForbiddenException(
            "No access found via delegation or direct access"
          )
        );
      });
  }


  private Future<JsonObject> generateJwtToken(DxUser user, JsonObject extraClaims) {
      LOGGER.debug("Inside generation of tokens : {}", extraClaims);
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
