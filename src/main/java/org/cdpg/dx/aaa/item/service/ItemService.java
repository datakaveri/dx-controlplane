package org.cdpg.dx.aaa.item.service;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.common.ResponseModel;
import org.cdpg.dx.aaa.item.model.Item;
import org.cdpg.dx.aaa.item.util.GetItemRequest;
import org.cdpg.dx.aaa.item.util.PatchItemRequest;
import org.cdpg.dx.database.elastic.model.ElasticsearchResponse;

public interface ItemService {
    public Future<Void> createItem(Item item);

    public Future<Void> updateItem(Item item);

    public Future<ElasticsearchResponse> deleteItem(String id);

    Future<Item> itemWithTheNameExists(String type, String name);

    Future<Void> ownerShipTransfer(String oldOwnerId, String newOwnerId, String organizationId);

    Future<ResponseModel> getItem(GetItemRequest request);

    Future<ResponseModel> getItemWithAccessChecks(GetItemRequest request);

    Future<ElasticsearchResponse> patchItem(PatchItemRequest patchItemRequest);
    Future<Boolean> exists(String itemId);
}
