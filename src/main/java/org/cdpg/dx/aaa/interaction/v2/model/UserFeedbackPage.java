package org.cdpg.dx.aaa.interaction.v2.model;

import java.util.List;
import org.cdpg.dx.common.util.PaginationInfo;

/** Service-layer result of {@code GET /user/feedback}: enriched rows, the full-set rating summary, and pagination. */
public record UserFeedbackPage(
    RatingSummary summary, List<UserFeedbackResponse> data, PaginationInfo paginationInfo) {}
