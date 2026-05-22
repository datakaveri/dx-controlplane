package org.cdpg.dx.acl.accessRequest.dao.model;

import io.vertx.core.json.JsonObject;
import java.util.List;
import org.cdpg.dx.common.util.PaginationInfo;

public class HasAccessResponse {
  private List<PolicyAccessInfo> results;
  private PaginationInfo paginationInfo;

  public HasAccessResponse() {}

  public HasAccessResponse(List<PolicyAccessInfo> results,
                           PaginationInfo paginationInfo) {
    this.results = results;
    this.paginationInfo = paginationInfo;
  }

  public List<PolicyAccessInfo> getResults() {
    return results;
  }

  public void setResults(List<PolicyAccessInfo> results) {
    this.results = results;
  }

  public PaginationInfo getPaginationInfo() {
    return paginationInfo;
  }

  public void setPaginationInfo(PaginationInfo paginationInfo) {
    this.paginationInfo = paginationInfo;
  }

  public JsonObject toJson() {
    return JsonObject.mapFrom(this);
  }
}
