package org.cdpg.dx.aaa.appCredentials.service.impl;


import io.vertx.core.json.JsonObject;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.appCredentials.dao.AppCredentialsDAO;
import org.cdpg.dx.aaa.appCredentials.model.AppCredentials;
import org.cdpg.dx.aaa.appCredentials.service.AppCredentialsService;
import io.vertx.core.Future;
import org.cdpg.dx.aaa.delegation.models.DelegationScopeConstraint;
import org.cdpg.dx.aaa.delegation.util.RoleScopeMapping;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.database.postgres.models.PaginatedResult;


import java.security.SecureRandom;
import java.util.*;
import java.util.function.Supplier;

import static org.cdpg.dx.aaa.appCredentials.util.Constants.*;


public class AppCredentialsServiceImpl implements AppCredentialsService {

  public static final int APP_SECRET_BYTES = 20;
  private final Logger LOGGER = LogManager.getLogger(AppCredentialsServiceImpl.class);
  private final AppCredentialsDAO appCredentialsDAO;
  private final Supplier<String> randomSecretSupplier =
    () -> {
      byte[] randBytes = new byte[APP_SECRET_BYTES];
      new SecureRandom().nextBytes(randBytes);
      return Hex.encodeHexString(randBytes);
    };


  public AppCredentialsServiceImpl(AppCredentialsDAO appCredentialsDAO) {
    this.appCredentialsDAO = appCredentialsDAO;
  }

  @Override
  public Future<AppCredentials> createApp(
      AppCredentials appCredentials) {

    String clientSecret = randomSecretSupplier.get();
    String hashedClientSecret = DigestUtils.sha512Hex(clientSecret);

    JsonObject appCredentialsJson = appCredentials.toJson();
    String userId = appCredentialsJson.getString(USER_ID);

    appCredentialsJson.put(APP_SECRET,hashedClientSecret);

    AppCredentials updated_appCredentials = AppCredentials.fromJson(appCredentialsJson);

    return appCredentialsDAO
      .create(updated_appCredentials)
      .onSuccess(
        savedCredentials ->
          LOGGER.info("App credentials saved successfully for userId: {}", userId))
      .onFailure(throwable -> LOGGER.error("Failed to save app credentials", throwable));

  }

  @Override
  public Future<PaginatedResult<AppCredentials>> getApp(PaginatedRequest paginatedRequest) {
    return appCredentialsDAO.getAllWithFilters(paginatedRequest);
  }

  @Override
  public Future<Boolean> deleteApp(UUID userId, UUID appId) {

    Map<String, Object> filter = Map.of(
      USER_ID, userId.toString(),
      APP_ID, appId.toString()
    );

    return appCredentialsDAO
      .getAllWithFilters(filter)
      .compose(apps -> {
        if (apps.isEmpty()) {
          return Future.failedFuture(
            new DxNotFoundException(
              "No appCredentials found for userId " + userId + " and appId " + appId
            )
          );
        }
        return appCredentialsDAO.delete(appId);
      });
  }

}
