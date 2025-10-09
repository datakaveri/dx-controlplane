package org.cdpg.dx.aaa.item.service;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.connector.service.ConnectorService;
import org.cdpg.dx.aaa.ingestion.service.IngestionService;
import org.cdpg.dx.aaa.item.model.Item;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.exception.DxInternalServerErrorException;
import io.vertx.ext.web.client.WebClient;

import static org.cdpg.dx.aaa.common.Constants.ID;
import org.cdpg.dx.aaa.item.model.DataBankCreationResponse;
import org.cdpg.dx.aaa.item.util.DataBankCreationRequest;

public class ItemRegistryServiceImpl implements ItemRegistryService {
  private static final Logger LOGGER = LogManager.getLogger(ItemRegistryServiceImpl.class);

  private final ItemService itemService;
  private final IngestionService ingestionService;
  private final ConnectorService connectorService;
  private final WebClient webClient;
  private final HashMap<String,String> scriptConfigMap;
  private final ScriptGenerationService scriptGenerationService;
  
  public ItemRegistryServiceImpl(ItemService itemService,
                                 IngestionService ingestionService,
                                 ConnectorService connectorService,
                                 WebClient webClient, HashMap<String,String> scriptConfigMap) {
    this.itemService = itemService;
    this.ingestionService = ingestionService;
    this.connectorService = connectorService;
    this.webClient = webClient;
    this.scriptConfigMap=scriptConfigMap;
    this.scriptGenerationService = new ScriptGenerationService();
  }

  @Override
  public Future<DataBankCreationResponse> createDataBankWithIntegrations(DataBankCreationRequest dataBankCreationRequest, Item item) {
      LOGGER.debug("Creating DataBank item");
    String userId = dataBankCreationRequest.getUserId();
    String itemId = item.getId();
    LOGGER.debug("User ID: {}, Item ID: {}", userId, itemId);
    JsonArray resourceServers = dataBankCreationRequest.getOriginalBody().getJsonArray("resourceServer");
    if (resourceServers == null || resourceServers.isEmpty()) {
      return Future.failedFuture(new DxBadRequestException("resourceServer is required and must not be empty"));
    }

    // Early validation - validate all resource server types upfront
    String validationError = validateResourceServers(resourceServers);
    if (validationError != null) {
      return Future.failedFuture(new DxBadRequestException(validationError));
    }

    List<Supplier<Future<Void>>> rollbackActions = new ArrayList<>();
    DataBankCreationResponse response = new DataBankCreationResponse(item);
    
    // Create requestBody once for reuse
    JsonObject requestBody = new JsonObject()
        .put("dataDescriptor", dataBankCreationRequest.getDataDescriptor())
        .put(ID, itemId);
    
      return itemService
              .createItem(item)
              .compose(v -> processResourceServersSequentially(dataBankCreationRequest, resourceServers, requestBody, rollbackActions, response,item))
              .map(v -> response)
              .recover(err -> {
                  LOGGER.error("Error during item creation flow, starting rollbacks. Cause: {}", err.getCause());
                  return performRollbacksSequentially(rollbackActions)
                          .compose(x -> itemService.deleteItem(itemId).mapEmpty())
                          .compose(x -> Future.failedFuture(err));
              });

  }

  private String validateResourceServers(JsonArray resourceServers) {
    for (int i = 0; i < resourceServers.size(); i++) {
      JsonObject rs = resourceServers.getJsonObject(i);
      String datasetType = rs.getString("name", "").toUpperCase();
      if (!isValidDatasetType(datasetType)) {
        return "Unsupported datasetType: " + datasetType + " at index " + i;
      }
    }
    return null; // No validation errors    JsonArray resourceServers = request.getOriginalBody().getJsonArray("resourceServer");

  }

  private boolean isValidDatasetType(String datasetType) {
    return "GATEWAY".equals(datasetType) || "NGSI-LD".equals(datasetType) || "OGC".equals(datasetType) ||"FILE".equals(datasetType);
  }

