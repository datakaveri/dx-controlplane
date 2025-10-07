package org.cdpg.dx.catalogueService.models;

public enum ItemType {
  AIMODEL("AIMODEL"),
  DATABANK("DATABANK"),
  RESOURCE_GROUP("RESOURCE_GROUP"),
  RESOURCE("RESOURCE");

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