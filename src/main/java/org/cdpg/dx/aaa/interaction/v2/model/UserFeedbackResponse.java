package org.cdpg.dx.aaa.interaction.v2.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.vertx.core.json.JsonObject;
import java.time.LocalDateTime;
import java.util.UUID;

public record UserFeedbackResponse(
    UUID id,
    UUID userId,
    String userName,
    String organisation,
    UUID assetId,
    String assetType,
    String assetName,
    Integer entityRating,
    @JsonProperty("actionSubtype") String actionSubtype,
    @JsonProperty("actionSubdata") JsonObject actionSubdata,
    LocalDateTime ratingCreatedAt,
    LocalDateTime ratingUpdatedAt,
    @JsonProperty("feedbackStatus") String feedbackStatus,
    @JsonProperty("feedbackComment") String feedbackComment,
    @JsonProperty("feedbackStatusUpdatedAt") LocalDateTime feedbackStatusUpdatedAt) {

  public static UserFeedbackResponse from(
      UserFeedback feedback, String userName, String organisation, String assetName) {
    return new UserFeedbackResponse(
        feedback.id(),
        feedback.userId(),
        userName,
        organisation,
        feedback.assetId(),
        feedback.assetType(),
        assetName,
        feedback.entityRating(),
        feedback.actionSubType(),
        feedback.actionSubData(),
        feedback.ratingCreatedAt(),
        feedback.ratingUpdatedAt(),
        feedback.feedbackStatus(),
        feedback.feedbackComment(),
        feedback.feedbackStatusUpdatedAt());
  }
}
