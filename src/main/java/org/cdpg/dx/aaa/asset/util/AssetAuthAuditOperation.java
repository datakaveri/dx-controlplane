package org.cdpg.dx.aaa.asset.util;

public enum AssetAuthAuditOperation
{
  CREATE("Create asset upload request"),
  GET("Get asset requests"),
  DELETE("Delete pending asset request"),
  UPDATE("Update asset requests");

  private final String value;

  AssetAuthAuditOperation(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
