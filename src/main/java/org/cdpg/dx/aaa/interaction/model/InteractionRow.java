package org.cdpg.dx.aaa.interaction.model;

import org.cdpg.dx.auditing.enums.EntityType;

public record InteractionRow(
        String entityId,
        EntityType entityType,
        boolean isBookmarked,
        boolean isLiked,
        boolean isDisliked) {}
