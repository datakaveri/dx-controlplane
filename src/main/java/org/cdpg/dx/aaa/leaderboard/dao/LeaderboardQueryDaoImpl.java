package org.cdpg.dx.aaa.leaderboard.dao;

import io.vertx.core.Future;

import java.util.*;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.leaderboard.model.*;
import org.cdpg.dx.aaa.leaderboard.util.SortQueryBuilder;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.util.PaginationInfo;
import org.cdpg.dx.database.postgres.models.OrderBy;
import org.cdpg.dx.database.postgres.service.PostgresService;

import static org.cdpg.dx.aaa.leaderboard.enums.LeaderboardType.*;

public class LeaderboardQueryDaoImpl implements LeaderboardQueryDao {
  private static final Logger LOGGER = LogManager.getLogger(LeaderboardQueryDaoImpl.class);
  private final PostgresService postgresService;

  public LeaderboardQueryDaoImpl(PostgresService postgresService) {
    this.postgresService = postgresService;
  }

  private String toCsv(Map<String, Object> filters, String key) {
    Object value = filters.get(key);
    if (value == null) return null;

    List<String> list = new ArrayList<>();

    if (value instanceof JsonArray arr) {
      arr.forEach(
          v -> {
            if (v != null && !v.toString().isBlank()) list.add(v.toString().trim());
          });
    } else if (value instanceof List<?> l) {
      l.forEach(
          v -> {
            if (v != null && !v.toString().isBlank()) list.add(v.toString().trim());
          });
    } else {
      if (!value.toString().isBlank()) list.add(value.toString().trim());
    }

    return list.isEmpty() ? null : String.join(",", list);
  }

