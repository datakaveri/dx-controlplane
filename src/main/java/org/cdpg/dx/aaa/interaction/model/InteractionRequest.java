package org.cdpg.dx.aaa.interaction.model;

import java.util.UUID;

import org.cdpg.dx.aaa.interaction.enums.ActionType;
import org.cdpg.dx.aaa.interaction.enums.InteractionValue;
import org.cdpg.dx.auditing.enums.EntityType;

public record InteractionRequest(
    UUID entityId, EntityType entityType, ActionType actionType, InteractionValue value) {}