  private Future<Void> processResourceServersSequentially(DataBankCreationRequest request, JsonArray resourceServers, JsonObject requestBody, List<Supplier<Future<Void>>> rollbackActions, DataBankCreationResponse response,Item item) {
    LOGGER.debug("Processing {} resource servers sequentially for safety", resourceServers.size());
    
    Future<Void> chain = Future.succeededFuture();
    for (int i = 0; i < resourceServers.size(); i++) {
      JsonObject rs = resourceServers.getJsonObject(i);
      String datasetType = rs.getString("name", "").toUpperCase();
      LOGGER.debug("Processing resource server type: {} at index {}", datasetType, i);
      
      chain = chain.compose(v -> handleSingleResourceServer(request, requestBody, datasetType, rollbackActions, response,item));
    }
    return chain;
  }

  private Future<Void> handleSingleResourceServer(DataBankCreationRequest request,JsonObject requestBody, String datasetType, List<Supplier<Future<Void>>> rollbackActions, DataBankCreationResponse response,Item item) {
      LOGGER.debug("Handling resource server of type: {}", datasetType);
      String itemId = item.getId();
      String userId = request.getUserId();
      return switch (datasetType) {
          case "GATEWAY" -> connectorService
                  .createConnector(userId, itemId)
                  .map(queueModel -> {
                      rollbackActions.add(() -> connectorService.deleteConnector(userId, itemId).recover(x -> Future.succeededFuture()));
                      DataBankCreationResponse.ResourceServerResponse rsResponse =
                              new DataBankCreationResponse.ResourceServerResponse("GATEWAY", queueModel.toJson());
                      response.addResourceServer(rsResponse);
                      return null;
                  })
                  .mapEmpty();
          case "NGSI-LD" -> {
              String token = request.getToken();
              yield postToDataPlane(requestBody, token)
                      .compose(v -> {
                          LOGGER.debug("Data Plane index creation successful, proceeding to register adapter");
                          return ingestionService.registerAdapter(itemId, userId);
                      })
                      .map(exchangeModel -> {
                          rollbackActions.add(() -> ingestionService.deleteAdapter(itemId, userId).recover(x -> Future.succeededFuture()));
                          DataBankCreationResponse.ResourceServerResponse rsResponse =
                                  new DataBankCreationResponse.ResourceServerResponse("NGSI-LD", exchangeModel.toJson());
                          response.addResourceServer(rsResponse);
                          return null;
                      })
                      .mapEmpty();
          }
          case "OGC" -> {
              LOGGER.debug("OGC resource server - checking for vector/raster data and generating script");
              yield handleOgcResourceServer(request, response,item);
          }
          case "FILE" -> {
              LOGGER.debug("FILE resource server - no additional processing required, returning success");
              DataBankCreationResponse.ResourceServerResponse rsResponse =
                      new DataBankCreationResponse.ResourceServerResponse("FILE", new JsonObject().put("status", "created"));
              response.addResourceServer(rsResponse);
              yield Future.succeededFuture();
          }
          default -> Future.failedFuture(new DxBadRequestException("Unsupported datasetType: " + datasetType));
      };
  }

  private Future<Void> postToDataPlane(JsonObject requestBody, String bearerToken) {
    String url = scriptConfigMap.get("dataPlaneUrl").concat("/admin/elasticsearch/createIndex");
    if (url == null || url.isBlank()) {
        LOGGER.debug("Resource server URL is missing or blank");
      return Future.failedFuture(new DxBadRequestException("resourceServer url is required"));
    }
    if (bearerToken == null || bearerToken.isBlank()) {
        LOGGER.debug("Bearer token is missing or blank");
      return Future.failedFuture(new DxBadRequestException("Missing Authorization header"));
    }
    
    LOGGER.debug("Making HTTP call to data plane: {}", url);
    return webClient
      .postAbs(url)
      .bearerTokenAuthentication(bearerToken)
      .sendJson(requestBody)
      .compose(resp -> {
        int code = resp.statusCode();
        if (code >= 200 && code < 300) {
            LOGGER.debug("Data Plane call succeeded with status {}", code);
          return Future.succeededFuture();
        } else {
            if(code==401){
                LOGGER.error("Unauthorized access to data plane with status {}", code);
                return Future.failedFuture(new DxBadRequestException("Unauthorized access to data plane"));
            }
          LOGGER.error("Data plane call failed with status {}", code);
          return Future.failedFuture(new DxInternalServerErrorException("Upstream error: " + code));
        }
      });
  }

