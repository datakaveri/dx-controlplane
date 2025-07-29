package org.cdpg.dx.catalogueService.models;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.json.JsonObject;
import java.util.UUID;

/**
 * A class representing a resource object with item ID, provider ID, and resource group ID
 * (optional). This class is used to store information about a resource/resource_group.
 */

/**
 * Constructs a new ResourceObj with the given item ID, provider ID, and resource group ID. If the
 * item is resource group, the resource group ID will be null.
 *
 * @param itemId The unique ID of the resource item.
 * @param providerId The unique ID of the provider who owns the resource.
 * @param resourceGroupId The unique ID of the resource group to which the resource belongs (can be
 *     null).
 * @param resourceServerUrl The resource server URL to which the resource item belong.
 * @param isGroupLevelResource Boolean which is true when the resource is Rs-Group and vice-versa.
 */

/**
 * A class representing a resource object with item ID, provider ID, and resource group ID
 * (optional). This class is used to store information about a resource/resource_group.
 */
@JsonGen
@DataObject
public class ResourceObj {
  private UUID itemId;
  private UUID providerId;
  private UUID resourceGroupId;
  private String resourceServerUrl;
  private ItemType itemType;
  private boolean isGroupLevelResource;

  public ResourceObj(ResourceObj other) {
    this.itemId = other.getItemId();
    this.providerId = other.getProviderId();
    this.resourceGroupId = other.getResourceGroupId();
    this.resourceServerUrl = other.getResourceServerUrl();
    this.isGroupLevelResource = other.getIsGroupLevelResource();
    this.itemType = other.getItemType();
  }

  public ResourceObj() {
    super();
  }

  public ResourceObj(JsonObject json) {
    ResourceObjConverter.fromJson(json, this);
  }

  public JsonObject toJson() {
    JsonObject jsonObject = new JsonObject();
    ResourceObjConverter.toJson(this, jsonObject);
    return jsonObject;
  }

  /**
   * @param groupLevelResource Boolean which is true when the resource is Rs-Group and vice-verse.
   * @return ResourceObj instance
   */
  public ResourceObj setGroupLevelResource(boolean groupLevelResource) {
    isGroupLevelResource = groupLevelResource;
    return this;
  }

  /**
   * Get the item ID of the resource/resource_group.
   *
   * @return The item ID as a UUID.
   */
  public UUID getItemId() {
    return itemId;
  }

  /**
   * @param itemId The unique ID of the resource item
   * @return ResourceObj instance
   */
  public ResourceObj setItemId(UUID itemId) {
    this.itemId = itemId;
    return this;
  }

  /**
   * Get the provider ID of the resource/resource_group.
   *
   * @return The provider ID as a UUID.
   */
  public UUID getProviderId() {
    return providerId;
  }

  /**
   * @param providerId The unique ID of the provider who owns the resource.
   * @return ResourceObj instance
   */
  public ResourceObj setProviderId(UUID providerId) {
    this.providerId = providerId;
    return this;
  }

  /**
   * Get the resource group ID of the resource.
   *
   * @return The resource group ID as a UUID, or null if the item is resource group.
   */
  public UUID getResourceGroupId() {
    return resourceGroupId;
  }

  /**
   * @param resourceGroupId The unique ID of the resource group to which the resource belongs (can
   *     be null).
   * @return ResourceObj instance
   */
  public ResourceObj setResourceGroupId(UUID resourceGroupId) {
    this.resourceGroupId = resourceGroupId;
    return this;
  }

  /**
   * Get the resource server URL of the resource.
   *
   * @return The resource server URL as a String.
   */
  public String getResourceServerUrl() {
    return resourceServerUrl;
  }

  /**
   * @param resourceServerUrl The resource server URL to which the resource item belong
   * @return ResourceObj instance
   */
  public ResourceObj setResourceServerUrl(String resourceServerUrl) {
    this.resourceServerUrl = resourceServerUrl;
    return this;
  }

  /**
   * Tells if the resource is resource level or resource group level
   *
   * @return RESOURCE_GROUP, if the resource is resource group level, RESOURCE if the item is
   *     resource level
   */
  public ItemType getItemType() {
    return itemType;
  }

  public ResourceObj setItemType(ItemType itemType) {
    this.itemType = itemType;
    return this;
  }

  /**
   * Tells if the resource is resource level or resource group level
   *
   * @return true, if the resource is resource group level, false if the item is resource level
   */
  public boolean getIsGroupLevelResource() {
    return isGroupLevelResource;
  }
}
