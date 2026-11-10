package org.cdpg.dx.aaa.interaction.v2.model;

import java.util.List;

/** The {@code result} body of {@code GET /user/feedback}: rating summary plus the current page of feedback rows. */
public record UserFeedbackResult(RatingSummary summary, List<UserFeedbackResponse> data) {}
