package org.cdpg.dx.aaa.item.util;

public class GetItemRequest {
  private final String itemId;
  private final String subId;

  public GetItemRequest(String itemId, String subId) {
    this.itemId = itemId;
    this.subId = subId;
  }

  public String getItemId() {
    return itemId;
  }

  public String getSubId() {
    return subId;
  }
}
