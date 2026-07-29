package org.cdpg.dx.aaa.asset.handler;

import static org.cdpg.dx.aaa.asset.util.Constants.*;
import static org.cdpg.dx.aaa.asset.util.Constants.STATUS;
import static org.cdpg.dx.aaa.credit.util.Constants.*;
import static org.cdpg.dx.aaa.credit.util.Constants.REQUESTED_AT;
import static org.cdpg.dx.database.postgres.util.Constants.DEFAULT_SORTING_ORDER;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.asset.models.AssetRequest;
import org.cdpg.dx.aaa.asset.models.Status;
import org.cdpg.dx.aaa.asset.service.AssetService;
import org.cdpg.dx.aaa.asset.util.AssetAuthAuditLogHelper;
import org.cdpg.dx.aaa.asset.util.AssetAuthAuditOperation;
import org.cdpg.dx.aaa.audit.util.AuditingHelper;
import org.cdpg.dx.aaa.email.util.EmailComposer;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.item.util.GetItemRequest;
import org.cdpg.dx.auditing.model.AuditLog;
import org.cdpg.dx.auditing.v2.model.UserActivityAuditLogBuilder;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.exception.*;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.request.PaginationRequestBuilder;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.common.util.CpRoutingContextHelper;
import org.cdpg.dx.common.util.RoutingContextHelper;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

public class AssetHandler {

  private static final Logger LOGGER = LogManager.getLogger(AssetHandler.class);
  private final AssetService assetService;
  private final ItemService itemService;
  private final EmailComposer emailComposer;
  private final KeycloakUserService keycloakUserService;
  private final URNGenerator urnGenerator;

  public AssetHandler(
      AssetService assetService,
      ItemService itemService,
      EmailComposer emailComposer,
      KeycloakUserService keycloakUserService,
      URNGenerator urnGenerator) {
    this.emailComposer = emailComposer;
    this.assetService = assetService;
    this.itemService = itemService;
    this.keycloakUserService = keycloakUserService;
    this.urnGenerator = urnGenerator;
  }

