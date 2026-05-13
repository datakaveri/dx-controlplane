package org.cdpg.dx.aaa.appCredentials.service.impl;

import static org.cdpg.dx.aaa.appCredentials.util.Constants.*;
import static org.cdpg.dx.aaa.common.Constants.ROLES;
import static org.cdpg.dx.common.util.DateTimeHelper.FORMATTER;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;

import io.vertx.ext.auth.User;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.appCredentials.dao.AppConstraintsDAO;
import org.cdpg.dx.aaa.appCredentials.dao.AppCredentialsDAO;
import org.cdpg.dx.aaa.appCredentials.model.AppConstraints;
import org.cdpg.dx.aaa.appCredentials.model.AppCredentialResponse;
import org.cdpg.dx.aaa.appCredentials.model.AppCredentials;
import org.cdpg.dx.aaa.appCredentials.service.AppCredentialsService;
import org.cdpg.dx.aaa.delegation.DelegationHandlerValidator;
import org.cdpg.dx.aaa.delegation.DelegationValidator;
import org.cdpg.dx.aaa.delegation.models.DelegationScopeConstraint;
import org.cdpg.dx.aaa.user.service.UserService;
import org.cdpg.dx.common.exception.BaseDxException;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.exception.DxUnauthorizedException;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.database.postgres.models.PaginatedResult;
import org.cdpg.dx.databroker.service.DataBrokerService;

public class AppCredentialsServiceImpl implements AppCredentialsService {

  public static final int APP_SECRET_BYTES = 20;
  private final Logger LOGGER = LogManager.getLogger(AppCredentialsServiceImpl.class);
  private final AppCredentialsDAO appCredentialsDAO;
  private final AppConstraintsDAO appConstraintsDAO;
  private final DelegationValidator delegationValidator;
  private final DataBrokerService dataBrokerService;
  private final UserService userService;
  private final String appIdRevokeExchange;
  private final Supplier<String> randomSecretSupplier =
      () -> {
        byte[] randBytes = new byte[APP_SECRET_BYTES];
        new SecureRandom().nextBytes(randBytes);
        return Hex.encodeHexString(randBytes);
      };

  public AppCredentialsServiceImpl(DelegationValidator delegationValidator, AppCredentialsDAO appCredentialsDAO, AppConstraintsDAO appConstraintsDAO, DataBrokerService dataBrokerService, String appIdRevokeExchange,UserService userService) {
    this.appCredentialsDAO = appCredentialsDAO;
    this.appConstraintsDAO = appConstraintsDAO;
    this.delegationValidator = delegationValidator;
    this.dataBrokerService = dataBrokerService;
    this.appIdRevokeExchange = appIdRevokeExchange;
    this.userService = userService;
  }

  @Override
  public Future<AppCredentials> createApp(JsonObject appCredentialsJson) {

    LOGGER.info("ServiceImplementation of createApp");
    LOGGER.info("Create app request body: {}", appCredentialsJson);

    // generate secret
    String clientSecret = randomSecretSupplier.get();
    String hashedSecret = DigestUtils.sha512Hex(clientSecret);
    appCredentialsJson.put(APP_SECRET, hashedSecret);

    UUID userId = UUID.fromString(appCredentialsJson.getString(USER_ID));

    JsonArray rolesArray = appCredentialsJson.getJsonArray(ROLES);

    boolean isWildcardApp =
      rolesArray == null || rolesArray.isEmpty();

    AppCredentials appCredentials =
      AppCredentials.fromJson(appCredentialsJson);

    String expirationDate = appCredentialsJson.getString(EXPIRY_AT);
    LocalDateTime expiry = LocalDateTime.parse(expirationDate, FORMATTER);

    Future<AppCredentials> flow;


    if (isWildcardApp) {

      flow = appCredentialsDAO.create(appCredentials)
        .compose(savedApp->createScopes(savedApp.appId(),expiry,userId)
        .map(res-> savedApp)
        );

    } else {

      flow =
        delegationValidator
          .validateConstraints(userId, rolesArray)
          .compose(v -> appCredentialsDAO.create(appCredentials))
          .compose(savedApp ->
            insertAppConstraints(savedApp.appId(), rolesArray,expiry,userId)
              .map(v -> savedApp)
          );
    }

    return flow
      .map(savedApp -> {
        // return plain secret only once
        return AppCredentials.fromJson(
          savedApp.toJson().put(APP_SECRET, clientSecret)
        );
      })
      .recover(err -> {
        LOGGER.error("Failed to create app", err);
        return Future.failedFuture(BaseDxException.from(err));
      });
  }

