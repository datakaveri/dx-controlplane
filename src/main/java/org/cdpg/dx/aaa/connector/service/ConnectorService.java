package org.cdpg.dx.aaa.connector.service;

import io.vertx.core.Future;
import org.cdpg.dx.databroker.model.RegisterQueueModel;

public interface ConnectorService {
  Future<RegisterQueueModel> createConnector(String userId, String assetId);

  Future<String> deleteConnector(String userId, String assetId);
}
