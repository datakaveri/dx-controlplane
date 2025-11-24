package org.cdpg.dx.scheduler.service;

import io.vertx.core.*;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.database.postgres.models.*;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.cdpg.dx.databroker.service.DataBrokerService;
import org.cdpg.dx.databroker.util.PermissionOpType;
import org.cdpg.dx.databroker.util.Vhosts;

public class SchedulerServiceImpl implements SchedulerService {

  private static final Logger LOGGER = LogManager.getLogger(SchedulerServiceImpl.class);

  private final PostgresService postgresService;
  private final DataBrokerService dataBrokerService;
  private final String SUBSCRIPTION_TABLE_NAME = "subscriptions";

  public SchedulerServiceImpl(
      Vertx vertx,
      PostgresService postgresService,
      DataBrokerService dataBrokerService,
      int timeIntervalInHours) {

    this.postgresService = postgresService;
    this.dataBrokerService = dataBrokerService;

    vertx.setPeriodic(
        TimeUnit.MINUTES.toMillis(timeIntervalInHours), handler -> checkSubscriptionStatus());
  }

  private Future<Void> checkSubscriptionStatus() {
    LOGGER.debug("Checking subscription status...");

    Condition condition =
        new Condition("expiryAt", Condition.Operator.LESS_EQUALS, List.of(LocalDateTime.now().toString()));

    SelectQuery query =
        new SelectQuery(
            SUBSCRIPTION_TABLE_NAME,
            List.of("id", "queue_name", "user_id", "entityId"),
            condition,
            null,
            null,
            null,
            null);

    return postgresService
        .select(query, false)
        .compose(result -> processExpiredSubscriptions(result.getRows()))
        .onSuccess(v -> LOGGER.info("Completed subscription status check."))
        .onFailure(err -> LOGGER.error("Error checking subscription status: {}", err.getMessage()));
  }

  private Future<Void> processExpiredSubscriptions(JsonArray rows) {

    if (rows.isEmpty()) {
      LOGGER.debug("No expired subscriptions found.");
      return Future.succeededFuture();
    }

    List<Future> ops = new ArrayList<>();

    for (int i = 0; i < rows.size(); i++) {
      JsonObject row = rows.getJsonObject(i);

      String id = row.getString("id");
      String queueName = row.getString("queue_name");
      String userId = row.getString("user_id");

      LOGGER.debug("Processing expired subscription: {}, queue={}", id, queueName);

      Future<Void> op =
          dataBrokerService
              .deleteQueue(queueName, Vhosts.IUDX_PROD)
              .compose(
                  v -> {
                    LOGGER.debug("Deleted queue from Data Broker: {}", queueName);
                    return dataBrokerService.updatePermission(
                        userId, queueName, PermissionOpType.DELETE_READ, Vhosts.IUDX_PROD);
                  })
              .compose(
                  v -> {
                    LOGGER.debug("Updated permissions for queue {}", queueName);

                    Condition cond1 = new Condition("id", Condition.Operator.EQUALS, List.of(id));
                    Condition cond2 =
                        new Condition("queue_name", Condition.Operator.EQUALS, List.of(queueName));

                    Condition combined =
                        new Condition(List.of(cond1, cond2), Condition.LogicalOperator.AND);

                    DeleteQuery deleteQuery =
                        new DeleteQuery(SUBSCRIPTION_TABLE_NAME, combined, null, null);

                    return postgresService.delete(deleteQuery).map(vv -> (Void) null);
                  })
              .onSuccess(v -> LOGGER.info("Successfully deleted subscription id: {}", id))
              .onFailure(
                  err ->
                      LOGGER.error(
                          "Failed processing subscription id {} queue {}: {}",
                          id,
                          queueName,
                          err.getMessage()));

      ops.add(op);
    }

    return CompositeFuture.join(ops).mapEmpty();
  }
}
