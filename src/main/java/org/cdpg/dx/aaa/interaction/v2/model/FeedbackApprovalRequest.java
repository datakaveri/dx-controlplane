package org.cdpg.dx.aaa.interaction.v2.model;

import io.vertx.core.json.JsonObject;

public record FeedbackApprovalRequest(FeedbackStatus status, String comment) {

  public static FeedbackApprovalRequest fromJson(JsonObject json) {

    String statusValue = json.getString("status");

    if (statusValue == null || statusValue.isBlank()) {
      throw new IllegalArgumentException("status is required");
    }

    FeedbackStatus status;

    try {
      status = FeedbackStatus.valueOf(statusValue.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("status must be APPROVED or REJECTED");
    }

    if (status != FeedbackStatus.APPROVED && status != FeedbackStatus.REJECTED) {

      throw new IllegalArgumentException("Only APPROVED or REJECTED status is allowed");
    }

    String comment = json.getString("comment");

    if (status == FeedbackStatus.REJECTED && (comment == null || comment.isBlank())) {

      throw new IllegalArgumentException("comment is required when rejecting feedback");
    }

    return new FeedbackApprovalRequest(status, comment);
  }
}
