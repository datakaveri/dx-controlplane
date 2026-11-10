package org.cdpg.dx.aaa.interaction.model;

import java.util.UUID;
import org.cdpg.dx.auditing.enums.EntityType;

public record InteractionAggregate(
    UUID entityId,
    EntityType entityType,
    int likes,
    int dislikes,
    int bookmarks
) {}