  @Override
  public Future<LeaderboardResponse<OrganizationLeaderboardEntry>> fetchOrgLeaderboard(
      PaginatedRequest request) {

    LOGGER.debug("Fetching org leaderboard with filters: {}", request.filters());
    LOGGER.debug("Page: {}, Size: {}", request.page(), request.size());
    LOGGER.debug("Sort: {}", request.orderByList());

    // -------------------------------------------------
    // 1. Sorting
    // -------------------------------------------------
    OrderBy orderBy =
        request.orderByList() != null && !request.orderByList().isEmpty()
            ? request.orderByList().getFirst()
            : null;

    String orderBySql =
        SortQueryBuilder.buildLeaderboardOrderBy(
            ORGANIZATION,
            orderBy != null ? orderBy.getColumn() : null,
            orderBy != null ? orderBy.getDirection() : null,
            "organization_name");

    // -------------------------------------------------
    // 2. Filters
    // -------------------------------------------------
    Map<String, Object> filters = request.filters();

    String assetTypeCsv = toCsv(filters, "assetType");
    if (assetTypeCsv == null || assetTypeCsv.isBlank()) {
      assetTypeCsv = "DATABANK,AI_MODEL,USECASE";
    } else {
      assetTypeCsv = assetTypeCsv.toUpperCase();
    }

    String orgTypeCsv = toCsv(filters, "organizationType");

    // -------------------------------------------------
    // 3. Pagination
    // -------------------------------------------------
    int limit = request.size();
    int offset = (request.page() - 1) * request.size();

    // -------------------------------------------------
    // 4. SQL
    // -------------------------------------------------
    StringBuilder sql =
        new StringBuilder(
"""
WITH org_stats AS (
    SELECT
        organization_id,
        organization_name,
        organization_type,
        published_databank,
        published_ai_models,
        published_usecases,
        (
            CASE WHEN 'DATABANK' = ANY(string_to_array($1, ',')) THEN published_databank ELSE 0 END
          + CASE WHEN 'AI_MODEL' = ANY(string_to_array($1, ',')) THEN published_ai_models ELSE 0 END
          + CASE WHEN 'USECASE'  = ANY(string_to_array($1, ',')) THEN published_usecases  ELSE 0 END
        ) AS total_published,
        downloads,
        likes,
        views
    FROM organization_leaderboard
    WHERE (
        CASE WHEN 'DATABANK' = ANY(string_to_array($1, ',')) THEN published_databank ELSE 0 END
      + CASE WHEN 'AI_MODEL' = ANY(string_to_array($1, ',')) THEN published_ai_models ELSE 0 END
      + CASE WHEN 'USECASE'  = ANY(string_to_array($1, ',')) THEN published_usecases  ELSE 0 END
    ) > 0
),
members AS (
    SELECT
        organization_id,
        COUNT(*) AS members
    FROM organization_users
    GROUP BY organization_id
)
SELECT
    o.organization_id,
    o.organization_name,
    o.organization_type,
    COALESCE(m.members, 0) AS members,
    o.published_databank,
    o.published_ai_models,
    o.published_usecases,
    o.total_published,
    o.downloads,
    o.likes,
    ROW_NUMBER() OVER (ORDER BY %s) AS rank,
    COUNT(*) OVER () AS total_count
FROM org_stats o
LEFT JOIN members m ON o.organization_id = m.organization_id
"""
                .formatted(orderBySql));

    JsonArray params = new JsonArray();
    params.add(assetTypeCsv);
    int idx = 2;

    // ---- Optional org type filter
    if (orgTypeCsv != null) {
      sql.append(" WHERE o.organization_type = ANY(string_to_array($")
          .append(idx)
          .append(", ','))");
      params.add(orgTypeCsv);
      idx++;
    }

    // ---- Order + pagination
    sql.append(" ORDER BY ")
        .append(orderBySql)
        .append(" LIMIT $")
        .append(idx)
        .append(" OFFSET $")
        .append(idx + 1);

    params.add(limit);
    params.add(offset);

    LOGGER.debug("Executing Org Leaderboard SQL:\n{}", sql);
    LOGGER.debug("Params: {}", params.encodePrettily());

    // -------------------------------------------------
    // 5. Execute & map
    // -------------------------------------------------
    return postgresService
        .executeQuery(sql.toString(), params)
        .map(
            result -> {
              List<OrganizationLeaderboardEntry> data =
                  result.getRows().stream()
                      .map(
                          obj -> {
                            JsonObject r = (JsonObject) obj;
                            return new OrganizationLeaderboardEntry(
                                r.getInteger("rank"),
                                r.getString("organization_id"),
                                r.getString("organization_name"),
                                r.getString("organization_type", ""),
                                r.getInteger("members", 0),
                                r.getLong("published_databank", 0L),
                                r.getLong("published_ai_models", 0L),
                                r.getLong("published_usecases", 0L),
                                r.getLong("total_published", 0L),
                                r.getLong("downloads", 0L),
                                r.getLong("likes", 0L));
                          })
                      .toList();

              long total =
                  data.isEmpty() ? 0 : result.getRows().getJsonObject(0).getLong("total_count");

              int totalPages = (int) Math.ceil((double) total / request.size());

              PaginationInfo page =
                  new PaginationInfo(
                      request.page(),
                      request.size(),
                      total,
                      totalPages,
                      request.page() < totalPages,
                      request.page() > 1);

              return new LeaderboardResponse<>(data, page);
            });
  }

