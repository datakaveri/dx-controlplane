package org.cdpg.dx.aaa.grpc;

import static org.cdpg.dx.aaa.item.util.ItemExistenceValidator.getUtcDatetimeAsString;
import static org.cdpg.dx.database.elastic.util.Constants.PUBLISH_STATUS;

import io.grpc.stub.StreamObserver;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.common.Constants;
import org.cdpg.dx.aaa.common.ResponseModel;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.item.util.GetItemRequest;
import org.cdpg.dx.aaa.item.util.PatchItemRequest;
import org.cdpg.dx.auth.model.DxRole;
import org.cdpg.dx.cat.item.v1.CatItemServiceGrpc;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.exception.DxUnauthorizedException;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.database.elastic.model.ElasticsearchResponse;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

/**
 * gRPC counterparts of the HTTP catalogue item endpoints in {@code ItemController}:
 *
 * <ul>
 *   <li>{@link #getItem} — mirrors {@code GET /iudx/v2/cat/item} → {@code ItemService#getItem}
 *   <li>{@link #patchItem} — mirrors {@code PATCH /iudx/v2/cat/item} (operationId {@code patch item
 *       metadata}, handler {@code ItemController#handlePatchItemMetaData}) → {@code
 *       ItemService#patchItem}
 * </ul>
 *
 * <p>The HTTP handlers derive the caller's identity from the bearer JWT; over gRPC the acting
 * user's {@code userId}/{@code roles} are forwarded in the request (the channel itself is
 * authenticated by {@link org.cdpg.dx.aaa.grpc.auth.ServiceAuthInterceptor}). As in the HTTP path,
 * the caller's {@code organizationId} is resolved from Keycloak when not supplied. Audit logging,
 * which is bound to the HTTP RoutingContext, is intentionally not replicated here.
 */
public class CatItemGrpcService extends CatItemServiceGrpc.CatItemServiceImplBase {

  private static final Logger LOGGER = LogManager.getLogger(CatItemGrpcService.class);

  // Immutable fields no caller may set via PATCH /cat/item — mirrors handlePatchItemMetaData.
  private static final Set<String> RESTRICTED_FIELDS =
      Set.of(
          Constants.PROVIDER_USER_ID,
          Constants.ORGANIZATION_ID,
          Constants.ITEM_CREATED_AT,
          Constants.METRICS);

  private final Context vertxContext;
  private final ItemService itemService;
  private final KeycloakUserService keycloakUserService;

  public CatItemGrpcService(
      Vertx vertx, ItemService itemService, KeycloakUserService keycloakUserService) {
    this.vertxContext = vertx.getOrCreateContext();
    this.itemService = itemService;
    this.keycloakUserService = keycloakUserService;
  }

  /* ── GetItem ───────────────────────────────────────────────────────────── */

  @Override
  public void getItem(
      org.cdpg.dx.cat.item.v1.GetItemRequest request,
      StreamObserver<org.cdpg.dx.cat.item.v1.GetItemResponse> observer) {
    String itemId = request.getItemId();
    if (itemId == null || itemId.isBlank()) {
      respond(observer, failGetItem("NOT_FOUND"));
      return;
    }

    String userId = request.getUserId();
    List<String> roles = request.getRolesList();
    String reqOrgId = request.getOrganizationId();

    vertxContext.runOnContext(
        ignored ->
            resolveOrganizationId(userId, reqOrgId)
                .compose(
                    orgId -> {
                      GetItemRequest itemReq = new GetItemRequest(itemId, userId);
                      itemReq.setRoles(roles);
                      itemReq.setOrganizationId(orgId);
                      return itemService.getItem(itemReq);
                    })
                .onSuccess(
                    responseModel -> respond(observer, toGetItemResponse(responseModel)))
                .onFailure(
                    err -> {
                      LOGGER.error("GetItem failed for itemId={}: {}", itemId, err.getMessage());
                      respond(observer, failGetItem(mapGetItemError(err)));
                    }));
  }

  private org.cdpg.dx.cat.item.v1.GetItemResponse toGetItemResponse(ResponseModel responseModel) {
    if (responseModel.getTotalHits() == 0) {
      return failGetItem("NOT_FOUND");
    }
    JsonObject item =
        responseModel.getResponse().getJsonArray(Constants.RESULTS).getJsonObject(0);
    return org.cdpg.dx.cat.item.v1.GetItemResponse.newBuilder()
        .setSuccess(true)
        .setItemJson(item.encode())
        .setTotalHits(responseModel.getTotalHits())
        .build();
  }

  /* ── PatchItem ─────────────────────────────────────────────────────────── */

