package org.cdpg.dx.aaa.subscription.service;

import static org.cdpg.dx.aaa.subscription.util.SubscriptionConstants.RESOURCE_NOT_FOUND;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import java.time.LocalDateTime;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.subscription.dao.SubscriptionServiceDAO;
import org.cdpg.dx.aaa.subscription.model.GetAllSubscription;
import org.cdpg.dx.aaa.subscription.model.GetSubscriptionModel;
import org.cdpg.dx.aaa.subscription.model.RegisterSubscription;
import org.cdpg.dx.aaa.subscription.model.SubscriptionDTO;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.exception.DxSubscriptionException;
import org.cdpg.dx.databroker.service.DataBrokerService;
import org.cdpg.dx.databroker.util.PermissionOpType;
import org.cdpg.dx.databroker.util.Vhosts;

public class SubscriptionServiceImpl implements SubscriptionService {
  private static final Logger LOGGER = LogManager.getLogger(SubscriptionServiceImpl.class);
  private final SubscriptionServiceDAO subscriptionServiceDAO;
  private final DataBrokerService dataBrokerService;

  public SubscriptionServiceImpl(
      SubscriptionServiceDAO subscriptionServiceDAO, DataBrokerService dataBrokerService) {
    this.subscriptionServiceDAO = subscriptionServiceDAO;
    this.dataBrokerService = dataBrokerService;
  }

  @Override
  public Future<Void> deleteSubscription(String subscriptionId, String userid) {
    LOGGER.info("subsid to delete :: {}", subscriptionId);
    Promise<Void> promise = Promise.promise();
    getMetaDataFromSubscriptionIdAndUserId(subscriptionId, userid)
        .compose(
            foundEntityId -> {
              String entityId = foundEntityId.getJsonObject(0).getString("entityId");
              String queueName = foundEntityId.getJsonObject(0).getString("queue_name");
              LOGGER.debug("entityId  {} and queueName {} found ", entityId, queueName);
              return subscriptionServiceDAO
                  .deleteSubscriptionBySubId(subscriptionId)
                  .map(v -> queueName);
            })
        .compose(
            queueName -> {
              LOGGER.debug("deleted from postgres successful");
              return dataBrokerService.deleteQueue(queueName, Vhosts.IUDX_PROD).map(v -> queueName);
            })
        .compose(
            deleteQueue -> {
              LOGGER.debug("deleted queue successful");
              return dataBrokerService.updatePermission(
                  userid, deleteQueue, PermissionOpType.DELETE_READ, Vhosts.IUDX_PROD);
            })
        .onSuccess(
            deleteDataBroker -> {
              LOGGER.info("successfully deleted subscription");
              promise.complete();
            })
        .onFailure(
            failure -> {
              LOGGER.error("error:: {}", failure.getMessage());
              promise.fail(failure);
            });
    return promise.future();
  }

  @Override
  public Future<GetSubscriptionModel> getSubscriptionById(String subsId, String userId) {
    LOGGER.info("getSubscription() method started");
    Promise<GetSubscriptionModel> promise = Promise.promise();
    getMetaDataFromSubscriptionIdAndUserId(subsId, userId)
        .compose(
            postgresSuccess -> {
              String entitiesId = postgresSuccess.getJsonObject(0).getString("entityId");
              String queueName = postgresSuccess.getJsonObject(0).getString("queue_name");
              LOGGER.debug("entityId found {}", entitiesId);
              return dataBrokerService
                  .listQueue(queueName, Vhosts.IUDX_PROD)
                  .map(
                      listStream ->
                          new GetSubscriptionModel(listStream, entitiesId, postgresSuccess));
            })
        .onComplete(
            getDataBroker -> {
              if (getDataBroker.succeeded()) {
                promise.complete(getDataBroker.result());
              } else {
                promise.fail(getDataBroker.cause());
              }
            });
    return promise.future();
  }

  @Override
  public Future<GetAllSubscription> getAllSubscriptions(String userId, int limit, int offset) {
    Promise<GetAllSubscription> promise = Promise.promise();
    subscriptionServiceDAO
        .getAllSubscriptionByUserId(userId, limit, offset)
        .onSuccess(
            result -> {
              LOGGER.debug("Fetched all subscriptions for userId: {}", userId);
              promise.complete(result);
            })
        .onFailure(promise::fail);
    return promise.future();
  }