  public void createAssetRequest(RoutingContext ctx) {

    JsonObject assetRequestJson = ctx.body().asJsonObject();

    User user = ctx.user();
    LOGGER.debug("User: {}", user);
    if (user == null || user.subject() == null || user.principal() == null) {
      LOGGER.error("User not found in context");
      ctx.fail(new DxForbiddenException("User not found"));
      return;
    }

    String userIdStr = user.subject();
    String assetIdStr = assetRequestJson.getString(ASSET_ID);

    if (userIdStr == null || userIdStr.isEmpty()) {
      LOGGER.error("User ID is null or empty");
      ctx.fail(new DxForbiddenException("User not found"));
      return;
    }

    UUID userId = UUID.fromString(userIdStr);
    assetRequestJson.put("user_id", user.subject());
    AssetRequest assetRequest;
    try {
      assetRequest = AssetRequest.fromJson(assetRequestJson);
    } catch (Exception e) {
      LOGGER.error("Invalid asset request body: {}", e.getMessage(), e);
      ctx.fail(new DxBadRequestException("Invalid asset request body: " + e.getMessage()));
      return;
    }

    UUID assetId = assetRequest.assetId();
    if (assetId == null) {
      LOGGER.error("Asset ID is required for asset request");
      ctx.fail(new DxBadRequestException("Asset ID is required"));
      return;
    }

    GetItemRequest access = new GetItemRequest(assetIdStr,userIdStr);
    itemService.getItem(access).compose(res->
      assetService.getAssetRequestById(assetId,userId).onSuccess(existingRequest -> {
      if (existingRequest == true) {
        ctx.fail(new DxConflictException("Asset request already exists for asset ID and userId"));
      } else {
        keycloakUserService.getUserById(userId).compose(v -> {
          if (v.roles() != null && v.roles().contains("provider")) {
            LOGGER.info("User {} is authorized and creating asset request for asset {}", userId, assetId);
            return assetService.createAssetRequest(assetRequest);
          } else {
            return Future.failedFuture(new DxForbiddenException("User is not authorized to create asset request"));
          }
        }).onSuccess(requests -> {
//          AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
//            RoutingContextHelper.getRequestPath(ctx), "POST", "Created Asset Request");
//          RoutingContextHelper.setAuditingLog(ctx, auditLog);
          UserActivityAuditLogBuilder auditLogBuilder =
            AssetAuthAuditLogHelper.buildAudit(
              ctx, requests.toJson(),AssetAuthAuditOperation.CREATE);

          CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);
          ResponseBuilder.sendSuccess(ctx, "Created Asset Request", urnGenerator);

          emailComposer.sendEmailForAssetRequest(user);
        }).onFailure(ctx::fail);

      }
    }).onFailure(err -> {
      LOGGER.error("Error checking existing asset request: {}", err.getMessage(), err);
      ctx.fail(new DxInternalServerErrorException("Error checking existing asset request"));
    }));

  }

  public void getAllAssetRequests(RoutingContext ctx) {

    User dxUser = ctx.user();
    JsonObject userJson = dxUser.principal();

    //    AccessValidator.validate(
    //      userJson,
    //      List.of( // primary roles (no scope check)
    //        DxRole.COS_ADMIN.getRole()),
    //      List.of(DxScope.COS_ADMIN_ACCESS.getScope())
    //    );

    PaginatedRequest request =
        PaginationRequestBuilder.from(ctx)
            .allowedFiltersDbMap(ALLOWED_FILTER_MAP_FOR_ASSET_REQUEST)
            .fuzzyFiltersDbMap(Map.of(TYPE, TYPE))
            .apiToDbMap(ALLOWED_FILTER_MAP_FOR_ASSET_REQUEST)
            .allowedTimeFields(Set.of(REQUESTED_AT, PROCESSED_AT))
            .defaultTimeField(REQUESTED_AT)
            .defaultSort(REQUESTED_AT, DEFAULT_SORTING_ORDER)
            .allowedSortFields(ALLOWED_FILTER_MAP_FOR_ASSET_REQUEST.keySet())
            .build();

    AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
      RoutingContextHelper.getRequestPath(ctx), "GET", "Get All Asset Requests");

    assetService.getAllAssetRequest(request)
      .compose(result ->
        itemService.enrichWithAssetInfo(result.data())
          .map(enriched -> {
            LOGGER.info("Enriched Asset Requests count: {}", enriched.size());

            enriched.forEach(asset ->
              LOGGER.debug("Enriched Asset Request: {}", asset.getAssetRequest())
            );

            return Map.entry(enriched, result.paginationInfo());
          })
      )
      .onSuccess(entry -> {
        UserActivityAuditLogBuilder auditLogBuilder =
          AssetAuthAuditLogHelper.buildAudit(
            ctx, new JsonObject(),AssetAuthAuditOperation.GET);

        CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

        ResponseBuilder.sendSuccess(
          ctx,
          entry.getKey(),
          entry.getValue(),
          urnGenerator
        );
      })
      .onFailure(ctx::fail);
    }

  public void updateAssetRequestStatus(RoutingContext ctx) {
    JsonObject assetRequestJson = ctx.body().asJsonObject();

    User user = ctx.user();
    UUID userId = UUID.fromString(user.subject());

    JsonObject userJson = user.principal();
//
//    AccessValidator.validate(
//      userJson,
//      List.of( // primary roles (no scope check)
//        DxRole.COS_ADMIN.getRole()),
//      List.of(DxScope.COS_ADMIN_ACCESS.getScope())
//    );


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
//      AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
//        RoutingContextHelper.getRequestPath(ctx), "PUT", "Update Asset Request Status");
//      RoutingContextHelper.setAuditingLog(ctx, auditLog);

      UserActivityAuditLogBuilder auditLogBuilder =
        AssetAuthAuditLogHelper.buildAudit(
          ctx, new JsonObject().put(ASSET_REQUEST_ID,requestId.toString()),AssetAuthAuditOperation.UPDATE);

      CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);
      ResponseBuilder.sendSuccess(ctx, "Asset Request updated", urnGenerator);

      assetService.getAssetRequestDetailsById(requestId)
        .onSuccess(assetRequest ->
          emailComposer.sendUserEmailForAssetRequestApproval(assetRequest.userId(), status))
        .onFailure(err ->
          LOGGER.error("Failed to send asset request status email for request {}: {}",
            requestId, err.getMessage(), err));

    }).onFailure(err -> {
      LOGGER.error("Failed to update asset request status: {}", err.getMessage(), err);
      ctx.fail(err);
    });
  }

  public void deleteAssetRequest(RoutingContext ctx) {
    UUID assetRequestId = UUID.fromString(ctx.pathParam("id"));
    User user = ctx.user();
    UUID userId = UUID.fromString(user.subject());

    assetService.getAssetRequestDetailsById(assetRequestId).compose(v-> {
      if(v.status().equals(Status.PENDING.getStatus())) {
        if (v.userId().equals(userId)) {
          return assetService.deleteAssetRequestById(assetRequestId)
            .onSuccess(t -> {
//              AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
//                RoutingContextHelper.getRequestPath(ctx), "DELETE", "Deleted Asset Request");
//              RoutingContextHelper.setAuditingLog(ctx, auditLog);
              UserActivityAuditLogBuilder auditLogBuilder =
                AssetAuthAuditLogHelper.buildAudit(
                  ctx, new JsonObject().put(ASSET_REQUEST_ID,assetRequestId.toString()),AssetAuthAuditOperation.DELETE);

              CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);
              ResponseBuilder.sendSuccess(ctx, "Asset Request deleted successfully", urnGenerator);
            })
            .onFailure(ctx::fail);
        } else {
          ctx.fail(new DxForbiddenException("User is not authorized to delete this asset request"));
          return Future.failedFuture(new DxForbiddenException("User is not authorized to delete this asset request"));
        }
      }
      else
      {
        ctx.fail(new DxBadRequestException("Only pending asset requests can be deleted"));
        return Future.failedFuture(new DxBadRequestException("Only pending asset requests can be deleted"));
      }
    });
  }
}