  @Override
  public void patchItem(
      org.cdpg.dx.cat.item.v1.PatchItemRequest request,
      StreamObserver<org.cdpg.dx.cat.item.v1.PatchItemResponse> observer) {
    String itemId = request.getItemId();
    if (itemId == null || itemId.isBlank()) {
      respond(observer, failPatchItem("BAD_REQUEST", itemId));
      return;
    }

    String userId = request.getUserId();
    if (userId == null || userId.isBlank()) {
      respond(observer, failPatchItem("FORBIDDEN", itemId));
      return;
    }

    JsonObject body;
    try {
      body = new JsonObject(request.getPatchJson());
    } catch (Exception e) {
      respond(observer, failPatchItem("BAD_REQUEST", itemId));
      return;
    }
    if (body.isEmpty()) {
      respond(observer, failPatchItem("BAD_REQUEST", itemId));
      return;
    }

    List<String> roles = request.getRolesList();
    boolean isAdmin =
        roles.contains(DxRole.ORG_ADMIN.value()) || roles.contains(DxRole.COS_ADMIN.value());

    // Only admins may patch publishStatus — mirrors handlePatchItemMetaData.
    if (!isAdmin && body.containsKey(PUBLISH_STATUS)) {
      respond(observer, failPatchItem("FORBIDDEN", itemId));
      return;
    }

    // Immutable fields can never be set via PATCH /cat/item.
    for (String field : RESTRICTED_FIELDS) {
      if (body.containsKey(field)) {
        respond(observer, failPatchItem("FORBIDDEN", itemId));
        return;
      }
    }

    // Server-managed timestamp, like the HTTP handler.
    body.put(Constants.LAST_UPDATED, getUtcDatetimeAsString());

    vertxContext.runOnContext(
        ignored ->
            resolveOrganizationId(userId, request.getOrganizationId())
                .compose(
                    orgId -> {
                      PatchItemRequest patchReq =
                          new PatchItemRequest(itemId, orgId, userId, body, roles);
                      return itemService.patchItem(patchReq);
                    })
                .onSuccess(esResponse -> respond(observer, toPatchItemResponse(itemId, esResponse)))
                .onFailure(
                    err -> {
                      LOGGER.error("PatchItem failed for itemId={}: {}", itemId, err.getMessage());
                      respond(observer, failPatchItem(mapPatchItemError(err), itemId));
                    }));
  }

  private org.cdpg.dx.cat.item.v1.PatchItemResponse toPatchItemResponse(
      String itemId, ElasticsearchResponse esResponse) {
    JsonObject source = esResponse != null ? esResponse.getSource() : null;
    return org.cdpg.dx.cat.item.v1.PatchItemResponse.newBuilder()
        .setSuccess(true)
        .setItemId(itemId)
        .setItemJson(source != null ? source.encode() : "{}")
        .build();
  }

  /* ── Helpers ───────────────────────────────────────────────────────────── */

  /**
   * Resolve the acting user's organisation id. Mirrors the HTTP handlers: an explicit value wins;
   * otherwise it is fetched from Keycloak by {@code userId}. Anonymous callers (no userId) and
   * Keycloak lookup failures yield {@code null}, matching the HTTP behaviour for public items.
   */
  private Future<String> resolveOrganizationId(String userId, String orgId) {
    if (orgId != null && !orgId.isBlank()) {
      return Future.succeededFuture(orgId);
    }
    if (userId == null || userId.isBlank()) {
      return Future.succeededFuture(null);
    }
    return keycloakUserService
        .getUserById(UUID.fromString(userId))
        .map(DxUser::organisationId)
        .recover(
            err -> {
              LOGGER.warn(
                  "Could not resolve organisationId for userId={}: {}", userId, err.getMessage());
              return Future.succeededFuture(null);
            });
  }

  private <T> void respond(StreamObserver<T> observer, T response) {
    observer.onNext(response);
    observer.onCompleted();
  }

  private org.cdpg.dx.cat.item.v1.GetItemResponse failGetItem(String errorCode) {
    return org.cdpg.dx.cat.item.v1.GetItemResponse.newBuilder()
        .setSuccess(false)
        .setErrorCode(errorCode)
        .build();
  }

  private org.cdpg.dx.cat.item.v1.PatchItemResponse failPatchItem(String errorCode, String itemId) {
    return org.cdpg.dx.cat.item.v1.PatchItemResponse.newBuilder()
        .setSuccess(false)
        .setErrorCode(errorCode)
        .setItemId(itemId == null ? "" : itemId)
        .build();
  }

  private String mapGetItemError(Throwable err) {
    if (err instanceof DxUnauthorizedException) {
      return "UNAUTHORIZED";
    }
    if (err instanceof DxForbiddenException) {
      return "FORBIDDEN";
    }
    if (err instanceof DxNotFoundException) {
      return "NOT_FOUND";
    }
    return "INTERNAL_ERROR";
  }

  private String mapPatchItemError(Throwable err) {
    if (err instanceof DxForbiddenException) {
      return "FORBIDDEN";
    }
    if (err instanceof DxNotFoundException) {
      return "NOT_FOUND";
    }
    return "BAD_REQUEST";
  }
}
