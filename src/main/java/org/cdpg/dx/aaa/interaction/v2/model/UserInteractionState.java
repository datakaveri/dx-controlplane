package org.cdpg.dx.aaa.interaction.v2.model;

/** User-specific interaction state for an asset. Stored in Postgres and computed per user. */
public record UserInteractionState(boolean liked, boolean disliked, boolean bookmarked) {}