  @Override
  public Future<LeaderboardResponse<ProviderLeaderboardEntry>> fetchProviderLeaderboard(
      PaginatedRequest request) {

    LOGGER.debug("Fetching provider leaderboard with filters: {}", request.filters());
    LOGGER.debug("Page: {}, Size: {}", request.page(), request.size());
    LOGGER.debug("Sort: {}", request.orderByList());

    // -------------------------------------------------
    // 1. Sorting
    // -------------------------------------------------
    OrderBy orderBy =
        request.orderByList() != null && !request.orderByList().isEmpty()
            ? request.orderByList().getFirst()
            : null;

    String orderBySql =
        SortQueryBuilder.buildLeaderboardOrderBy(
            PROVIDER,
            orderBy != null ? orderBy.getColumn() : null,
            orderBy != null ? orderBy.getDirection() : null,
            "provider_name");

    // -------------------------------------------------
    // 2. Filters
    // -------------------------------------------------
    Map<String, Object> filters = request.filters();

    // ---- assetType (OLD semantics)
    String assetTypeCsv = toCsv(filters, "assetType");
    if (assetTypeCsv == null || assetTypeCsv.isBlank()) {
      assetTypeCsv = "DATABANK,AI_MODEL,USECASE";
    } else {
      assetTypeCsv = assetTypeCsv.toUpperCase();
    }

    // ---- optional org type
    String orgTypeCsv = toCsv(filters, "organizationType");

    // -------------------------------------------------
    // 3. Pagination
    // -------------------------------------------------
    int limit = request.size();
    int offset = (request.page() - 1) * request.size();

    // -------------------------------------------------
    // 4. SQL (provider_leaderboard)
    // -------------------------------------------------
    StringBuilder sql =
        new StringBuilder(
            """
                    WITH provider_stats AS (
                        SELECT
                            provider_id,
                            provider_name,

                            organization_id,
                            organization_name,
                            organization_type,

                            published_databank,
                            published_ai_models,
                            published_usecases,

                            /* -------- COMPUTED total_published -------- */
                            (
                                CASE WHEN 'DATABANK' = ANY(string_to_array($1, ','))
                                     THEN published_databank ELSE 0 END
                              + CASE WHEN 'AI_MODEL' = ANY(string_to_array($1, ','))
                                     THEN published_ai_models ELSE 0 END
                              + CASE WHEN 'USECASE'  = ANY(string_to_array($1, ','))
                                     THEN published_usecases  ELSE 0 END
                            ) AS total_published,

                            downloads,
                            likes,
                            views,

                            updated_at
                        FROM provider_leaderboard
                        WHERE (
                                CASE WHEN 'DATABANK' = ANY(string_to_array($1, ','))
                                     THEN published_databank ELSE 0 END
                              + CASE WHEN 'AI_MODEL' = ANY(string_to_array($1, ','))
                                     THEN published_ai_models ELSE 0 END
                              + CASE WHEN 'USECASE'  = ANY(string_to_array($1, ','))
                                     THEN published_usecases  ELSE 0 END
                        ) > 0
                    """);

    JsonArray params = new JsonArray();
    params.add(assetTypeCsv);
    int idx = 2;

    // ---- Optional org type filter
    if (orgTypeCsv != null) {
      sql.append(" AND organization_type = ANY(string_to_array($").append(idx).append(", ','))");
      params.add(orgTypeCsv);
      idx++;
    }

    // ---- Close CTE + final select
    sql.append(
        """
                )
                SELECT *,
                       ROW_NUMBER() OVER (ORDER BY %s) AS rank,
                       COUNT(*) OVER () AS total_count
                FROM provider_stats
                ORDER BY %s
                LIMIT $%d OFFSET $%d
                """
            .formatted(orderBySql, orderBySql, idx, idx + 1));

    params.add(limit);
    params.add(offset);

    LOGGER.debug("Executing Provider Leaderboard SQL:\n{}", sql);
    LOGGER.debug("Params: {}", params.encodePrettily());

    // -------------------------------------------------
    // 5. Execute & map
    // -------------------------------------------------
    return postgresService
        .executeQuery(sql.toString(), params)
        .map(
            result -> {
              List<ProviderLeaderboardEntry> data =
                  result.getRows().stream()
                      .map(
                          obj -> {
                            JsonObject r = (JsonObject) obj;
                            return new ProviderLeaderboardEntry(
                                r.getInteger("rank"),
                                r.getString("provider_id"),
                                r.getString("provider_name"),
                                r.getString("organization_id"),
                                r.getString("organization_name"),
                                r.getString("organization_type", ""),
                                r.getLong("published_databank", 0L),
                                r.getLong("published_ai_models", 0L),
                                r.getLong("published_usecases", 0L),
                                r.getLong("total_published", 0L), // computed
                                r.getLong("downloads", 0L),
                                r.getLong("likes", 0L));
                          })
                      .toList();

              long total =
                  data.isEmpty() ? 0 : result.getRows().getJsonObject(0).getLong("total_count");

              int totalPages = (int) Math.ceil((double) total / request.size());

              PaginationInfo page =
                  new PaginationInfo(
                      request.page(),
                      request.size(),
                      total,
                      totalPages,
                      request.page() < totalPages,
                      request.page() > 1);

              return new LeaderboardResponse<>(data, page);
            });
  }

