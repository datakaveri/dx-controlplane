package org.cdpg.dx.catalogueService.models;

public enum ItemType {
  AIMODEL("AIMODEL"),
  DATABANK("DATABANK"),
  APPS("APPS"),
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

  public static ItemType fromCatalogueItemType(String value) {
    if (value == null) {
      throw new IllegalArgumentException("Item type cannot be null");
    }

    // Handle cases like: adex:AiModel, adex:DataBank, adex:Apps
    if (value.contains(":")) {
      value = value.substring(value.indexOf(":") + 1);
    }

    return fromTypeValue(value.toUpperCase());
  }

  public String getTypeValue() {
    return typeValue;
  }
}