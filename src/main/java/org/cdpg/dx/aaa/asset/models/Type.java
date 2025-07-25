package org.cdpg.dx.aaa.asset.models;

public enum Type {
  SFTP("SFTP"),
  API("API");

  private final String typeName;

  Type(String typeName) {
    this.typeName = typeName;
  }

  public String getTypeName() {
    return typeName;
  }

  private static Type temp;
  public static Type fromString(String typeStr) {
    for (Type type : Type.values()) {
      if (type.getTypeName().equalsIgnoreCase(typeStr))
       return type;
    }

    throw new IllegalArgumentException("Invalid role: " + typeStr);

  }

}

