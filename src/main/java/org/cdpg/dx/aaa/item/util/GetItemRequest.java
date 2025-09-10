package org.cdpg.dx.aaa.item.util;

import java.util.List;

public class GetItemRequest {
  private final String itemId;
  private final String subId;
  private List<String> roles;

  public GetItemRequest(String itemId, String subId) {
    this.itemId = itemId;
    this.subId = subId;
  }

  public List<String> getRoles() {
    return roles;
  }

  public void setRoles(List<String> roles) {
    this.roles = roles;
  }
  public String getItemId() {
    return itemId;
  }

  public String getSubId() {
    return subId;
  }
}
