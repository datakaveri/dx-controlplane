package org.cdpg.dx.aaa.interaction.v2.model;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public record ItemSummary(
    String id,
    String name,
    String sortDescription,
    String type,
    String accessPolicy,
    String ownerUserId,
    String organizationId,
    String organizationName,
    String uploadedBy,
    JsonArray resourceServer,
    String fileFormat,
    String industry,
    JsonArray tags,
    String itemCreatedAt,
    JsonObject metrics) {}
