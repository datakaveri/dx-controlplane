package org.cdpg.dx.aaa.item.service;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.common.ResponseModel;
import org.cdpg.dx.aaa.item.model.Item;
import org.cdpg.dx.aaa.item.util.GetItemRequest;
import org.cdpg.dx.aaa.item.util.PatchItemRequest;

public interface ItemService {
  public Future<Void> createItem(Item item);

  public Future<Void> updateItem(Item item);

  public Future<Void> deleteItem(String id);

  Future<Item> itemWithTheNameExists(String type, String name);

  Future<ResponseModel> getItem(GetItemRequest request);
  Future<Void> patchItem(PatchItemRequest patchItemRequest);
}
