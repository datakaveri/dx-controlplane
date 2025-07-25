package org.cdpg.dx.aaa.asset.service;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.asset.models.AssetRequest;
import org.cdpg.dx.aaa.asset.models.Status;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.database.postgres.models.PaginatedResult;

import java.util.UUID;

public interface AssetService {

  Future<AssetRequest> createAssetRequest(AssetRequest assetRequest);

  Future<PaginatedResult<AssetRequest>> getAllAssetRequest(PaginatedRequest paginatedRequest);

  Future<Boolean> updateAssetRequestStatus(UUID requestId, Status status);

}
