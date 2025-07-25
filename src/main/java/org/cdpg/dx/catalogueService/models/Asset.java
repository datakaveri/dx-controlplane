package org.cdpg.dx.catalogueService.models;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.json.JsonObject;

@DataObject
@JsonGen
public class Asset {
  String providerId;
  String itemId;
  String assetName;
  String assetType;
  String organizationId;
  String shortDescription;

  public Asset() {
  }

  public Asset(JsonObject json) {
    AssetConverter.fromJson(json, this);
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    AssetConverter.toJson(this, json);
    return json;
  }

  public Asset(Asset other) {
    this.providerId = other.getProviderId();
    this.itemId = other.getItemId();
    this.assetName = other.getAssetName();
    this.assetType = other.getAssetType();
    this.organizationId = other.getOrganizationId();
    this.shortDescription = other.getShortDescription();
  }

  public String getOrganizationId() {
    return organizationId;
  }

  public Asset setOrganizationId(String organizationId) {
    this.organizationId = organizationId;
    return this;
  }

  public String getProviderId() {
    return providerId;
  }

  public Asset setProviderId(String providerId) {
    this.providerId = providerId;
    return this;
  }

  public String getItemId() {
    return itemId;
  }

  public Asset setItemId(String itemId) {
    this.itemId = itemId;
    return this;
  }

  public String getAssetName() {
    return assetName;
  }

  public Asset setAssetName(String assetName) {
    this.assetName = assetName;
    return this;
  }

  public String getAssetType() {
    return assetType;
  }

  public Asset setAssetType(String assetType) {
    this.assetType = assetType;
    return this;
  }

  public String getShortDescription() {
    return shortDescription;
  }

  public Asset setShortDescription(String shortDescription) {
    this.shortDescription = shortDescription;
    return this;
  }

  @Override
  public String toString() {
    return "Asset{" +
        "providerId='" + providerId + '\'' +
        ", itemId='" + itemId + '\'' +
        ", assetName='" + assetName + '\'' +
        ", assetType='" + assetType + '\'' +
        ", organizationId='" + organizationId + '\'' +
        ", shortDescription='" + shortDescription + '\'' +
        '}';
  }
}