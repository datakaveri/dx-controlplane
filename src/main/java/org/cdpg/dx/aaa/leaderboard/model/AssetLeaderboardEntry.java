package org.cdpg.dx.aaa.leaderboard.model;

public record AssetLeaderboardEntry(
    int rank,
    String id,
    String name,
    String type,
    String shortDescription,
    String accessPolicy,
    String providerId,
    String providerName,
    String organizationId,
    String organizationName,
    String organizationType,
    int downloads,
    int likes,
    int dislikes,
    int views) {}