  private Future<Void> insertAppConstraints(
    UUID appId,
    JsonArray roles,
    LocalDateTime expiryAt,
    UUID userId
  ) {

    if (roles == null || roles.isEmpty()) {
      return Future.succeededFuture();
    }

    List<Future> insertFutures = new ArrayList<>();

    for (Object roleObj : roles) {
      JsonObject roleJson = (JsonObject) roleObj;

      String role = roleJson.getString("role");
      JsonArray constraints =
        roleJson.getJsonArray("constraints",new JsonArray());

      if(constraints.isEmpty())
      {
        insertFutures.add(createScopes(appId,expiryAt,userId));
        continue;
      }

      for (Object constraintObj : constraints) {
        JsonObject constraint = (JsonObject) constraintObj;
        String scope = constraint.getString("scope");
        LOGGER.info("constraints in delseviceImpl: {}" ,constraints.encode());

        JsonArray entityIds = constraint.getJsonArray("entity_id");

        //skipping cos_admin_access and compute_management because no entity check is needed for them
        if (entityIds != null && !entityIds.isEmpty()) {
          for (Object entity : entityIds) {
            insertFutures.add(createScopeConstraint(appId, constraint, entity,userId)
            );
          }
        } else{
          // entity_id == null means entity_type is already null (validated)
          insertFutures.add(createScopeConstraint(appId, constraint, null,userId)
          );
        }
      }
    }

    return CompositeFuture.all(insertFutures).mapEmpty();
  }

  private Future<Void> createScopeConstraint(
    UUID appId,
    JsonObject constraint,
    Object entityId,
    UUID userId
  ) {

    JsonObject dbRow = new JsonObject()
      .put("app_id", appId.toString())
      .put("scope", constraint.getString("scope")!=null
        ?constraint.getString("scope"):"*")
      .put("expiry_at", constraint.getString("expiry_at"))
      .put(
        "entity_id", entityId !=null ?
          entityId
          : "*")
      .put("user_id",userId.toString())
      .put(
        "entity_type",
        constraint.getString("entity_type") != null
          ? constraint.getString("entity_type")
          : "*"
      );

    AppConstraints appConstraints =
      AppConstraints.fromJson(dbRow);

    return appConstraintsDAO
      .create(appConstraints)
      .mapEmpty();
  }

  private Future<Void> createScopes(
    UUID appId,
    LocalDateTime expiry,
    UUID userId
  ) {

    JsonObject dbRow = new JsonObject()
      .put("app_id", appId.toString())
      .put("scope", "*")
      .put("expiry_at", expiry)
      .put(
        "entity_id", "*")
      .put("user_id",userId.toString())
      .put(
        "entity_type", "*"
      );

    AppConstraints appConstraints =
      AppConstraints.fromJson(dbRow);

    return appConstraintsDAO
      .create(appConstraints)
      .mapEmpty();
  }



  @Override
  public Future<PaginatedResult<AppCredentialResponse>> getApp(PaginatedRequest paginatedRequest) {
    return appCredentialsDAO.getAllWithFilters(paginatedRequest)
      .map(paginatedResult -> {
        List<AppCredentialResponse> sanitized = paginatedResult.data().stream()
          .map(app -> new AppCredentialResponse(
            app.appId(),
            app.userId(),
            app.expiryAt(),
            app.status(),
            app.createdAt(),
            app.modifiedAt(),
            app.revokedAt()
          ))
          .toList();

        return new PaginatedResult<>(
          paginatedResult.paginationInfo(),
          sanitized
        );
      });
  }


  @Override
  public Future<Boolean> deleteApp(UUID userId, UUID appId) {

    Map<String, Object> filter =
        Map.of(
            USER_ID, userId.toString(),
            APP_ID, appId.toString());

    return appCredentialsDAO
        .getAllWithFilters(filter)
        .compose(
            apps -> {
              if (apps.isEmpty()) {
                return Future.failedFuture(
                    new DxNotFoundException(
                        "No appCredentials found for userId " + userId + " and appId " + appId));
              }
              return appCredentialsDAO.delete(appId);
            })
        .compose(deleted -> {
          publishRevocation(appId.toString());
          return Future.succeededFuture(deleted);
        });
  }


