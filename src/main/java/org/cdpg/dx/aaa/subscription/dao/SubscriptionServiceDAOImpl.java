package org.cdpg.dx.aaa.subscription.dao;

import static org.cdpg.dx.aaa.subscription.util.SubscriptionConstants.RESOURCE_NOT_FOUND;
import static org.cdpg.dx.aaa.subscription.util.SubscriptionConstants.SUBSCRIPTION_TABLE;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.subscription.model.GetAllSubscription;
import org.cdpg.dx.aaa.subscription.model.SubscriptionDTO;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.database.postgres.base.dao.AbstractBaseDAO;
import org.cdpg.dx.database.postgres.models.*;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class SubscriptionServiceDAOImpl extends AbstractBaseDAO<SubscriptionDTO>
    implements SubscriptionServiceDAO {
  private static final Logger LOGGER = LogManager.getLogger(SubscriptionServiceDAOImpl.class);

  public SubscriptionServiceDAOImpl(PostgresService postgresService) {
    super(postgresService, SUBSCRIPTION_TABLE, "id", SubscriptionDTO::fromJson);
  }

  @Override
  public Future<String> getEntityIdByQueueName(String queueName) {

    Condition condition =
        new Condition("queue_name", Condition.Operator.EQUALS, List.of(queueName));

    SelectQuery query =
        new SelectQuery(tableName, List.of("entityId"), condition, null, null, null, null);

    return postgresService
        .select(query, false)
        .compose(
            result -> {
              JsonArray rows = result.getRows();

              if (rows.isEmpty()) {
                LOGGER.warn("No entityId found for queueName {}", queueName);
                throw new DxNotFoundException(RESOURCE_NOT_FOUND);
              }

              String entityId = rows.getJsonObject(0).getString("entityId");

              if (entityId == null || entityId.isBlank()) {
                throw new DxNotFoundException("Entities ID missing for queueName: " + queueName);
              }

              return Future.succeededFuture(entityId);
            });
  }

  @Override
  public Future<Void> insertSubscription(SubscriptionDTO subscriptionDTO) {
    Promise<Void> promise = Promise.promise();
    var columnNameAndValues = subscriptionDTO.toNonEmptyFieldsMap();

    postgresService
        .insert(
            new InsertQuery(
                tableName,
                List.copyOf(columnNameAndValues.keySet()),
                List.copyOf(columnNameAndValues.values())))
        .onComplete(
            pgHandler -> {
              if (pgHandler.succeeded()) {
                LOGGER.info("Success inserted subs data");
                promise.complete();
              } else {
                LOGGER.error("failed to insert subs data");
                promise.fail(pgHandler.cause());
              }
            });

    return promise.future();
  }

  @Override
  public Future<Void> deleteSubscriptionBySubId(String subscriptionId) {
    Promise<Void> promise = Promise.promise();
    Condition condition = new Condition("id", Condition.Operator.EQUALS, List.of(subscriptionId));
    DeleteQuery deleteQuery = new DeleteQuery(tableName, condition, null, null);

    postgresService
        .delete(deleteQuery)
        .onComplete(
            pgHandler -> {
              if (pgHandler.succeeded()) {
                LOGGER.debug("deleted from postgres");
                promise.complete();
              } else {
                LOGGER.error("fail :: {}", pgHandler.cause().getMessage());
                promise.fail(pgHandler.cause());
              }
            });
    return promise.future();
  }

  @Override
  public Future<GetAllSubscription> getAllSubscriptionByUserId(
      String userId, int limit, int offset) {
    Promise<GetAllSubscription> promise = Promise.promise();
    List<String> columns = List.of("*");
    Condition conditionComponent =
        new Condition("user_id", Condition.Operator.EQUALS, List.of(userId));
    SelectQuery selectQuery =
        new SelectQuery(tableName, columns, conditionComponent, null, null, limit, offset);
    postgresService
        .select(selectQuery, true)
        .onComplete(
            pgHandler -> {
              if (pgHandler.succeeded()) {
                JsonArray result = pgHandler.result().getRows();
                promise.complete(
                    new GetAllSubscription(result, pgHandler.result().getTotalCount()));
              } else {
                promise.fail(pgHandler.cause());
              }
            });
    return promise.future();
  }

  @Override
  public Future<JsonArray> getSubscriptionByQueueNameAndEntityId(
      String queueName, String entityId) {
    Promise<JsonArray> promise = Promise.promise();
    SelectQuery selectQuery = getSelectQueryForQueueNameAndEntityId(queueName, entityId);
    postgresService
        .select(selectQuery, false)
        .onComplete(
            pgHandler -> {
              if (pgHandler.succeeded()) {
                JsonArray result = pgHandler.result().getRows();
                promise.complete(result);
              } else {
                promise.fail(pgHandler.cause());
              }
            });
    return promise.future();
  }

  @Override
  public Future<Void> updateSubscriptionExpiryByQueueNameAndEntityId(
      String subsId, /*String entitiesid,*/ LocalDateTime expiryAt) {
    Promise<Void> promise = Promise.promise();
    Condition subsConditions = new Condition("id", Condition.Operator.EQUALS, List.of(subsId));
    /*Condition entityCondition =
    new Condition("entitiesid", Condition.Operator.EQUALS, List.of(entitiesid));*/

    /*Condition condition =
    new Condition(List.of(queueNameCondition*/
    /*, entityCondition*/
    /*), Condition.LogicalOperator.AND);*/
    UpdateQuery updateQuery =
        new UpdateQuery(
            tableName,
            List.of("expiryAt"),
            List.of(expiryAt.toString()),
            subsConditions,
            null,
            null);

    postgresService
        .update(updateQuery)
        .onComplete(
            pgHandler -> {
              if (pgHandler.succeeded()) {
                LOGGER.info("Success updated expiry");
                promise.complete();
              } else {
                LOGGER.error("failed to update expiry");
                promise.fail(pgHandler.cause());
              }
            });
    return promise.future();
  }

  @Override
  public Future<JsonArray> getEntitiesIdAndQueueNameBySubscriptionId(UUID subscriptionId) {
    Condition subsId =
        new Condition("id", Condition.Operator.EQUALS, List.of(subscriptionId.toString()));
    SelectQuery selectQuery =
        new SelectQuery(
            tableName, List.of("*"), subsId, null, null, null, null);
    return postgresService
        .select(selectQuery, false)
        .compose(
            result -> {
              JsonArray rows = result.getRows();
              if (rows.isEmpty()) {
                LOGGER.warn("No data found for subsId {}", subscriptionId);
                throw new DxNotFoundException(RESOURCE_NOT_FOUND);
              }
              return Future.succeededFuture(rows);
            });
  }

  private SelectQuery getSelectQueryForQueueNameAndEntityId(String queueName, String entityId) {
    Condition queueNameCondition =
        new Condition("queue_name", Condition.Operator.EQUALS, List.of(queueName));
    Condition entityCondition =
        new Condition("entityId", Condition.Operator.EQUALS, List.of(entityId));
    Condition condition =
        new Condition(List.of(queueNameCondition, entityCondition), Condition.LogicalOperator.AND);
    return new SelectQuery(tableName, List.of("*"), condition, null, null, null, null);
  }
}
