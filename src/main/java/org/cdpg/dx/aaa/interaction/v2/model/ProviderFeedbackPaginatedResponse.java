package org.cdpg.dx.aaa.interaction.v2.model;

import org.cdpg.dx.common.util.PaginationInfo;

import java.util.List;

public record ProviderFeedbackPaginatedResponse(
  List<ProviderFeedbackByAsset> data, PaginationInfo paginationInfo) {}
