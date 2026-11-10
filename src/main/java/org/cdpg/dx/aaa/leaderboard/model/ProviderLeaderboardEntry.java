package org.cdpg.dx.aaa.leaderboard.model;

public record ProviderLeaderboardEntry(
    int rank,
    String id,
    String username,
    String organizationId,
    String organizationName,
    String organizationType,
    long publishedDatabank,
    long publishedAiModels,
    long publishedUsecases,
    long totalPublished,
    long downloads,
    long likes) {}
