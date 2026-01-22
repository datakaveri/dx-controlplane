package org.cdpg.dx.aaa.interaction.model;

import org.cdpg.dx.common.util.PaginationInfo;

import java.util.List;

public record UserInteractionsPaginatedResponse(
    List<InteractionRow> data, PaginationInfo paginationInfo) {}
