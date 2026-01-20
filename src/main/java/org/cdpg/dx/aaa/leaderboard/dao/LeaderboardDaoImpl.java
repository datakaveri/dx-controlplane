package org.cdpg.dx.aaa.leaderboard.dao;

import io.vertx.core.Future;

import java.util.*;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.leaderboard.model.*;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.request.TemporalRequest;
import org.cdpg.dx.common.util.PaginationInfo;
import org.cdpg.dx.database.postgres.models.OrderBy;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class LeaderboardDaoImpl implements LeaderboardDao {
  private static final Logger LOGGER = LogManager.getLogger(LeaderboardDaoImpl.class);
  private final PostgresService postgresService;

  public LeaderboardDaoImpl(PostgresService postgresService) {
    this.postgresService = postgresService;
  }

  @Override
  public Future<LeaderboardResponse<OrganizationLeaderboardEntry>> fetchOrgLeaderboard(
      PaginatedRequest request) {

    Map<String, Object> filters = request.filters();

    // -----------------------
    // 1. assetType (optional)
    // -----------------------
    List<String> assetTypes =
        getList(filters, "assetType").stream().map(String::toUpperCase).toList();

    if (assetTypes.isEmpty()) {
      assetTypes = List.of("DATABANK", "AI_MODEL", "USECASE");
    }

    LOGGER.debug("Fetching org leaderboard with assetTypes: {}", assetTypes);

    // -----------------------
    // 2. Optional time filter
    // -----------------------
    String startTime = null;
    String endTime = null;

    if (request.temporalRequests() != null && !request.temporalRequests().isEmpty()) {
      TemporalRequest t = request.temporalRequests().getFirst();
      startTime = t.time();
      endTime = t.endtime();
    }

    // -----------------------
    // 3. Pagination
    // -----------------------
    int limit = request.size();
    int offset = (request.page() - 1) * request.size();

    // -----------------------
    // 4. SQL
    // -----------------------
    String sql =
        """
            WITH org_stats AS (
                SELECT
                    a.org_id,
                    a.org_name,

                    COUNT(*) FILTER (
                        WHERE a.operation = 'CREATE' AND a.entity_type = 'DATABANK'
                    ) AS published_databanks,

                    COUNT(*) FILTER (
                        WHERE a.operation = 'CREATE' AND a.entity_type = 'AI_MODEL'
                    ) AS published_ai_models,

                    COUNT(*) FILTER (
                        WHERE a.operation = 'CREATE' AND a.entity_type = 'USECASE'
                    ) AS published_usecases,

                    COUNT(*) FILTER (
                        WHERE a.operation = 'CREATE'
                          AND a.entity_type IN ('DATABANK','AI_MODEL','USECASE')
                    ) AS total_published,

                    COUNT(*) FILTER (WHERE a.operation = 'DOWNLOAD') AS downloads

                FROM activity_audit_log a
                WHERE a.org_id IS NOT NULL
                  AND a.entity_type::text = ANY (
                      SELECT jsonb_array_elements_text($1::jsonb)
                  )
                  AND (
                      $2::text IS NULL
                      OR $3::text IS NULL
                      OR a.created_at BETWEEN
                           to_timestamp($2, 'YYYY-MM-DD"T"HH24:MI:SS')
                       AND to_timestamp($3, 'YYYY-MM-DD"T"HH24:MI:SS')
                  )
                GROUP BY a.org_id, a.org_name
            ),

            votes AS (
                SELECT
                    a.org_id,
                    SUM(v.likes)    AS likes,
                    SUM(v.dislikes) AS dislikes
                FROM (
                    SELECT
                        entity_id,
                        entity_type,
                        COUNT(*) FILTER (WHERE vote_type = 'LIKE')    AS likes,
                        COUNT(*) FILTER (WHERE vote_type = 'DISLIKE') AS dislikes
                    FROM item_votes
                    GROUP BY entity_id, entity_type
                ) v
                JOIN (
                    SELECT DISTINCT entity_id, entity_type, org_id
                    FROM activity_audit_log
                    WHERE org_id IS NOT NULL
                ) a
                  ON v.entity_id = a.entity_id
                 AND v.entity_type = a.entity_type
                GROUP BY a.org_id
            ),

            members AS (
                 SELECT
                     organization_id AS org_id,
                     COUNT(*) AS members
                 FROM organization_users
                 GROUP BY organization_id
             )


            SELECT
                o.org_id                           AS id,
                o.org_name                         AS organization_name,
                NULL                               AS organization_type,
                NULL                               AS sector,

                COALESCE(m.members, 0)             AS members,
                o.published_databanks,
                o.published_ai_models,
                o.published_usecases,
                o.total_published,
                o.downloads,

                COALESCE(v.likes, 0)               AS likes,
                COALESCE(v.dislikes, 0)            AS dislikes,

                ROW_NUMBER() OVER (
                    ORDER BY o.total_published DESC,
                             o.downloads DESC,
                             o.org_name ASC
                ) AS rank,

                COUNT(*) OVER() AS total_count

            FROM org_stats o
            LEFT JOIN votes v   ON o.org_id = v.org_id
            LEFT JOIN members m ON o.org_id = m.org_id
            WHERE o.total_published > 0
            ORDER BY o.total_published DESC, o.downloads DESC, o.org_name ASC
            LIMIT $4 OFFSET $5
            """;

    // -----------------------
    // 5. Params
    // -----------------------
    JsonArray params =
        new JsonArray()
            .add(new JsonArray(assetTypes)) // $1
            .add(startTime) // $2
            .add(endTime) // $3
            .add(limit) // $4
            .add(offset); // $5

    LOGGER.debug("Executing org leaderboard SQL");
    LOGGER.debug("Params: {}", params.encodePrettily());

    // -----------------------
    // 6. Execute & map
    // -----------------------
    return postgresService
        .executeQuery(sql, params)
        .map(
            result -> {
              List<OrganizationLeaderboardEntry> data =
                  result.getRows().stream()
                      .map(
                          obj -> {
                            JsonObject r = (JsonObject) obj;
                            return new OrganizationLeaderboardEntry(
                                r.getInteger("rank"),
                                r.getString("id"),
                                r.getString("organization_name"),
                                r.getInteger("members", 0),
                                r.getInteger("published_databanks", 0),
                                r.getInteger("published_ai_models", 0),
                                r.getInteger("published_usecases", 0),
                                r.getInteger("total_published", 0),
                                r.getInteger("downloads", 0),
                                r.getInteger("likes", 0),
                                r.getInteger("dislikes", 0));
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

    Map<String, Object> filters = request.filters();

    // 1. assetType (optional, default all)
    List<String> assetTypes = getList(filters, "assetType");
    if (assetTypes.isEmpty()) {
      assetTypes = List.of("DATABANK", "AI_MODEL", "USECASE");
    }

    LOGGER.debug("Fetching provider leaderboard with asset types: {}", assetTypes);

    // 2. Optional time
    String startTime = null;
    String endTime = null;
    if (request.temporalRequests() != null && !request.temporalRequests().isEmpty()) {
      TemporalRequest t = request.temporalRequests().getFirst();
      startTime = t.time();
      endTime = t.endtime();
    }

    // 3. Pagination
    int limit = request.size();
    int offset = (request.page() - 1) * request.size();

    // 4. SQL (JSON-safe array binding)
    String sql =
        """
      WITH provider_stats AS (
          SELECT
              a.provider_id,
              a.org_id,
              a.org_name,
              COUNT(*) FILTER (
                  WHERE a.operation = 'CREATE' AND a.entity_type = 'DATABANK'
              ) AS databank_published_count,
              COUNT(*) FILTER (
                  WHERE a.operation = 'CREATE' AND a.entity_type = 'AI_MODEL'
              ) AS ai_model_published_count,
              COUNT(*) FILTER (
                  WHERE a.operation = 'CREATE' AND a.entity_type = 'USECASE'
              ) AS use_case_published_count,
              COUNT(*) FILTER (
                  WHERE a.operation = 'CREATE' AND a.entity_type IN ('DATABANK', 'AI_MODEL', 'USECASE')
              ) AS total_published_count,
              COUNT(*) FILTER (
                  WHERE a.operation = 'DOWNLOAD'
              ) AS total_downloads,
              COUNT(*) FILTER (
                  WHERE a.operation = 'VIEW'
              ) AS total_views
          FROM activity_audit_log a
          WHERE a.provider_id IS NOT NULL
            AND a.entity_type::text = ANY (
                SELECT jsonb_array_elements_text($1::jsonb)
            )
           AND (
                  $2::text IS NULL
                  OR $3::text IS NULL
                  OR a.created_at BETWEEN
                       to_timestamp($2, 'YYYY-MM-DD"T"HH24:MI:SS')
                   AND to_timestamp($3, 'YYYY-MM-DD"T"HH24:MI:SS')
              )

          GROUP BY a.provider_id, a.org_id, a.org_name
      ),
      votes AS (
           SELECT
               a.provider_id,
               SUM(v.likes)    AS total_likes,
               SUM(v.dislikes) AS total_dislikes
           FROM (
               SELECT
                   entity_id,
                   entity_type,
                   COUNT(*) FILTER (WHERE vote_type = 'LIKE')    AS likes,
                   COUNT(*) FILTER (WHERE vote_type = 'DISLIKE') AS dislikes
               FROM item_votes
               GROUP BY entity_id, entity_type
           ) v
           JOIN (
               SELECT DISTINCT entity_id, entity_type, provider_id
               FROM activity_audit_log
               WHERE provider_id IS NOT NULL
           ) a
             ON v.entity_id = a.entity_id
            AND v.entity_type = a.entity_type
           GROUP BY a.provider_id
       )

      SELECT
          p.*,
          COALESCE(v.total_likes, 0)    AS total_likes,
          COALESCE(v.total_dislikes, 0) AS total_dislikes,
          ROW_NUMBER() OVER (
              ORDER BY total_published_count DESC, total_downloads DESC, org_name ASC
          ) AS rank,
          COUNT(*) OVER() AS total_count
      FROM provider_stats p
      LEFT JOIN votes v ON p.provider_id = v.provider_id
      WHERE p.total_published_count > 0
      ORDER BY total_published_count DESC, total_downloads DESC, org_name ASC
      LIMIT $4 OFFSET $5
      """;

    // 5. Params (JSON-safe)
    JsonArray params =
        new JsonArray()
            .add(new JsonArray(assetTypes)) // $1
            .add(startTime) // $2
            .add(endTime) // $3
            .add(limit) // $4
            .add(offset); // $5

    // 6. Execute & map
    return postgresService
        .executeQuery(sql, params)
        .map(
            result -> {
              List<ProviderLeaderboardEntry> data =
                  result.getRows().stream()
                      .map(
                          obj -> {
                            JsonObject row = (JsonObject) obj;
                            return new ProviderLeaderboardEntry(
                                row.getInteger("rank"),
                                row.getString("provider_id"),
                                row.getString("provider_name", "provider name"),
                                row.getString("org_id"),
                                row.getString("org_name", ""),
                                row.getInteger("databank_published_count", 0),
                                row.getInteger("ai_model_published_count", 0),
                                row.getInteger("use_case_published_count", 0),
                                row.getInteger("total_published_count", 0),
                                row.getInteger("total_downloads", 0),
                                row.getInteger("total_likes", 0),
                                row.getInteger("total_dislikes", 0));
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

    LOGGER.debug("Fetching asset leaderboard with request filter: {}", request.filters());
    LOGGER.debug("Page: {}, Size: {}", request.page(), request.size());
    LOGGER.debug("Sort: {}", request.orderByList());

    // ----------------------------
    // 1. Sorting (safe whitelist)
    // ----------------------------
    Set<String> allowed = Set.of("downloads", "views", "likes", "dislikes");

    String sortColumn = "downloads";
    OrderBy.Direction sortDirection = OrderBy.Direction.DESC;

    if (request.orderByList() != null && !request.orderByList().isEmpty()) {
      OrderBy ob = request.orderByList().getFirst();
      if (ob.getColumn() != null && allowed.contains(ob.getColumn())) {
        sortColumn = ob.getColumn();
      }
      if (ob.getDirection() != null) {
        sortDirection = ob.getDirection();
      }
    }

    String orderBySql = sortColumn + " " + sortDirection.name() + ", entity_name ASC";

    // ----------------------------
    // 2. Filters
    // ----------------------------
    Map<String, Object> filters = request.filters();

    String assetType = toCsv(filters, "assetType");
    if (assetType == null || assetType.isBlank()) {
      assetType = "DATABANK,AI_MODEL,USECASE";
    } else {
      assetType = assetType.toUpperCase();
    }

    String accessPolicy = toCsv(filters, "accessPolicy");
    String orgType = toCsv(filters, "organizationType"); // only apply if column exists
    String sector = toCsv(filters, "sector"); // only apply if column exists

    // ----------------------------
    // 3. Optional time filter
    // ----------------------------
    String startTime = null;
    String endTime = null;

    if (request.temporalRequests() != null && !request.temporalRequests().isEmpty()) {
      TemporalRequest t = request.temporalRequests().getFirst();
      startTime = t.time();
      endTime = t.endtime();
    }

    // ----------------------------
    // 4. Pagination
    // ----------------------------
    int limit = request.size();
    int offset = (request.page() - 1) * request.size();

    // ----------------------------
    // 5. Build SQL dynamically
    // ----------------------------
    StringBuilder sql =
        new StringBuilder(
            """
    WITH stats AS (
        SELECT
            a.entity_id,
            a.entity_name,
            a.entity_type,
            a.org_id,
            a.org_name,
            a.provider_id,
            MAX(a.short_description) AS short_description,

            COUNT(*) FILTER (WHERE a.operation = 'VIEW')     AS views,
            COUNT(*) FILTER (WHERE a.operation = 'DOWNLOAD') AS downloads,
            COALESCE(v.likes, 0)    AS likes,
            COALESCE(v.dislikes, 0) AS dislikes

        FROM activity_audit_log a
        LEFT JOIN (
            SELECT
                entity_id,
                entity_type,
                COUNT(*) FILTER (WHERE vote_type = 'LIKE')    AS likes,
                COUNT(*) FILTER (WHERE vote_type = 'DISLIKE') AS dislikes
            FROM item_votes
            GROUP BY entity_id, entity_type
        ) v
          ON a.entity_id = v.entity_id
         AND a.entity_type = v.entity_type

        WHERE a.operation IN ('VIEW', 'DOWNLOAD')
  """);

    JsonArray params = new JsonArray();
    int idx = 1;

    // Optional time filter
    if (startTime != null && endTime != null) {
      sql.append(" AND a.created_at BETWEEN ")
          .append("to_timestamp($")
          .append(idx)
          .append(", 'YYYY-MM-DD\"T\"HH24:MI:SS')")
          .append(" AND ")
          .append("to_timestamp($")
          .append(idx + 1)
          .append(", 'YYYY-MM-DD\"T\"HH24:MI:SS')");

      params.add(startTime);
      params.add(endTime);
      idx += 2;
    }

    // assetType (ENUM safe)
    sql.append(" AND a.entity_type = ANY(string_to_array($")
        .append(idx)
        .append(", ',')::entity_type[])");
    params.add(assetType);
    idx++;

    if (accessPolicy != null) {
      sql.append(" AND a.access_policy = ANY(string_to_array($").append(idx).append(", ','))");
      params.add(accessPolicy);
      idx++;
    }

    if (orgType != null) {
      sql.append(" AND a.org_type = ANY(string_to_array($").append(idx).append("::text, ','))");
      params.add(orgType);
      idx++;
    }

    if (sector != null) {
      sql.append(" AND a.sector = ANY(string_to_array($").append(idx).append("::text, ','))");
      params.add(sector);
      idx++;
    }

    // Finish SQL
    sql.append(
        """
        GROUP BY
            a.entity_id, a.entity_name, a.entity_type,
            a.org_id, a.org_name,
            a.provider_id,
            v.likes, v.dislikes
    )
    SELECT *,
           ROW_NUMBER() OVER (ORDER BY %s) AS rank,
           COUNT(*) OVER() AS total_count
    FROM stats
    ORDER BY %s
    LIMIT $%d OFFSET $%d
  """
            .formatted(orderBySql, orderBySql, idx, idx + 1));

    params.add(limit);
    params.add(offset);

    LOGGER.debug("Executing SQL:\n{}", sql);
    LOGGER.debug("Params: {}", params.encodePrettily());

    // ----------------------------
    // 6. Execute & map
    // ----------------------------
    return postgresService
        .executeQuery(sql.toString(), params)
        .map(
            result -> {
              List<AssetLeaderboardEntry> data =
                  result.getRows().stream()
                      .map(
                          obj -> {
                            JsonObject row = (JsonObject) obj;
                            return new AssetLeaderboardEntry(
                                row.getInteger("rank"),
                                row.getString("entity_id"),
                                row.getString("entity_name"),
                                row.getString("entity_type"),
                                row.getString("short_description"),
                                row.getString("access_policy", ""),
                                row.getString("provider_id"),
                                row.getString("provider_name", ""),
                                row.getString("org_id"),
                                row.getString("org_name"),
                                row.getInteger("downloads", 0),
                                row.getInteger("likes", 0),
                                row.getInteger("dislikes", 0),
                                row.getInteger("views", 0));
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

  private List<String> getList(Map<String, Object> filters, String key) {
    Object value = filters.get(key);
    if (value == null) return List.of();

    if (value instanceof JsonArray arr) {
      return arr.stream()
          .map(Object::toString)
          .map(String::trim)
          .filter(s -> !s.isEmpty())
          .map(String::toUpperCase)
          .toList();
    }

    if (value instanceof List<?> list) {
      return list.stream()
          .map(Object::toString)
          .map(String::trim)
          .filter(s -> !s.isEmpty())
          .map(String::toUpperCase)
          .toList();
    }

    String s = value.toString().trim();
    return s.isEmpty() ? List.of() : List.of(s.toUpperCase());
  }
}
