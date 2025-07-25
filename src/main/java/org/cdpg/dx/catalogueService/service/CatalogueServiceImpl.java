package org.cdpg.dx.catalogueService.service;

import static org.cdpg.dx.catalogueService.config.Constants.*;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.accessRequest.dao.model.AssetType;
import org.cdpg.dx.aaa.apiserver.util.Util;
import org.cdpg.dx.catalogueService.client.CatalogueClient;
import org.cdpg.dx.catalogueService.models.Asset;
import org.cdpg.dx.catalogueService.models.ItemType;
import org.cdpg.dx.catalogueService.models.ResourceObj;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.exception.DxInternalServerErrorException;
import org.cdpg.dx.common.exception.DxNotFoundException;

public class CatalogueServiceImpl implements CatalogueService {

  private static final Logger LOGGER = LogManager.getLogger(CatalogueServiceImpl.class);

  private final String apdUrl;
  private final CatalogueClient client;
  private final String catalogueBasePath;

  public CatalogueServiceImpl(CatalogueClient client, String catalogueBasePath, String apdUrl) {
    this.apdUrl = apdUrl;
    this.client = client;
    this.catalogueBasePath = catalogueBasePath;
  }

  @Override
  public Future<ResourceObj> fetchItems(String id) {
    return client
        .get(catalogueBasePath + RELATIONSHIP_PATH, Map.of(ID, id, "rel", "all"))
        .map(
            resultJsonList -> {
              if (resultJsonList == null || resultJsonList.isEmpty()) {
                throw new DxNotFoundException("No Item found in catalogue for id: " + id);
              }
              JsonObject result = resultJsonList.getJsonObject(0);
              return parseResourceItem(id, result);
            });
  }

  @Override
  public Future<Asset> fetchAsset(String id) {
    LOGGER.info("Fetching asset from catalogue for id: {}", id);

    return client
        .get(catalogueBasePath + ITEM_PATH, Map.of(ID, id))
        .map(
            resultJsonList -> {
              if (resultJsonList == null || resultJsonList.isEmpty()) {
                throw new DxNotFoundException("No asset found in catalogue for id: " + id);
              }
              JsonObject result = resultJsonList.getJsonObject(0);
              return parseAndGetAsset(result, id);
            });
  }

  private ResourceObj parseResourceItem(String id, JsonObject result) {
    try {

      List<String> tags = Util.toList(result.getJsonArray(TYPE));

      if (!tags.contains(RESOURCE_ITEM_TAG)) {
        throw new DxBadRequestException("Given id is invalid - Not Resource/Resource group");
      }

      String idFromResponse = result.getString(ID);
      if (idFromResponse == null || !idFromResponse.equals(id)) {
        throw new DxBadRequestException("Given id is invalid");
      }

      UUID provider = UUID.fromString(result.getString(OWNER_ID));
      String resServerUrl = result.getString(RS_URL);
      String apdUrlOfResource = result.getString(APD_URL);

      if (resServerUrl == null
          || resServerUrl.isEmpty()
          || apdUrlOfResource == null
          || apdUrlOfResource.isEmpty()) {
        LOGGER.error(
            "Null values from catalogue - resourceId: {}, provider: {}, rsUrl: {}",
            id,
            provider,
            resServerUrl);
        throw new DxInternalServerErrorException("Incomplete info from catalogue.");
      }

      if (!apdUrl.equals(apdUrlOfResource)) {
        throw new DxInternalServerErrorException(
            "Access forbidden. APD URL mismatch: expected "
                + apdUrl
                + ", found "
                + apdUrlOfResource);
      }

      return new ResourceObj()
          .setItemId(UUID.fromString(id))
          .setProviderId(provider)
          .setResourceGroupId(null) // DO we need this ?
          .setResourceServerUrl(resServerUrl)
          .setGroupLevelResource(false)
          .setItemType(ItemType.RESOURCE);
    } catch (Exception e) {
      LOGGER.error("Error building asset from catalogue metadata: {}", e.getMessage(), e);
      throw new DxInternalServerErrorException("Incomplete Resource  from catalogue");
    }
  }

  private Asset parseAndGetAsset(JsonObject result, String id) {
    LOGGER.debug("Asset info : {}", result.encodePrettily());
    try {
      String assetName = result.getString(ASSET_NAME_KEY, "").trim();
      String provider = result.getString(OWNER_ID);
      String organizationId = result.getString(ORGANIZATION_ID);
      String shortDescription = result.getString(SHORT_DESCRIPTION, "").trim();

      AssetType catAssetType = null;
      JsonArray typeArray = result.getJsonArray(TYPE);
      if (typeArray != null) {
        for (Object type : typeArray) {
          String typeStr = type.toString();
          catAssetType = AssetType.fromString(typeStr);
        }
      }

      // Validation
      if (provider == null
          || assetName.isEmpty()
          || catAssetType == null
          || organizationId == null
          || shortDescription == null) {
        LOGGER.error("Asset metadata invalid for id: {}", id);
        LOGGER.error(
            "Provider: {}, AssetName: {}, AssetType: {}, OrgId: {}, shortDescription : {}",
            provider,
            assetName,
            catAssetType,
            organizationId,
            shortDescription);
        throw new DxInternalServerErrorException("Incomplete asset metadata from catalogue");
      }

      return new Asset()
          .setItemId(id)
          .setProviderId(provider)
          .setOrganizationId(organizationId)
          .setAssetType(catAssetType.getAssetType())
          .setAssetName(assetName)
          .setShortDescription(shortDescription);

    } catch (Exception e) {
      LOGGER.error("Error building asset from catalogue metadata: {}", e.getMessage(), e);
      throw new DxInternalServerErrorException("Incomplete asset metadata from catalogue");
    }
  }
}
