package org.cdpg.dx.aaa.item.service;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.item.model.Item;
import org.cdpg.dx.aaa.item.util.GetItemRequest;
import org.cdpg.dx.aaa.item.util.ItemFactory;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.exception.DxConflictException;
import org.cdpg.dx.common.exception.DxNotFoundException;

public class ItemFetchService {

  private final ItemService itemService;
  private final ItemService centralItemService;
  private final boolean isCentralCatEnabled;

  public ItemFetchService(
      ItemService itemService,
      ItemService centralItemService,
      boolean isCentralCatEnabled) {

    this.itemService = itemService;
    this.centralItemService = centralItemService;
    this.isCentralCatEnabled = isCentralCatEnabled;
  }

  public Future<Item> fetchForWrite(GetItemRequest request) {

    Future<Boolean> localExists =
        itemService.getItem(request)
            .map(res -> res.getTotalHits() > 0)
            .recover(err -> Future.succeededFuture(false));

    Future<Boolean> centralExists =
        isCentralCatEnabled
            ? centralItemService.getItem(request)
            .map(res -> res.getTotalHits() > 0)
            .recover(err -> Future.succeededFuture(false))
            : Future.succeededFuture(true);

    return Future.all(localExists, centralExists)
        .compose(cf -> {
          boolean local = cf.resultAt(0);
          boolean central = cf.resultAt(1);

          if (!local && !central) {
            return Future.failedFuture(
                new DxNotFoundException("Item not found for update"));
          }

          if (local != central) {
            return Future.failedFuture(
                new DxConflictException(
                    "Item exists in only one catalogue. Catalogue state is inconsistent"));
          }

          // Both exist → fetch from local (source of truth)
          return itemService.getItem(request)
              .map(res -> {
                JsonObject itemJson =
                    res.getElasticsearchResponses().getFirst();
                return ItemFactory.parse(itemJson);
              });
        });
  }
}