  @Override
  public Future<Void> updateSubscription(String entitiesid, String subsId, LocalDateTime expiryAt) {
    LOGGER.info("updateSubscription() method started");
    Promise<Void> promise = Promise.promise();

    subscriptionServiceDAO
        .getSubscriptionBySubIdAndEntityId(subsId, entitiesid)
        .compose(
            selectQueryHandler -> {
              LOGGER.debug("selectQueryHandler result {}", selectQueryHandler);
              if (selectQueryHandler.isEmpty()) {
                LOGGER.warn("Subscription not found for [subsId,entitiesid]");
                return Future.failedFuture(new DxNotFoundException(RESOURCE_NOT_FOUND));
              }
              return subscriptionServiceDAO.updateSubscriptionExpiryByQueueNameAndEntityId(
                  subsId, /*entitiesid, */ expiryAt);
            })
        .onSuccess(
            pgHandler -> {
              LOGGER.debug("updated in subscription successful");
              promise.complete();
            })
        .onFailure(
            failure -> {
              LOGGER.error("Failed to update subscription {}", failure.getLocalizedMessage());
              promise.fail(failure);
            });

    return promise.future();
  }

  @Override
  public Future<RegisterSubscription> createSubscription(
      String userId,
      UUID subscriptionId,
      String subscriptionName,
      String entitiesId,
      LocalDateTime expiryAt,
      String providerId) {
    LOGGER.info("createSubscription() method started with subscriptionId {}", subscriptionId);
    Promise<RegisterSubscription> promise = Promise.promise();

    String queueName = userId + "/" + subscriptionName;
    dataBrokerService
        .registerQueue(userId, queueName, Vhosts.IUDX_PROD)
        .compose(
            registerQueueHandler -> {
              LOGGER.debug("Queue created");
              return dataBrokerService
                  .queueBinding(entitiesId, queueName, entitiesId, Vhosts.IUDX_PROD)
                  .map(
                      v ->
                          new RegisterSubscription(
                              subscriptionId.toString(),
                              userId,
                              registerQueueHandler.getApiKey(),
                              queueName,
                              registerQueueHandler.getUrl(),
                              registerQueueHandler.getPort(),
                              registerQueueHandler.getvHost()));
            })
        .compose(
            queueBindingHandler -> {
              LOGGER.debug("QueueBinding done");
              return dataBrokerService
                  .updatePermission(userId, queueName, PermissionOpType.ADD_READ, Vhosts.IUDX_PROD)
                  .map(v -> queueBindingHandler);
            })
        .compose(
            updateHandler -> {
              LOGGER.debug("Permission added");
              return subscriptionServiceDAO
                  .insertSubscription(
                      new SubscriptionDTO(
                          subscriptionId,
                          queueName,
                          entitiesId,
                          expiryAt,
                          userId,
                          providerId,
                          userId,
                          null,
                          null))
                  .map(v -> updateHandler);
            })
        .onSuccess(
            successHandler -> {
              LOGGER.info("Subscription created successfully");
              promise.complete(successHandler);
            })
        /*.recover(
        recoverHanlder -> {
          LOGGER.error(
              "Error occurred during subscription creation: {}", recoverHanlder.getMessage());
          // Rollback: Delete the created queue in Data Broker
          return dataBrokerService
              .deleteQueue(queueName, Vhosts.IUDX_PROD)
              .compose(
                  v -> {
                    LOGGER.info("Rolled back: Deleted queue {}", queueName);
                    return Future.failedFuture(recoverHanlder);
                  });
        })*/
        .onFailure(
            failureHandler -> {
              failureHandler.printStackTrace();
              LOGGER.error(
                  "Failed to create subscription {}", failureHandler.getLocalizedMessage());
              promise.fail(failureHandler);
            });

    return promise.future();
  }

  private Future<JsonArray> getMetaDataFromSubscriptionIdAndUserId(
      String subscriptionId, String userId) {
    Promise<JsonArray> promise = Promise.promise();
    subscriptionServiceDAO
        .getEntitiesIdAndQueueNameBySubscriptionIdAndUserId(
            UUID.fromString(subscriptionId), UUID.fromString(userId))
        .onComplete(
            entityResult -> {
              if (entityResult.succeeded()) {
                promise.complete(entityResult.result());
              } else {
                LOGGER.error("error: {}", entityResult.cause().getMessage());
                promise.fail(entityResult.cause());
              }
            });
    return promise.future();
  }
}
