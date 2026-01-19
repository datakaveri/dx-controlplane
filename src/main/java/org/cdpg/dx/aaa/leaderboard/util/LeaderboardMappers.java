package org.cdpg.dx.aaa.leaderboard.util;

import io.vertx.sqlclient.Row;
import org.cdpg.dx.aaa.leaderboard.model.AssetLeaderboardEntry;

public final class LeaderboardMappers {

  private LeaderboardMappers() {}

  public static AssetLeaderboardEntry mapAsset(Row row) {
    return new AssetLeaderboardEntry(
        row.getInteger("rank"),
        row.getUUID("entity_id").toString(),
        row.getString("entity_name"),
        row.getString("entity_type"),
        row.getString("short_description"),
        row.getString("access_policy"),
        row.getUUID("provider_id").toString(),
        row.getString("provider_name"),
        row.getInteger("downloads"),
        row.getInteger("likes"),
        row.getInteger("dislikes"),
        row.getInteger("views"),
        row.getInteger("sector"),
        new AssetLeaderboardEntry.Organization(
            row.getUUID("org_id").toString(),
            row.getString("org_name"),
            row.getString("org_type")));
  }
}