  @Override
  public Future<Boolean> changeAppStatus(UUID userId, UUID appId, String status) {

    Map<String, Object> inFilter = Map.of(
      USER_ID, userId.toString(),
      APP_ID, appId.toString()
    );

    Map<String, Object> updateFilter = Map.of(
      STATUS, status
    );

    return appCredentialsDAO
      .update(inFilter, updateFilter)
      .compose(apps -> {
        if (apps == null) {
          return Future.failedFuture(
            new DxNotFoundException(
              "No appCredentials found for userId " + userId + " and appId " + appId));
        }
        if ("revoked".equalsIgnoreCase(status)) {
          publishRevocation(appId.toString());
        }
        return Future.succeededFuture(true);
      });
  }

  private void publishRevocation(String appId) {
    JsonObject payload = new JsonObject().put("appId", appId);
    dataBrokerService
        .publishMessageInternal(payload, appIdRevokeExchange, "##")
        .onSuccess(v -> LOGGER.info("AppId revocation published to payload={}", payload))
        .onFailure(err -> LOGGER.error("Failed to publish AppId revocation for appId={}: {}", appId, err.getMessage()));
  }

  @Override
  public Future<AppCredentials> getAppById(UUID appId) {
    Map<String, Object> filter = Map.of(APP_ID, appId.toString());

    return appCredentialsDAO
        .getAllWithFilters(filter)
        .compose(
            apps -> {
              if (apps.isEmpty()) {
                return Future.failedFuture(new DxNotFoundException("Invalid appId or appSecret"));
              }
              return Future.succeededFuture(apps.stream().findFirst().get());
            });
  }


  @Override
  public Future<DxUser> postDxUserInfoFromAppId(String appId, String appSecret) {
    Map<String, Object> credentialsFilter = Map.of(APP_ID, appId);
    Map<String, Object> constraintsFilter = Map.of(APP_ID, appId);

    Future<List<AppCredentials>> credentialsFuture = appCredentialsDAO.getAllWithFilters(credentialsFilter);
    Future<List<AppConstraints>> constraintsFuture = appConstraintsDAO.getAllWithFilters(constraintsFilter);

    return Future.all(credentialsFuture, constraintsFuture)
      .compose(results -> {
        List<AppCredentials> credentials = results.resultAt(0);
        List<AppConstraints> constraints = results.resultAt(1);

        LOGGER.info("credentials: {}",credentials);
        LOGGER.info("constraints: {}",constraints);

        if (credentials.isEmpty()) {
          return Future.failedFuture(new DxNotFoundException("Invalid appId"));
        }
        if (constraints.isEmpty()) {
          return Future.failedFuture(new DxNotFoundException("No constraints found for appId"));
        }

        AppCredentials appCredentials = credentials.stream().findFirst().get();

        String hashedIncomingSecret = DigestUtils.sha512Hex(appSecret);
        if (!hashedIncomingSecret.equals(appCredentials.appSecret())) {
          return Future.failedFuture(new DxUnauthorizedException("Invalid appSecret"));
        }

        String role = appCredentials.role();
        UUID userId = appCredentials.userId();

        // Scopes from AppConstraints
        JsonArray scopes = new JsonArray();
        constraints.forEach(c -> {
          if (c.scope() != null && !c.scope().isBlank()) {
            scopes.add(c.scope());
          }
        });

        // Fetch user details using userId from AppCredentials
        return userService.getUserInfoByID(userId)
          .compose(baseUser -> {
            if (baseUser == null) {
              return Future.failedFuture(new DxBadRequestException("User not found for appId"));
            }

            DxUser dxUser = new DxUser(
              role != null ? List.of(role) : baseUser.roles(),
              baseUser.organisationId(),
              baseUser.organisationName(),
              baseUser.sub(),
              baseUser.emailVerified(),
              baseUser.kycVerified(),
              baseUser.name(),
              baseUser.preferredUsername(),
              baseUser.givenName(),
              baseUser.familyName(),
              baseUser.email(),
              baseUser.pendingRoles(),
              baseUser.organisation(),
              baseUser.createdAt(),
              baseUser.kycData(),
              baseUser.twitter_account(),
              baseUser.linkedin_account(),
              baseUser.github_account(),
              baseUser.account_enabled(),
              baseUser.did(),
              baseUser.aud(),
              scopes,
              null,  // delegateeId — not applicable here
              null   // appId — not applicable here
            );

            return Future.succeededFuture(dxUser);
          });
      });
  }

  @Override
  public Future<List<AppConstraints>> getAppConstraintsById(UUID appId) {
    Map<String, Object> filter = Map.of(APP_ID, appId.toString());

    return appConstraintsDAO
      .getAllWithFilters(filter)
      .compose(
        apps -> {
          if (apps.isEmpty()) {
            return Future.failedFuture(new DxNotFoundException("Invalid appId or appSecret"));
          }
          return Future.succeededFuture(apps);
        });
  }
}
