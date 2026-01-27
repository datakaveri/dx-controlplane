package org.cdpg.dx.aaa.interaction.v2.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import org.cdpg.dx.aaa.interaction.v2.enums.InteractionAction;
import org.cdpg.dx.auditing.enums.EntityType;

import java.util.UUID;

public record UserInteractionV2Request(
    UUID assetId, EntityType assetType, @JsonAlias({"actionType"}) InteractionAction action) {}
