package org.cdpg.dx.aaa.leaderboard.model;

public record OrganizationLeaderboardEntry(
    int rank,
    String id,
    String organizationName,
    int members,
    int publishedDatabanks,
    int publishedAiModels,
    int publishedUsecases,
    int totalPublished,
    int downloads,
    int likes,
    int dislikes) {}
