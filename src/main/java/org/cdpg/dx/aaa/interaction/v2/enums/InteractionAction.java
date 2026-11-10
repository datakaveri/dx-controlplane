package org.cdpg.dx.aaa.interaction.v2.enums;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum InteractionAction {
  LIKE,
  DISLIKE,
  BOOKMARK,
  UNBOOKMARK,
  NEUTRAL;

  @JsonCreator
  public static InteractionAction fromString(String value) {
    if (value == null) {
      throw new IllegalArgumentException("action cannot be null");
    }

    return InteractionAction.valueOf(value.trim().toUpperCase());
  }

  @JsonValue
  public String toJson() {
    return name();
  }
}
