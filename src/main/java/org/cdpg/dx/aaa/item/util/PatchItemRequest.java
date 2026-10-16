package org.cdpg.dx.aaa.item.util;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.List;

public class PatchItemRequest {

    private final String itemId;
    private final String orgId;
    private final String userId;
    private final JsonObject requestBody;
  private  JsonArray allowedScopes;
  private List<String> allowedRoles;
  /**
   * Non-null only when the caller is a dept_admin (not the org_admin):
   * the ids of their administered department and all its descendants. When
   * set, {@code ItemServiceImpl.patchItem} must restrict the match to these
   * departments instead of the whole organization.
   */
  private List<String> deptScopedNodeIds;

  public PatchItemRequest(
      String itemId, String subId, String userId, JsonObject requestBody, JsonArray allowedScopes,
      List<String> allowedRoles) {
        this.itemId = itemId;
        this.orgId = subId;
      this.userId = userId;
      this.requestBody = requestBody;
      this.allowedScopes = allowedScopes;
      this.allowedRoles = allowedRoles;
    }

    public String getItemId() {
        return itemId;
    }

    public String getOrgId() {
        return orgId;
    }

    public JsonObject getRequestBody() {
        return requestBody;
    }

  public JsonArray getAllowedScopes() {
    return allowedScopes;
    }

  public List<String> getAllowedRoles() {
    return allowedRoles;
  }

  public String getUserId() {
        return userId;
    }

  public List<String> getDeptScopedNodeIds() {
    return deptScopedNodeIds;
  }

  public void setDeptScopedNodeIds(List<String> deptScopedNodeIds) {
    this.deptScopedNodeIds = deptScopedNodeIds;
  }
}


