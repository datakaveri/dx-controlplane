package org.cdpg.dx.aaa.ingestion.service;

import static org.cdpg.dx.databroker.util.Constants.*;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import java.util.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.exception.ExchangeNotFoundException;
import org.cdpg.dx.databroker.model.ExchangeSubscribersResponse;
import org.cdpg.dx.databroker.model.RegisterExchangeModel;
import org.cdpg.dx.databroker.service.DataBrokerService;
import org.cdpg.dx.databroker.util.PermissionOpType;
import org.cdpg.dx.databroker.util.Vhosts;

public class IngestionServiceImpl implements IngestionService {
  private static final Logger LOGGER = LogManager.getLogger(IngestionServiceImpl.class);
  private final DataBrokerService dataBroker;

  public IngestionServiceImpl(DataBrokerService dataBroker) {
    this.dataBroker = dataBroker;
  }

  @Override
  public Future<RegisterExchangeModel> registerAdapter(String assetId, String userId) {
    Promise<RegisterExchangeModel> promise = Promise.promise();
    if (assetId == null || assetId.isEmpty() || userId == null || userId.isEmpty()) {
      promise.fail(new DxBadRequestException("Invalid input or blank value"));
      return promise.future();
    }

    dataBroker
        .registerExchange(userId, assetId, Vhosts.IUDX_PROD)
        .compose(
            meta -> {
              LOGGER.debug("Exchange created in DataBroker");
              return dataBroker
                  .updatePermission(
                      userId, assetId, PermissionOpType.ADD_WRITE, Vhosts.IUDX_PROD)
                  .map(v -> meta);
            })
        .compose(
            meta -> {
              LOGGER.debug("Permission granted. Binding to Database queue...");
              return dataBroker
                  .queueBinding(
                      assetId, DATABASE_QUEUE, assetId, Vhosts.IUDX_PROD)
                  .map(v -> meta);
            })
        /*.compose(
            meta -> {
              LOGGER.debug("Redis queue bound. Binding to Subscription queue...");
              return dataBroker
                  .queueBinding(assetId, QUEUE_SUBS, assetId, Vhosts.IUDX_PROD)
                  .map(v -> meta);
            })*/
        .onSuccess(
            meta -> {
              LOGGER.debug("Adapter metadata inserted successfully.");
              promise.complete(meta);
            })
        .onFailure(
            error -> {
              LOGGER.error("Failed to register adapter: {}", error.getMessage(), error);
              promise.fail(error);
            });
    return promise.future();
  }

  @Override
  public Future<Void> deleteAdapter(String exchangeName, String userId) {
    Promise<Void> promise = Promise.promise();
    dataBroker
        .deleteExchange(exchangeName, userId, Vhosts.IUDX_PROD)
        .compose(
            deletionResult -> {
              LOGGER.info(
                  "Exchange '{}' deleted successfully for user '{}'.", exchangeName, userId);
              return dataBroker.updatePermission(
                  userId, exchangeName, PermissionOpType.DELETE_WRITE, Vhosts.IUDX_PROD);
            })
        .onSuccess(
            pgResult -> {
              LOGGER.info("Adapter '{}' deleted from RMQ.", exchangeName);
              promise.complete();
            })
        .onFailure(
            error -> {
              LOGGER.error(
                  "Failed to delete adapter '{}': {}", exchangeName, error.getMessage(), error);
              promise.fail(error);
            });

    return promise.future();
  }

  @Override
  public Future<ExchangeSubscribersResponse> getAdapterDetails(String exchangeName) {
    Promise<ExchangeSubscribersResponse> promise = Promise.promise();
    dataBroker
        .listExchange(exchangeName, Vhosts.IUDX_PROD)
        .onSuccess(
            result -> {
              if (!result.getSubscribers().isEmpty()) {
                promise.complete(result);
              } else {
                promise.fail(new ExchangeNotFoundException("Exchange not found"));
              }
            })
        .onFailure(promise::fail);
    return promise.future();
  }

  @Override
  public Future<List<JsonObject>> getAllAdapterDetailsForUser(String iid) {
    return null;
  }
}
