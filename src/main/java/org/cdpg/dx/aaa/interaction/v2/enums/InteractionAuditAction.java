package org.cdpg.dx.aaa.interaction.v2.enums;

public enum InteractionAuditAction {
  LIKE("Like"),
  DISLIKE("Dislike"),
  NEUTRAL("Neutral"),
  BOOKMARK("Bookmark"),
  UNBOOKMARK("Unbookmark");

  private final String value;

  InteractionAuditAction(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
