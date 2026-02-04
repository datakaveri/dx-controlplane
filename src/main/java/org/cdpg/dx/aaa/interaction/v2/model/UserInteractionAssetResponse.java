package org.cdpg.dx.aaa.interaction.v2.model;

/**
 * API response DTO representing: - Asset metadata (from Elasticsearch) - User-specific interaction
 * state (from Postgres)
 *
 * <p>This class MUST NOT be used in DAO or DB layers.
 */
public record UserInteractionAssetResponse(
    ItemSummary asset, UserInteractionState userInteraction) {}
