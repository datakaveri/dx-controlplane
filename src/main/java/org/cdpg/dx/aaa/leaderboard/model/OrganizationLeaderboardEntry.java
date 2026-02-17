package org.cdpg.dx.aaa.leaderboard.model;

public record OrganizationLeaderboardEntry(
    int rank,
    String id,
    String organizationName,
    String organizationType,
    long members,
    long publishedDatabanks,
    long publishedAiModels,
    long publishedUsecases,
    long totalPublished,
    long downloads,
    long likes) {}
