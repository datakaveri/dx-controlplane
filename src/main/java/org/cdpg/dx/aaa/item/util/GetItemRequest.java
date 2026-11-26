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
  // When true, a PRIVATE item is also readable by users it was shared with (directly or via
  // their organisation). Only for read-only metadata views such as GET /item -- never for
  // write, delete or data-access flows.
  private boolean includeSharedAccess;

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

  public boolean isIncludeSharedAccess() {
    return includeSharedAccess;
  }

  public void setIncludeSharedAccess(boolean includeSharedAccess) {
    this.includeSharedAccess = includeSharedAccess;
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
