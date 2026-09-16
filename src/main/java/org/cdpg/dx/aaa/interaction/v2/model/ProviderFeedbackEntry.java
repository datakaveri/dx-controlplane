package org.cdpg.dx.aaa.interaction.v2.model;

import io.vertx.core.json.JsonArray;

import java.time.LocalDateTime;
import java.util.UUID;

import org.cdpg.dx.aaa.interaction.v2.enums.ProviderFeedbackType;

public record ProviderFeedbackEntry(
    ProviderFeedbackType type,
    JsonArray data,
    UUID userId,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {}
