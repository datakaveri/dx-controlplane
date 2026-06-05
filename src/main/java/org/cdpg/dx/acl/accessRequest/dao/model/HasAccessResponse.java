package org.cdpg.dx.acl.accessRequest.dao.model;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.List;
import org.cdpg.dx.acl.policy.dao.model.PolicyDto;
import org.cdpg.dx.common.util.PaginationInfo;

public class HasAccessResponse {
  private List<PolicyDto> policies;
  private boolean hasPendingRequests;

  private List<AccessRequestDto> pendingRequests;
  private PaginationInfo paginationInfo;

  public HasAccessResponse() {}

  public HasAccessResponse(List<PolicyDto> policies, PaginationInfo paginationInfo) {
    this.policies = policies;
    this.paginationInfo = paginationInfo;
  }

  public List<PolicyDto> getPolicies() {
    return policies;
  }

  public void setPolicies(List<PolicyDto> policies) {
    this.policies = policies;
  }

  public PaginationInfo getPaginationInfo() {
    return paginationInfo;
  }

  public void setPaginationInfo(PaginationInfo paginationInfo) {
    this.paginationInfo = paginationInfo;
  }

  public boolean isHasPendingRequests() {
    return hasPendingRequests;
  }

  public void setHasPendingRequests(boolean hasPendingRequests) {
    this.hasPendingRequests = hasPendingRequests;
  }

  public List<AccessRequestDto> getPendingRequests() {
    return pendingRequests;
  }

  public void setPendingRequests(List<AccessRequestDto> pendingRequests) {
    this.pendingRequests = pendingRequests;
  }

  public JsonObject toJson() {

    JsonObject response = new JsonObject();

    if (policies != null) {
      response.put(
          "policies",
          policies.stream()
              .map(PolicyDto::toJson)
              .collect(JsonArray::new, JsonArray::add, JsonArray::addAll));
    }

    response.put("hasPendingRequests", hasPendingRequests);

    if (pendingRequests != null) {
      response.put(
          "pendingRequests",
          pendingRequests.stream()
              .map(AccessRequestDto::toJson)
              .collect(JsonArray::new, JsonArray::add, JsonArray::addAll));
    }

    if (paginationInfo != null) {
      response.put("pagination", JsonObject.mapFrom(paginationInfo));
    }

    return response;
  }
}
