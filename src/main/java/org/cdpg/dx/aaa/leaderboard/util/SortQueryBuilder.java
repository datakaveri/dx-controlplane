package org.cdpg.dx.aaa.leaderboard.util;

import java.util.List;
import java.util.Set;
import org.cdpg.dx.aaa.leaderboard.enums.LeaderboardType;
import org.cdpg.dx.database.postgres.models.OrderBy;

public class SortQueryBuilder {

  public static String buildLeaderboardOrderBy(
      LeaderboardType type,
      String columnFromRequest,
      OrderBy.Direction directionFromRequest,
      String nameColumn) {
    OrderBy.Direction direction =
        directionFromRequest != null ? directionFromRequest : OrderBy.Direction.DESC;

    // Allowed columns
    String primary = getString(type, columnFromRequest);

    // Base tie-breakers (order matters!)
    List<String> baseTieBreakers =
        switch (type) {
          case ASSET -> List.of("downloads", "likes", "views");
          case PROVIDER, ORGANIZATION -> List.of("total_published", "downloads", "likes");
        };

    // Remove primary from tie-breakers
    List<String> tieBreakers =
        baseTieBreakers.stream().filter(col -> !col.equals(primary)).toList();

    // Build ORDER BY
    StringBuilder orderBy = new StringBuilder();
    orderBy.append(primary).append(" ").append(direction.name());

    for (String tb : tieBreakers) {
      orderBy.append(", ").append(tb).append(" DESC");
    }

    orderBy.append(", ").append(nameColumn).append(" ASC");

    return orderBy.toString();
  }

  private static String getString(LeaderboardType type, String columnFromRequest) {
    Set<String> allowedColumns =
        switch (type) {
          case ASSET -> Set.of("downloads", "views", "likes");
          case PROVIDER, ORGANIZATION -> Set.of("total_published", "downloads", "likes");
        };

    // Default primary column
    String defaultPrimary =
        switch (type) {
          case ASSET -> "downloads";
          case PROVIDER, ORGANIZATION -> "total_published";
        };

    return allowedColumns.contains(columnFromRequest) ? columnFromRequest : defaultPrimary;
  }
}
