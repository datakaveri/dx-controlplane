package org.cdpg.dx.aaa.item.enums;

public enum ItemAuditOperation {
  VIEW("View"),
  DOWNLOAD("Download"),
  UPLOAD("Upload"),
  CREATE("Create"),
  UPDATE("Update"),
  DELETE("Delete");

  private final String value;

  ItemAuditOperation(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