  private Future<Void> handleOgcResourceServer(DataBankCreationRequest request, DataBankCreationResponse response,Item item) {
    LOGGER.debug("Handling OGC resource server for itemId: {}", item.getId());
    String itemId = item.getId();
    // Find the OGC resource server in the request to check accessType
    JsonArray resourceServers = request.getOriginalBody().getJsonArray("resourceServer");
    JsonObject ogcResourceServer = null;

    for (int i = 0; i < resourceServers.size(); i++) {
      JsonObject rs = resourceServers.getJsonObject(i);
      if ("OGC".equalsIgnoreCase(rs.getString("name"))) {
        ogcResourceServer = rs;
        break;
      }
    }

    // Extract common details for script generation from request body
    String title =  item.getName();
    String description =  item.getShortDescription();
    String authToken = request.getToken();
    
    // Check if this is vector data based on accessType
    if (scriptGenerationService.isVectorData(ogcResourceServer)) {
      LOGGER.debug("Vector data detected in OGC resource server, generating script");
      
      // Generate vector creation script file
      JsonObject fileInfo = scriptGenerationService.generateVectorScriptFile(authToken, itemId, title, description,scriptConfigMap);
      JsonObject scriptResponse = scriptGenerationService.createScriptFileResponse(fileInfo, "vector", itemId);
      
      // Create response with script information
      JsonObject ogcResponse = new JsonObject()
          .put("status", "created")
          .put("dataType", "vector")
          .put("script", scriptResponse);
      
      DataBankCreationResponse.ResourceServerResponse rsResponse =
          new DataBankCreationResponse.ResourceServerResponse("OGC", ogcResponse);
      response.addResourceServer(rsResponse);
      
      LOGGER.info("OGC vector data onboarding script generated for itemId: {}", itemId);
    } else if (scriptGenerationService.isRasterData(ogcResourceServer)) {
      LOGGER.debug("Raster data detected in OGC resource server, generating script");
      
      // Generate raster creation script file
      JsonObject fileInfo = scriptGenerationService.generateRasterScriptFile(authToken, itemId, title, description,scriptConfigMap);
      JsonObject scriptResponse = scriptGenerationService.createScriptFileResponse(fileInfo, "raster", itemId);
      
      // Create response with script information
      JsonObject ogcResponse = new JsonObject()
          .put("status", "created")
          .put("dataType", "raster")
          .put("script", scriptResponse);
      
      DataBankCreationResponse.ResourceServerResponse rsResponse =
          new DataBankCreationResponse.ResourceServerResponse("OGC", ogcResponse);
      response.addResourceServer(rsResponse);
      
      LOGGER.info("OGC raster data onboarding script generated for itemId: {}", itemId);
    } else {
      LOGGER.debug("No vector or raster data detected in OGC resource server");
      DataBankCreationResponse.ResourceServerResponse rsResponse =
          new DataBankCreationResponse.ResourceServerResponse("OGC", new JsonObject().put("status", "created"));
      response.addResourceServer(rsResponse);
    }
    
    return Future.succeededFuture();
  }
  private Future<Void> performRollbacksSequentially(List<Supplier<Future<Void>>> rollbackActions) {
    LOGGER.debug("Performing {} rollback actions sequentially", rollbackActions.size());
    
    Future<Void> chain = Future.succeededFuture();
    for (int i = rollbackActions.size() - 1; i >= 0; i--) {
      final int rollbackIndex = i;
      Supplier<Future<Void>> action = rollbackActions.get(i);
      chain = chain.compose(v -> {
        return action.get().recover(err -> {
          LOGGER.warn("Rollback action {} failed, continuing with other rollbacks: {}", rollbackIndex, err.getMessage());
          return Future.succeededFuture();
        });
      });
    }
    return chain;
  }
}


