package org.cdpg.dx.acl.accessRequest.dao.model;

import java.util.List;

public class AccessRequestSummary {
  private boolean hasGrantedAccess;
  private boolean hasPendingRequests;
  private boolean hasRejectedRequests;
  private List<AccessRequestDto> pendingRequests;

  public AccessRequestSummary(boolean hasGrantedAccess, boolean hasPendingRequests,
                              boolean hasRejectedRequests,
                              List<AccessRequestDto> pendingRequests) {
    this.hasGrantedAccess = hasGrantedAccess;
    this.hasPendingRequests = hasPendingRequests;
    this.pendingRequests = pendingRequests;
  }

  public boolean isHasRejectedRequests() {
    return hasRejectedRequests;
  }

  public void setHasRejectedRequests(boolean hasRejectedRequests) {
    this.hasRejectedRequests = hasRejectedRequests;
  }

  public boolean isHasGrantedAccess() {
    return hasGrantedAccess;
  }

  public void setHasGrantedAccess(boolean hasGrantedAccess) {
    this.hasGrantedAccess = hasGrantedAccess;
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
}
