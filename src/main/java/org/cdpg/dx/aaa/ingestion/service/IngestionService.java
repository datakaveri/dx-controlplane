package org.cdpg.dx.aaa.ingestion.service;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.List;
import org.cdpg.dx.databroker.model.ExchangeSubscribersResponse;
import org.cdpg.dx.databroker.model.RegisterExchangeModel;

public interface IngestionService {
  Future<RegisterExchangeModel> registerAdapter(String entitiesId, String userId);

  Future<Void> deleteAdapter(String exchangeName, String userId);

  Future<ExchangeSubscribersResponse> getAdapterDetails(String exchangeName);

  Future<List<JsonObject>> getAllAdapterDetailsForUser(String iid);
}
