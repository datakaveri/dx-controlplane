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
   public Future<ProviderFeedback> postProviderFeedback(ProviderFeedback providerFeedback)
  {

    JsonObject feedbackJson =  providerFeedback.toJson();
    String userId = feedbackJson.getString("user_id");
    String assetId = feedbackJson.getString("asset_id");
    String feedbackType = feedbackJson.getString("type");

    var map = providerFeedback.toNonEmptyFieldsMap();

    LOGGER.debug("feedbackJson: {}", feedbackJson);

    Condition condition =
      new Condition(
        List.of(
          new Condition("user_id", Condition.Operator.EQUALS, List.of(userId)),
          new Condition("asset_id", Condition.Operator.EQUALS, List.of(assetId)),
          new Condition("type",Condition.Operator.EQUALS, List.of(feedbackType))
        ),
        Condition.LogicalOperator.AND
      );



    SelectQuery query = new SelectQuery()
      .setTable("provider_feedback")
      .setColumns(List.of("*"))
      .setCondition(condition);


    return postgresService.select(query, true).compose(v -> {
      if (!v.isRowsAffected()) {
        InsertQuery insertQuery = new InsertQuery("provider_feedback", List.copyOf(map.keySet()), List.copyOf(map.values()));
        return postgresService.insert(insertQuery)
          .map(res -> {
            LOGGER.debug("Insert result JSON: {}", res.toJson());  // ← add this
            return ProviderFeedback.fromJson(res.toJson());
          });
      } else {
        UpdateQuery updateQuery = new UpdateQuery("provider_feedback", List.copyOf(map.keySet()), List.copyOf(map.values()), condition, null, null);
        return postgresService.update(updateQuery)
          .map(res -> {
            LOGGER.debug("Update result JSON: {}", res.toJson());  // ← add this
            return ProviderFeedback.fromJson(res.toJson());
          });
      }
    });
  }

  @Override
  public Future<ProviderFeedbackPaginatedResponse> fetchProviderFeedbacks(PaginatedRequest request) {

    int page = request.page();
    int size = request.size();
    int offset = (page - 1) * size;

    Map<String, Object> filters = request.filters();
    LOGGER.debug("filters: {}", filters);

    StringBuilder where = new StringBuilder(" WHERE 1=1 ");
    JsonArray params = new JsonArray();
    AtomicInteger index = new AtomicInteger(1);

    java.util.function.BiConsumer<String, Object> applyFilter =
      (column, rawValue) -> {
        if (rawValue == null) return;

        List<?> values =
          rawValue instanceof List<?> list
            ? list
            : List.of(rawValue);

        if (values.isEmpty()) return;

        where.append(" AND ").append(column).append(" IN (");

        for (int i = 0; i < values.size(); i++) {
          if (i > 0) where.append(", ");
          where.append("$").append(index.getAndIncrement());
          params.add(values.get(i));
        }

        where.append(")");
      };

    applyFilter.accept("user_id", filters.get("user_id"));
    applyFilter.accept("asset_id", filters.get("asset_id"));
    applyFilter.accept("type", filters.get("type"));

    int limitIndex = index.getAndIncrement();
    int offsetIndex = index.getAndIncrement();

    String sql =
      """
      SELECT
          id,
          user_id,
          asset_id,
          type,
          data,
          created_at,
          updated_at,
          COUNT(*) OVER() AS total_count
      FROM provider_feedback
      """
        + where
        + """
    ORDER BY created_at DESC
    LIMIT $"""
        + limitIndex
        + " OFFSET $"
        + offsetIndex;

    params.add(size);
    params.add(offset);

    LOGGER.debug("SQL: {}", sql);
    LOGGER.debug("Params: {}", params);

    return postgresService
      .executeQuery(sql, params)
      .map(
        rows -> {

          List<ProviderFeedback> result =
            rows.getRows().stream()
              .map(obj -> {
                JsonObject r = (JsonObject) obj;

                return new ProviderFeedback(
                  UUID.fromString(r.getString("id")),
                  UUID.fromString(r.getString("user_id")),
                  UUID.fromString(r.getString("asset_id")),
                  ProviderFeedbackType.valueOf(r.getString("type")),
                  r.getJsonObject("data"),
                  r.getString("created_at") != null ? LocalDateTime.parse(r.getString("created_at")) : null,
                  r.getString("updated_at") != null ? LocalDateTime.parse(r.getString("updated_at")) : null
                );
              })
              .toList();

          long total = rows.getTotalCount();

          return new ProviderFeedbackPaginatedResponse(
            result,
            PaginationInfo.from(page, size, total));
        });
  }

  @Override
  public Future<Boolean> deleteProviderFeedback(UUID reqId, UUID userId) {

    Condition condition =
      new Condition(
        List.of(
          new Condition("user_id", Condition.Operator.EQUALS, List.of(userId.toString())),
          new Condition("id", Condition.Operator.EQUALS, List.of(reqId.toString()))),
        Condition.LogicalOperator.AND
      );


    DeleteQuery query = new DeleteQuery("provider_feedback",condition,null,null);

    return postgresService.delete(query).map(v -> true);
  }



}
