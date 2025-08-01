package org.cdpg.dx.aaa.asset.handler;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.asset.models.AssetRequest;
import org.cdpg.dx.aaa.asset.models.Status;
import org.cdpg.dx.aaa.asset.service.AssetService;
import org.cdpg.dx.aaa.audit.util.AuditingHelper;
import org.cdpg.dx.aaa.credit.handler.CreditHandler;
import org.cdpg.dx.aaa.email.util.EmailComposer;
import org.cdpg.dx.aaa.organization.models.ProviderRoleRequest;
import org.cdpg.dx.auditing.model.AuditLog;
import org.cdpg.dx.common.exception.*;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.request.PaginationRequestBuilder;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.common.util.RoutingContextHelper;
import org.cdpg.dx.email.service.EmailService;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

import java.util.Set;
import java.util.UUID;

import static org.cdpg.dx.aaa.asset.util.Constants.ALLOWED_FILTER_MAP_FOR_ASSET_REQUEST;
import static org.cdpg.dx.aaa.asset.util.Constants.ASSET_REQUEST_ID;
import static org.cdpg.dx.aaa.credit.util.Constants.*;
import static org.cdpg.dx.aaa.credit.util.Constants.ALLOWED_FILTER_MAP_FOR_CREDIT_REQUEST;
import static org.cdpg.dx.aaa.credit.util.Constants.REQUESTED_AT;
import static org.cdpg.dx.database.postgres.util.Constants.DEFAULT_SORTING_ORDER;

public class AssetHandler {

  private static final Logger LOGGER = LogManager.getLogger(AssetHandler.class);
  private final AssetService assetService;
  private final EmailComposer emailComposer;
  private final KeycloakUserService keycloakUserService;


  public AssetHandler(AssetService assetService, EmailComposer emailComposer, KeycloakUserService keycloakUserService) {
    this.emailComposer = emailComposer;
    this.assetService = assetService;
    this.keycloakUserService = keycloakUserService;
  }


  public void createAssetRequest(RoutingContext ctx) {

    JsonObject assetRequestJson = ctx.body().asJsonObject();

    User user = ctx.user();
    LOGGER.debug("User: {}", user);
    if (user == null || user.subject() == null || user.principal() == null) {
      ctx.fail(new DxForbiddenException("User not found"));
      return;
    }

    String userIdStr = user.subject();

    if (userIdStr == null || userIdStr.isEmpty()) {
      ctx.fail(new DxForbiddenException("User not found"));
      return;
    }

     UUID userId = UUID.fromString(userIdStr);
    assetRequestJson.put("user_id", user.subject());
    AssetRequest assetRequest;
    try {
      assetRequest = AssetRequest.fromJson(assetRequestJson);
    } catch (Exception e) {
      ctx.fail(new DxBadRequestException("Invalid asset request body: " + e.getMessage()));
      return;
    }

    UUID assetId = assetRequest.assetId();
    if (assetId == null) {
      ctx.fail(new DxBadRequestException("Asset ID is required"));
      return;
    }

    assetService.getAssetRequestById(assetId,userId).onSuccess(existingRequest -> {
      if (existingRequest == true) {
        ctx.fail(new DxConflictException("Asset request already exists for asset ID and userId"));
      } else {
        keycloakUserService.getUserById(userId).compose(v -> {
          if (v.roles() != null && v.roles().contains("org_admin") && v.roles().contains("provider")) {
            LOGGER.info("User {} is authorized and creating asset request for asset {}", userId, assetId);
            return assetService.createAssetRequest(assetRequest);
          } else {
            return Future.failedFuture(new DxForbiddenException("User is not authorized to create asset request"));
          }
        }).onSuccess(requests -> {
          AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
            RoutingContextHelper.getRequestPath(ctx), "POST", "Created Asset Request");
          RoutingContextHelper.setAuditingLog(ctx, auditLog);
          ResponseBuilder.sendSuccess(ctx, "Created Asset Request");
        }).onFailure(ctx::fail);

      }
    }).onFailure(err -> {
      LOGGER.error("Error checking existing asset request: {}", err.getMessage(), err);
      ctx.fail(new DxInternalServerErrorException("Error checking existing asset request"));
    });




  }

  public void getAllAssetRequests(RoutingContext ctx) {
    PaginatedRequest request = PaginationRequestBuilder.from(ctx)
      .allowedFiltersDbMap(ALLOWED_FILTER_MAP_FOR_ASSET_REQUEST)
      .apiToDbMap(ALLOWED_FILTER_MAP_FOR_ASSET_REQUEST)
      .allowedTimeFields(Set.of(REQUESTED_AT, PROCESSED_AT))
      .defaultTimeField(REQUESTED_AT)
      .defaultSort(REQUESTED_AT, DEFAULT_SORTING_ORDER)
      .allowedSortFields(ALLOWED_FILTER_MAP_FOR_ASSET_REQUEST.keySet())
      .build();

    AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
      RoutingContextHelper.getRequestPath(ctx), "GET", "Get All Credit Requests");

    assetService.getAllAssetRequest(request)
      .onSuccess(result -> {
        RoutingContextHelper.setAuditingLog(ctx, auditLog);
        ResponseBuilder.sendSuccess(ctx,  result.data(), result.paginationInfo());
      })
      .onFailure(ctx::fail);

  }

  public void updateAssetRequestStatus(RoutingContext ctx) {
    JsonObject assetRequestJson = ctx.body().asJsonObject();

    User user = ctx.user();
    UUID userId = UUID.fromString(user.subject());

    Status status;
    try {
      status = Status.fromString(assetRequestJson.getString(STATUS));
    } catch (IllegalArgumentException e) {
      ctx.fail(new DxBadRequestException("Invalid status value"));
      return;
    }

    UUID requestId = UUID.fromString(ctx.pathParam("id"));


    if (userId == null) {
      ctx.fail(new DxForbiddenException("User not found"));
      return;
    }

    keycloakUserService.getUserById(userId).compose(v->{
      if(v.roles().contains("cos_admin"))
        return assetService.updateAssetRequestStatus(requestId,status);
      else
      {
        ctx.fail(new DxForbiddenException("User is not authorized to update asset request status"));
        return Future.failedFuture(new DxForbiddenException("User is not authorized to update asset request status"));
      }
    }).onSuccess(updatedAssetRequest -> {
      AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
        RoutingContextHelper.getRequestPath(ctx), "PUT", "Update Asset Request Status");
      RoutingContextHelper.setAuditingLog(ctx, auditLog);
      ResponseBuilder.sendSuccess(ctx, "Asset Request updated");

    }).onFailure(err -> {
      LOGGER.error("Failed to update asset request status: {}", err.getMessage(), err);
      ctx.fail(err);
    });
  }
}
