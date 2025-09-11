package org.cdpg.dx.aaa.connector.service;

import io.vertx.core.Future;

public interface ConnectorService {
    Future<String> createConnector(String userId, String assetId);
    Future<String> deleteConnector(String userId, String assetId);
}
