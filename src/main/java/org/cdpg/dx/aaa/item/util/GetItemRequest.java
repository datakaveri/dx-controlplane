package org.cdpg.dx.aaa.item.util;

import java.util.List;

public class GetItemRequest {
  private final String itemId;
  private final String subId;
  private String did; // delegator id, if present
  private List<String> roles;
  private String token;

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

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }

  public String getDid() {
    return did;
  }

  public void setDid(String did) {
    this.did = did;
  }
}
