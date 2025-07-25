package org.cdpg.dx.aaa.asset.service;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.asset.dao.AssetRequestDAO;
import org.cdpg.dx.aaa.asset.models.AssetRequest;
import org.cdpg.dx.aaa.asset.models.Status;
import org.cdpg.dx.auth.authorization.model.DxRole;
import org.cdpg.dx.common.exception.BaseDxException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.exception.NoRowFoundException;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.database.postgres.models.PaginatedResult;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.cdpg.dx.aaa.accessRequest.dao.model.Status.*;
import static org.cdpg.dx.aaa.asset.util.Constants.*;
import static org.cdpg.dx.common.util.DateTimeHelper.FORMATTER;


public class AssetServiceImpl implements AssetService {

  private static final Logger LOGGER = LoggerFactory.getLogger(AssetServiceImpl.class);

  private final AssetRequestDAO assetRequestDAO;
  private final KeycloakUserService keycloakUserService;
  private final JsonObject config;

  public AssetServiceImpl(AssetRequestDAO assetRequestDAO, KeycloakUserService keycloakUserService, JsonObject config) {
    this.assetRequestDAO = assetRequestDAO;
    this.keycloakUserService = keycloakUserService;
    this.config = config;
  }

  @Override
  public Future<AssetRequest> createAssetRequest(AssetRequest assetRequest) {
    // Implementation for creating an asset request
    return assetRequestDAO.create(assetRequest);
  }

  @Override
  public Future<PaginatedResult<AssetRequest>> getAllAssetRequest(PaginatedRequest paginatedRequest) {
    // Implementation for retrieving all asset requests
    return assetRequestDAO.getAll(paginatedRequest);
  }

  @Override
  public Future<Boolean> updateAssetRequestStatus(UUID requestId, Status status) {
    // Implementation for updating the status of an asset request
    Map<String, Object> conditionMap = Map.of(
      ASSET_REQUEST_ID, requestId.toString()
    );
    Map<String, Object> updateDataMap = Map.of(
      STATUS, status.getStatus(),
      UPDATED_AT, FORMATTER.format(LocalDateTime.now())
    );

    return assetRequestDAO.update(conditionMap, updateDataMap)
      .compose(v -> {
        if (Status.GRANTED.getStatus().equals(status.getStatus())) {
          return Future.succeededFuture(true);
        } else if (Status.REJECTED.getStatus().equals(status.getStatus())) {
          return Future.succeededFuture(true);
        } else {
          return Future.failedFuture(new BaseDxException("Invalid status for asset request"));
        }
      })
      .recover(throwable -> {
        if (throwable instanceof NoRowFoundException) {
          return Future.failedFuture(new DxNotFoundException("Asset request not found with ID: " + requestId));
        } else {
          LOGGER.error("Error updating asset request status", throwable);
          return Future.failedFuture(throwable);
        }
      });
  }

}
