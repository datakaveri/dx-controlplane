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
import org.cdpg.dx.common.exception.BaseDxException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.database.postgres.models.PaginatedResult;

public class AppCredentialsServiceImpl implements AppCredentialsService {

  public static final int APP_SECRET_BYTES = 20;
  private final Logger LOGGER = LogManager.getLogger(AppCredentialsServiceImpl.class);
  private final AppCredentialsDAO appCredentialsDAO;
  private final AppConstraintsDAO appConstraintsDAO;
  private final DelegationValidator delegationValidator;
  private final Supplier<String> randomSecretSupplier =
      () -> {
        byte[] randBytes = new byte[APP_SECRET_BYTES];
        new SecureRandom().nextBytes(randBytes);
        return Hex.encodeHexString(randBytes);
      };

  public AppCredentialsServiceImpl(DelegationValidator delegationValidator,AppCredentialsDAO appCredentialsDAO, AppConstraintsDAO appConstraintsDAO) {
    this.appCredentialsDAO = appCredentialsDAO;
    this.appConstraintsDAO = appConstraintsDAO;
    this.delegationValidator = delegationValidator;
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
        return Future.succeededFuture(true);
      });
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
