package org.cdpg.dx.aaa.interaction.v2.enums;

public enum InteractionAuditAction {
  LIKE("Like"),
  DISLIKE("Dislike"),
  NEUTRAL("Neutral"),
  BOOKMARK("Bookmark"),
  UNBOOKMARK("Unbookmark"),
  RATING("Rating"),
  VIEW_RATING("View Rating"),
  REMOVE_RATING("Remove Rating"),
  PROVIDER_FEEDBACK("Provider Feedback"),
  VIEW_PROVIDER_FEEDBACK("View Provider Feedback"),
  REMOVE_PROVIDER_FEEDBACK("Remove Provider Feedback"),
  APPROVE_RATING("Approve Rating"),
  REJECT_RATING("Reject Rating");
  private final String value;

  InteractionAuditAction(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
