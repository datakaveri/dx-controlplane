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
    int downloads,
    int likes,
    int dislikes,
    int views,
    int sector,
    Organization organization) {

  public record Organization(String id, String name, String type) {}
}
