package org.cdpg.dx.aaa.interaction.v2.dao.impl;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.interaction.dao.impl.UserInteractionDaoImpl;
import org.cdpg.dx.aaa.interaction.v2.dao.ProviderFeedbackDao;
import org.cdpg.dx.aaa.interaction.v2.dao.UserFeedbackDao;
import org.cdpg.dx.aaa.interaction.v2.enums.ProviderFeedbackType;
import org.cdpg.dx.aaa.interaction.v2.model.ProviderFeedback;
import org.cdpg.dx.aaa.interaction.v2.model.ProviderFeedbackByAsset;
import org.cdpg.dx.aaa.interaction.v2.model.ProviderFeedbackEntry;
import org.cdpg.dx.aaa.interaction.v2.model.ProviderFeedbackPaginatedResponse;
import org.cdpg.dx.aaa.interaction.v2.model.UserFeedback;
import org.cdpg.dx.aaa.interaction.v2.model.UserFeedbackPaginatedResponse;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.util.PaginationInfo;
import org.cdpg.dx.database.postgres.base.dao.AbstractBaseDAO;
import org.cdpg.dx.database.postgres.models.*;
import org.cdpg.dx.database.postgres.service.PostgresService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class ProviderFeedbackDaoImpl extends AbstractBaseDAO<ProviderFeedback>
    implements ProviderFeedbackDao {

  private static final Logger LOGGER = LogManager.getLogger(UserInteractionDaoImpl.class);

  public ProviderFeedbackDaoImpl(PostgresService postgresService) {
    super(postgresService, "provider_feedback", "id", ProviderFeedback::fromJson);
  }

  @Override
  public Future<ProviderFeedback> postProviderFeedback(ProviderFeedback providerFeedback) {
    // provider_feedback has a UNIQUE(asset_id, type) constraint as of V81 — one row
    // per asset+type regardless of which provider submits it (id stays the actual
    // primary key). This atomically inserts or, on conflict, overwrites data/user_id
    // in place; id/created_at never change once the row exists, and updated_at is
    // refreshed by the DB trigger added in the same migration.
    return super.upsert(providerFeedback, List.of("asset_id", "type"), List.of("data", "user_id"));
  }

  @Override
  public Future<ProviderFeedbackPaginatedResponse> fetchProviderFeedbacks(
      PaginatedRequest request) {

    int page = request.page();
    int size = request.size();
    int offset = (page - 1) * size;

    Map<String, Object> filters = request.filters();
    LOGGER.debug("filters: {}", filters);

    // Response is grouped by asset (one entry per asset, each carrying every
    // matching feedback type as a list), so pagination is over distinct assets,
    // not rows: first page the matching asset_ids, then fetch every row (still
    // honoring the same filters, e.g. type) for exactly that page of assets.
    StringBuilder where = new StringBuilder(" WHERE 1=1 ");
    JsonArray whereParams = new JsonArray();
    AtomicInteger index = new AtomicInteger(1);

    java.util.function.BiConsumer<String, Object> applyFilter =
        (column, rawValue) -> {
          if (rawValue == null) return;

          List<?> values = rawValue instanceof List<?> list ? list : List.of(rawValue);

          if (values.isEmpty()) return;

          where.append(" AND ").append(column).append(" IN (");

          for (int i = 0; i < values.size(); i++) {
            if (i > 0) where.append(", ");
            where.append("$").append(index.getAndIncrement());
            whereParams.add(values.get(i));
          }

          where.append(")");
        };

    applyFilter.accept("user_id", filters.get("user_id"));
    applyFilter.accept("asset_id", filters.get("asset_id"));
    applyFilter.accept("type", filters.get("type"));

    int limitIndex = index.getAndIncrement();
    int offsetIndex = index.getAndIncrement();

    String assetSql =
        """
      WITH matched_assets AS (
        SELECT asset_id, MAX(updated_at) AS latest_updated_at
        FROM provider_feedback
      """
            + where
            + """
        GROUP BY asset_id
      )
      SELECT asset_id, COUNT(*) OVER() AS total_count
      FROM matched_assets
      ORDER BY latest_updated_at DESC
      LIMIT $"""
            + limitIndex
            + " OFFSET $"
            + offsetIndex;

    JsonArray assetParams = new JsonArray(whereParams.getList());
    assetParams.add(size);
    assetParams.add(offset);

    LOGGER.debug("Asset SQL: {}", assetSql);
    LOGGER.debug("Asset params: {}", assetParams);

    return postgresService
        .executeQuery(assetSql, assetParams)
        .compose(
            assetRows -> {
              List<UUID> assetIds =
                  assetRows.getRows().stream()
                      .map(obj -> UUID.fromString(((JsonObject) obj).getString("asset_id")))
                      .toList();

              long total = assetRows.getTotalCount();

              if (assetIds.isEmpty()) {
                return Future.succeededFuture(
                    new ProviderFeedbackPaginatedResponse(
                        List.of(), PaginationInfo.from(page, size, total)));
              }

              StringBuilder detailWhere = new StringBuilder(" WHERE 1=1 ");
              JsonArray detailParams = new JsonArray();
              AtomicInteger detailIndex = new AtomicInteger(1);

              java.util.function.BiConsumer<String, Object> applyDetailFilter =
                  (column, rawValue) -> {
                    if (rawValue == null) return;

                    List<?> values = rawValue instanceof List<?> list ? list : List.of(rawValue);

                    if (values.isEmpty()) return;

                    detailWhere.append(" AND ").append(column).append(" IN (");

                    for (int i = 0; i < values.size(); i++) {
                      if (i > 0) detailWhere.append(", ");
                      detailWhere.append("$").append(detailIndex.getAndIncrement());
                      detailParams.add(values.get(i));
                    }

                    detailWhere.append(")");
                  };

              applyDetailFilter.accept("user_id", filters.get("user_id"));
              applyDetailFilter.accept("type", filters.get("type"));
              applyDetailFilter.accept(
                  "asset_id", assetIds.stream().map(UUID::toString).toList());

              String detailSql =
                  """
                SELECT user_id, asset_id, type, data, created_at, updated_at
                FROM provider_feedback
                """
                      + detailWhere
                      + " ORDER BY asset_id, type";

              LOGGER.debug("Detail SQL: {}", detailSql);
              LOGGER.debug("Detail params: {}", detailParams);

              return postgresService
                  .executeQuery(detailSql, detailParams)
                  .map(
                      detailRows -> {
                        Map<UUID, List<ProviderFeedbackEntry>> byAsset = new LinkedHashMap<>();

                        for (Object obj : detailRows.getRows()) {
                          JsonObject r = (JsonObject) obj;
                          UUID assetId = UUID.fromString(r.getString("asset_id"));

                          ProviderFeedbackEntry entry =
                              new ProviderFeedbackEntry(
                                  ProviderFeedbackType.valueOf(r.getString("type")),
                                  r.getJsonArray("data"),
                                  UUID.fromString(r.getString("user_id")),
                                  r.getString("created_at") != null
                                      ? LocalDateTime.parse(r.getString("created_at"))
                                      : null,
                                  r.getString("updated_at") != null
                                      ? LocalDateTime.parse(r.getString("updated_at"))
                                      : null);

                          byAsset.computeIfAbsent(assetId, k -> new ArrayList<>()).add(entry);
                        }

                        List<ProviderFeedbackByAsset> result =
                            assetIds.stream()
                                .map(
                                    assetId ->
                                        new ProviderFeedbackByAsset(
                                            assetId, byAsset.getOrDefault(assetId, List.of())))
                                .toList();

                        return new ProviderFeedbackPaginatedResponse(
                            result, PaginationInfo.from(page, size, total));
                      });
            });
  }

  @Override
  public Future<Boolean> deleteProviderFeedback(
      UUID userId, UUID assetId, ProviderFeedbackType type) {

    Condition condition =
        new Condition(
            List.of(
                new Condition("user_id", Condition.Operator.EQUALS, List.of(userId.toString())),
                new Condition("asset_id", Condition.Operator.EQUALS, List.of(assetId.toString())),
                new Condition("type", Condition.Operator.EQUALS, List.of(type.name()))),
            Condition.LogicalOperator.AND);

    DeleteQuery query = new DeleteQuery("provider_feedback", condition, null, null);

    return postgresService.delete(query).map(v -> true);
  }
}
