package org.cdpg.dx.aaa.leaderboard.model;

public record ProviderLeaderboardEntry(
    int rank,
    String userId,
    String orgId,
    String username,
    String role,
    Organization organization,
    int publishedDatasets,
    int publishedModels,
    int publishedUsecases,
    int totalPublished,
    int downloads,
    int likes,
    int dislikes) {
  public record Organization(String id, String name, String type) {}
}
