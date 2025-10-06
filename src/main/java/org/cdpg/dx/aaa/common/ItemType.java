package org.cdpg.dx.aaa.common;

import static org.cdpg.dx.aaa.common.Constants.*;

public enum ItemType {
  AI_MODEL("AIMODEL"),
  DATA_BANK("DATABANK"),
  APPS(ITEM_TYPE_APPS);

  private final String typeValue;

  ItemType(String typeValue) {
    this.typeValue = typeValue;
  }

  public static ItemType fromTypeValue(String value) {
    for (ItemType type : ItemType.values()) {
      if (type.getTypeValue().equalsIgnoreCase(value)) {
        return type;
      }
    }
    throw new IllegalArgumentException("No matching ItemType for: " + value);
  }

  public String getTypeValue() {
    return typeValue;
  }
}
