package org.cdpg.dx.aaa.connector.service;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.databroker.service.DataBrokerService;
import org.cdpg.dx.databroker.util.PermissionOpType;
import org.cdpg.dx.databroker.util.Vhosts;

public class ConnectorServiceImpl implements ConnectorService {
  private static final Logger LOGGER = LogManager.getLogger(ConnectorServiceImpl.class);
  private final DataBrokerService dataBrokerService;

  public ConnectorServiceImpl(DataBrokerService dataBrokerService) {
    this.dataBrokerService = dataBrokerService;
  }

  @Override
  public Future<String> createConnector(String userId, String assetId) {
    Promise<String> promise = Promise.promise();
    if (assetId == null || assetId.isEmpty() || userId == null || userId.isEmpty()) {
      promise.fail(new DxBadRequestException("Invalid input or blank value"));
      return promise.future();
    }

    dataBrokerService
        .registerQueue(userId, assetId, Vhosts.IUDX_INTERNAL)
        .compose(
            queueCreated -> {
              LOGGER.info("Queue created in DataBroker {}", queueCreated.toJson());
              return dataBrokerService.queueBinding(
                  "publishEx", assetId, assetId, Vhosts.IUDX_INTERNAL);
            })
        .compose(
            queueBinded -> {
              LOGGER.info("Queue Binding successful");
              return dataBrokerService.updatePermission(
                  userId, "assetId/amq.default", PermissionOpType.ADD_WRITE, Vhosts.IUDX_INTERNAL);
            })
        .onSuccess(
            permissionUpdated -> {
              LOGGER.info("Permission updated successfully");
              promise.complete("Connector created successfully");
            })
        .onFailure(
            err -> {
              LOGGER.error("Error in creating connector: {}", err.getMessage());
              promise.fail(err);
            });
    return promise.future();
  }

    @Override
    public Future<String> deleteConnector(String userId, String assetId) {
        Promise<String> promise = Promise.promise();
        if (assetId == null || assetId.isEmpty() || userId == null || userId.isEmpty()) {
            promise.fail(new DxBadRequestException("Invalid input or blank value"));
            return promise.future();
        }

        dataBrokerService.deleteQueue(assetId, userId, Vhosts.IUDX_INTERNAL)
            .compose(
                queueDeleted -> {
                    LOGGER.info("Queue deleted in DataBroker");
                    return dataBrokerService.updatePermission(
                        userId, "asset/amq.default", PermissionOpType.DELETE_WRITE, Vhosts.IUDX_INTERNAL);
                })
            .onSuccess(
                permissionUpdated -> {
                    LOGGER.info("Permission updated successfully");
                    promise.complete("Connector deleted successfully");
                })
            .onFailure(
                err -> {
                    LOGGER.error("Error in deleting connector: {}", err.getMessage());
                    promise.fail(err);
                });
        return promise.future();
    }
}
