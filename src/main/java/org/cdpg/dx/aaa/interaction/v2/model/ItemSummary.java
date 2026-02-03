package org.cdpg.dx.aaa.interaction.v2.model;

import io.vertx.core.json.JsonObject;

public record ItemSummary(
    String id,
    String name,
    String sortDescription,
    String type,
    String accessPolicy,
    String ownerUserId,
    String ownerUserName,
    String organizationId,
    String organizationName,
    JsonObject metrics) {}
