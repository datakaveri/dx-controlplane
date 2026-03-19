package org.cdpg.dx.aaa.asset.util;

public enum AssetAuthAuditOperation
{
  CREATE("Create data upload type request SFTP/API"),
  GET_DATA_INGESTION_TYPE("Get data upload type requests"),
  DELETE("Delete pending data type upload request"),
  UPDATE("Update data upload type requests");

  private final String value;

  AssetAuthAuditOperation(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
