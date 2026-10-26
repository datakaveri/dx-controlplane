package org.cdpg.dx.aaa.item.service;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.item.util.PatchItemRequest;
import org.cdpg.dx.database.elastic.model.ElasticsearchResponse;

/**
 * Applies an item PATCH to the local catalogue and, when the central catalogue is enabled, to the
 * central catalogue as well — mirroring how create/update/delete are kept in sync.
 *
 * <p>Order: central first (with one retry), then local. If the local patch fails, the patched
 * fields in central are reverted to their pre-patch values.
 */
public class CentralSyncedItemPatcher {
  private static final Logger LOGGER = LogManager.getLogger(CentralSyncedItemPatcher.class);

  private final ItemService itemService;
  private final ItemService centralItemService;
  private final boolean isCentralCatEnabled;

  public CentralSyncedItemPatcher(
      ItemService itemService, ItemService centralItemService, boolean isCentralCatEnabled) {
    this.itemService = itemService;
    this.centralItemService = centralItemService;
    this.isCentralCatEnabled = isCentralCatEnabled && centralItemService != null;
  }

  /**
   * @return the local item's pre-patch document, same as {@link ItemService#patchItem}
   */
  public Future<ElasticsearchResponse> patchItem(PatchItemRequest request) {
    if (!isCentralCatEnabled) {
      return itemService.patchItem(request);
    }

    return centralItemService
        .patchItem(request)
        .recover(
            err -> {
              LOGGER.warn("Central catalogue patch failed, retrying once", err);
              return centralItemService.patchItem(request);
            })
        .onFailure(
            err -> LOGGER.error("Central catalogue patch failed after retry: {}", err.getMessage()))
        .compose(
            centralBefore ->
                itemService
                    .patchItem(request)
                    .recover(
                        localErr -> {
                          LOGGER.error(
                              "Local patch failed, rolling back central catalogue", localErr);
                          return rollbackCentral(request, centralBefore)
                              .transform(ignored -> Future.failedFuture(localErr));
                        }));
  }

  private Future<ElasticsearchResponse> rollbackCentral(
      PatchItemRequest request, ElasticsearchResponse centralBefore) {
    JsonObject before =
        centralBefore != null && centralBefore.getSource() != null
            ? centralBefore.getSource()
            : new JsonObject();

    // Restore only the fields this patch touched; fields absent before are reset to null.
    JsonObject revertBody = new JsonObject();
    for (String field : request.getRequestBody().fieldNames()) {
      revertBody.put(field, before.getValue(field));
    }

    PatchItemRequest revert =
        new PatchItemRequest(
            request.getItemId(),
            request.getOrgId(),
            request.getUserId(),
            revertBody,
            request.getAllowedRoles());

    return centralItemService
        .patchItem(revert)
        .onFailure(
            err ->
                LOGGER.error(
                    "Central catalogue rollback failed for item {}", request.getItemId(), err));
  }
}
