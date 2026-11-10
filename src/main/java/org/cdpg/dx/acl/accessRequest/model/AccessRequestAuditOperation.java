package org.cdpg.dx.acl.accessRequest.model;

public enum AccessRequestAuditOperation {
  GRANT("Download Access Granted"),
  WITHDRAW("Download Access Withdrawn"),
  REJECT("Download Access Rejected"),
  REQUEST("Download Access Requested");

  private final String value;

  AccessRequestAuditOperation(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}