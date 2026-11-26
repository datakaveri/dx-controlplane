package org.cdpg.dx.aaa.item.util;

import java.util.List;

public class GetItemRequest {
  private final String itemId;
  private final String subId;
  private String organizationId;
  private String did; // delegator id, if present
  private List<String> roles;
  // Scopes used for admin visibility checks (asset-management / org-asset-management).
  private List<String> scopes;
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

  public List<String> getScopes() {
    return scopes;
  }

  public void setScopes(List<String> scopes) {
    this.scopes = scopes;
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

  public String getOrganizationId() {
    return organizationId;
  }

  public void setOrganizationId(String organizationId) {
    this.organizationId = organizationId;
  }
}
