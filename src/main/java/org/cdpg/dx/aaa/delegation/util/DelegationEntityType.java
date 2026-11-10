package org.cdpg.dx.aaa.delegation.util;

import org.cdpg.dx.common.exception.DxBadRequestException;

public enum DelegationEntityType {

  ORG("org"),
  PROVIDER("provider"),
  DATABANK("databank"),
  AIMODEL("aimodel");

  private final String type;

  DelegationEntityType(String type) {
    this.type = type;
  }

  public String getType() {
    return type;
  }

  public static DelegationEntityType fromString(String typeStr) {
    for (DelegationEntityType type : DelegationEntityType.values()) {
      if (type.type.equalsIgnoreCase(typeStr)) {
        return type;
      }
    }
    throw new DxBadRequestException("Invalid delegation entity type: " + typeStr);
  }
}
