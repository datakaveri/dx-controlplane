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
    long downloads,
    long likes,
    long views) {}
