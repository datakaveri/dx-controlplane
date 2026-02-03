package org.cdpg.dx.aaa.item.service;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.asset.models.AssetRequest;
import org.cdpg.dx.aaa.asset.models.AssetRequestResponse;
import org.cdpg.dx.database.elastic.model.BulkSyncResult;
import org.cdpg.dx.aaa.common.ResponseModel;
import org.cdpg.dx.aaa.interaction.model.InteractionAggregate;
import org.cdpg.dx.aaa.item.model.Item;
import org.cdpg.dx.aaa.item.util.GetItemRequest;
import org.cdpg.dx.aaa.item.util.PatchItemRequest;
import org.cdpg.dx.database.elastic.model.ElasticsearchResponse;

import java.util.List;
import java.util.UUID;

public interface ItemService {
    public Future<Void> createItem(Item item);

    public Future<Void> updateItem(Item item);

    public Future<ElasticsearchResponse> deleteItem(String id);

    Future<Item> itemWithTheNameExists(String type, String name);

    Future<Void> ownerShipTransfer(String oldOwnerId, String newOwnerId, String organizationId);

    Future<ResponseModel> getItem(GetItemRequest request);

    Future<ResponseModel> getItemWithAccessChecks(GetItemRequest request);

    Future <List<AssetRequestResponse>> enrichWithAssetInfo(List<AssetRequest> assetRequests);

    Future<ElasticsearchResponse> patchItem(PatchItemRequest patchItemRequest);
    Future<Boolean> exists(String itemId);
    Future<Void> updateEngagementCounters(
        UUID entityId,
        int likeDelta,
        int dislikeDelta
    );
     Future<Void> updateMetric(
            UUID entityId,
            String metricField,
            int delta
    );
    Future<BulkSyncResult> bulkSyncMetrics(List<InteractionAggregate> aggregates);
}
