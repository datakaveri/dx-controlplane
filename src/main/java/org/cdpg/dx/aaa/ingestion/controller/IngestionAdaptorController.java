package org.cdpg.dx.aaa.ingestion.controller;

import static org.cdpg.dx.aaa.apiserver.config.ApiConstants.*;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.apiserver.ApiController;
import org.cdpg.dx.aaa.ingestion.service.IngestionService;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.common.util.RoutingContextHelper;
import org.cdpg.dx.common.validations.idhandler.*;

public class IngestionAdaptorController implements ApiController {
  private static final Logger LOGGER = LogManager.getLogger(IngestionAdaptorController.class);
  private final IngestionService ingestionService;

  public IngestionAdaptorController(IngestionService ingestionService) {
    this.ingestionService = ingestionService;
  }

  @Override
  public void register(RouterBuilder builder) {

    builder
        .operation(POST_ADAPTER)
        .handler(new GetIdFromBodyHandler())
        .handler(this::handleRegisterAdapter);

    builder
        .operation(GET_ADAPTOR_BY_ID)
        .handler(new GetIdFromPathHandler())
        .handler(this::handleGetAdapterDetailsById);

    builder
        .operation(DELETE_ADAPTER_BY_ID)
        .handler(new GetIdFromPathHandler())
        .handler(this::handleDeleteAdapter);

    builder
        .operation(POST_INGESTION_ADAPTER_ENTITIES)
        .handler(this::handlePublishDataFromAdapter);
  }

  private void handleGetAdapterDetailsById(RoutingContext routingContext) {
    String exchangeName = RoutingContextHelper.getId(routingContext);

    ingestionService
        .getAdapterDetails(exchangeName)
        .onSuccess(
            result -> {
              ResponseBuilder.sendSuccess(routingContext, result.toJson());
              /*RoutingContextHelper.setResponseSize(routingContext, 0);*/
            })
        .onFailure(routingContext::fail);
  }

  private void handlePublishDataFromAdapter(RoutingContext routingContext) {
    JsonArray requestJson = routingContext.body().asJsonArray();
    ingestionService
        .publishDataFromAdapter(requestJson)
        .onSuccess(
            result -> {
              /*RoutingContextHelper.setResponseSize(routingContext, 0);*/
              ResponseBuilder.sendSuccess(routingContext, "Item Published");
            })
        .onFailure(routingContext::fail);
  }

  private void handleDeleteAdapter(RoutingContext routingContext) {
    String exchangeName = routingContext.pathParam(ID);
    String userId = routingContext.user().subject();
    ingestionService
        .deleteAdapter(exchangeName, userId)
        .onSuccess(
            result -> {
              /*RoutingContextHelper.setResponseSize(routingContext, 0);*/
              ResponseBuilder.sendSuccess(routingContext, "Adapter deleted");
            })
        .onFailure(routingContext::fail);
  }

  private void handleRegisterAdapter(RoutingContext routingContext) {
    JsonObject requestBody = routingContext.body().asJsonObject();
    String entitiesId = requestBody.getJsonArray("entities").getString(0);
    LOGGER.debug("user " + routingContext.user().principal());
    String userId = routingContext.user().subject();
    ingestionService
        .registerAdapter(entitiesId, userId)
        .onSuccess(
            result -> {
              /*RoutingContextHelper.setResponseSize(routingContext, 0);*/
              ResponseBuilder.sendSuccess(routingContext, result.toJson());
            })
        .onFailure(routingContext::fail);
  }
}
