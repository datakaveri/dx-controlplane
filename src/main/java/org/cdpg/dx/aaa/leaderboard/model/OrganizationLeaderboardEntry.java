package org.cdpg.dx.aaa.leaderboard.model;

public record OrganizationLeaderboardEntry(
    int rank,
    String id,
    String organizationName,
    String organizationType,
    String sector,
    int members,
    int publishedDatasets,
    int publishedModels,
    int publishedUsecases,
    int totalPublished,
    int downloads,
    int likes,
    int dislikes) {}
