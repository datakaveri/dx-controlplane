package org.cdpg.dx.aaa.bookmarks.model;

import java.util.Arrays;

public enum BookmarkEntityType {
  DATABANK("databank"),
  AI_MODEL("ai_model"),
  USECASE("usecase"),
  APP("app");

  private final String value;

  BookmarkEntityType(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public static BookmarkEntityType fromValue(String value) {
    return Arrays.stream(values())
        .filter(v -> v.value.equalsIgnoreCase(value))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Invalid entityType. Allowed values: dataset, model, usecase, app"));
  }
}
