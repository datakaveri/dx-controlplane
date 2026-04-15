package org.cdpg.dx.aaa.asset.models;

import org.cdpg.dx.catalogueService.models.Asset;

public class AssetRequestResponse {

  private final AssetRequest assetRequest;
  private final String itemName;
  private final String accessPolicy;
  private final String type;
  private final Asset asset;

  public AssetRequestResponse(
    AssetRequest assetRequest,
    String itemName,
    String accessPolicy,
    String type,
    Asset asset
  ) {
    this.assetRequest = assetRequest;
    this.itemName = itemName;
    this.accessPolicy = accessPolicy;
    this.type = type;
    this.asset = asset;
  }

  public AssetRequest getAssetRequest() {
    return assetRequest;
  }

  public String getItemName() {
    return itemName;
  }

  public String getAccessPolicy() {
    return accessPolicy;
  }

  public String getType() {
    return type;
  }

  public Asset getAsset() {return asset;}
}
