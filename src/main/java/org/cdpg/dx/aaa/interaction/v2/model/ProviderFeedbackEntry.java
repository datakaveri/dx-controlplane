package org.cdpg.dx.aaa.interaction.v2.model;

import java.time.LocalDateTime;
import java.util.UUID;
import org.cdpg.dx.aaa.interaction.v2.enums.ProviderFeedbackType;

public record ProviderFeedbackEntry(
    ProviderFeedbackType type,
    Object data,
    UUID userId,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {}
