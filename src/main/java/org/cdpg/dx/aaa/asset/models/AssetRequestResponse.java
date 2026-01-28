package org.cdpg.dx.aaa.asset.models;

public class AssetRequestResponse {

  private final AssetRequest assetRequest;
  private final String itemName;
  private final String accessPolicy;
  private final String type;

  public AssetRequestResponse(
    AssetRequest assetRequest,
    String itemName,
    String accessPolicy,
    String type
  ) {
    this.assetRequest = assetRequest;
    this.itemName = itemName;
    this.accessPolicy = accessPolicy;
    this.type = type;
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
}
