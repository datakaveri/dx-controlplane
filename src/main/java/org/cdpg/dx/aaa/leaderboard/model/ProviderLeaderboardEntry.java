package org.cdpg.dx.aaa.leaderboard.model;

public record ProviderLeaderboardEntry(
    int rank,
    String id,
    String username,
    String organizationId,
    String organizationName,
    String organizationType,
    int publishedDatabank,
    int publishedAiModels,
    int publishedUsecases,
    int totalPublished,
    int downloads,
    int likes) {}