  @Override
  public Future<LeaderboardResponse<AssetLeaderboardEntry>> fetchAssetLeaderboard(
      PaginatedRequest request) {

    LOGGER.debug("Fetching asset leaderboard with filters: {}", request.filters());
    LOGGER.debug("Page: {}, Size: {}", request.page(), request.size());
    LOGGER.debug("Sort: {}", request.orderByList());

    // -------------------------------------------------
    // 1. Sorting
    // -------------------------------------------------
    OrderBy orderBy =
        request.orderByList() != null && !request.orderByList().isEmpty()
            ? request.orderByList().getFirst()
            : null;

    String orderBySql =
        SortQueryBuilder.buildLeaderboardOrderBy(
            ASSET,
            orderBy != null ? orderBy.getColumn() : null,
            orderBy != null ? orderBy.getDirection() : null,
            "asset_name");

    LOGGER.info("OrderBy SQL: {}", orderBySql);

    // -------------------------------------------------
    // 2. Filters
    // -------------------------------------------------
    Map<String, Object> filters = request.filters();

    String assetTypeCsv = toCsv(filters, "assetType");
    if (assetTypeCsv == null || assetTypeCsv.isBlank()) {
      assetTypeCsv = "DATABANK,AI_MODEL,USECASE";
    } else {
      assetTypeCsv = assetTypeCsv.toUpperCase();
    }

    String accessPolicy = toCsv(filters, "accessPolicy");
    String orgType = toCsv(filters, "organizationType");

    // -------------------------------------------------
    // 3. Pagination
    // -------------------------------------------------
    int limit = request.size();
    int offset = (request.page() - 1) * request.size();

    // -------------------------------------------------
    // 4. SQL
    // -------------------------------------------------
    StringBuilder sql =
        new StringBuilder(
            """
                    WITH asset_stats AS (
                        SELECT
                            asset_id,
                            asset_name,
                            asset_type,
                            access_policy,

                            provider_id,
                            provider_name,

                            organization_id,
                            organization_name,
                            organization_type,

                            downloads,
                            likes,
                            views

                        FROM asset_leaderboard
                        WHERE asset_type = ANY(string_to_array($1, ','))
                    """);

    JsonArray params = new JsonArray();
    params.add(assetTypeCsv);
    int idx = 2;

    // ---- Optional filters
    if (accessPolicy != null) {
      sql.append(" AND access_policy = ANY(string_to_array($").append(idx).append(", ','))");
      params.add(accessPolicy);
      idx++;
    }

    if (orgType != null) {
      sql.append(" AND organization_type = ANY(string_to_array($").append(idx).append(", ','))");
      params.add(orgType);
      idx++;
    }

    // ---- Close CTE + final select
    sql.append(
        """
                )
                SELECT *,
                       ROW_NUMBER() OVER (ORDER BY %s) AS rank,
                       COUNT(*) OVER () AS total_count
                FROM asset_stats
                WHERE (downloads + views + likes) > 0
                ORDER BY %s
                LIMIT $%d OFFSET $%d
                """
            .formatted(orderBySql, orderBySql, idx, idx + 1));

    params.add(limit);
    params.add(offset);

    LOGGER.debug("Executing Asset Leaderboard SQL:\n{}", sql);
    LOGGER.debug("Params: {}", params.encodePrettily());

    // -------------------------------------------------
    // 5. Execute & map
    // -------------------------------------------------
    return postgresService
        .executeQuery(sql.toString(), params)
        .map(
            result -> {
              List<AssetLeaderboardEntry> data =
                  result.getRows().stream()
                      .map(
                          obj -> {
                            JsonObject r = (JsonObject) obj;
                            return new AssetLeaderboardEntry(
                                r.getInteger("rank"),
                                r.getString("asset_id"),
                                r.getString("asset_name"),
                                r.getString("asset_type"),
                                "", // short_description not available in table
                                r.getString("access_policy", ""),
                                r.getString("provider_id"),
                                r.getString("provider_name", ""),
                                r.getString("organization_id"),
                                r.getString("organization_name"),
                                r.getString("organization_type", ""),
                                r.getLong("downloads", 0L),
                                r.getLong("likes", 0L),
                                r.getLong("views", 0L));
                          })
                      .toList();

              long total =
                  data.isEmpty() ? 0 : result.getRows().getJsonObject(0).getLong("total_count");

              int totalPages = (int) Math.ceil((double) total / request.size());

              PaginationInfo page =
                  new PaginationInfo(
                      request.page(),
                      request.size(),
                      total,
                      totalPages,
                      request.page() < totalPages,
                      request.page() > 1);

              return new LeaderboardResponse<>(data, page);
            });
  }
}
