package org.cdpg.dx.common.model;

import io.vertx.core.json.JsonArray;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.cdpg.dx.catalogueService.models.ItemType;

/**
 * A class representing a resource object with item ID, provider ID, resource server URLs, resource
 * server definitions, and item type.
 */
public class ResourceObj {

  private final UUID itemId;
  private final UUID providerId;
  private final List<String> resourceServerUrls;
  private final JsonArray resourceServers;
  private final ItemType itemType;

  private UUID organizationId;

  public ResourceObj(
      UUID itemId,
      UUID providerId,
      List<String> resourceServerUrls,
      JsonArray resourceServers,
      ItemType itemType) {

    this.itemId = itemId;
    this.providerId = providerId;
    this.resourceServerUrls = resourceServerUrls;
    this.resourceServers = resourceServers;
    this.itemType = itemType;
  }

  public UUID getItemId() {
    return itemId;
  }

  public UUID getProviderId() {
    return providerId;
  }

  public List<String> getResourceServerUrls() {
    return resourceServerUrls;
  }

  public JsonArray getResourceServers() {
    return resourceServers;
  }

  public ItemType getItemType() {
    return itemType;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public void setOrganizationId(UUID organizationId) {
    this.organizationId = organizationId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof ResourceObj that)) {
      return false;
    }

    return Objects.equals(itemId, that.itemId)
        && Objects.equals(providerId, that.providerId)
        && Objects.equals(resourceServerUrls, that.resourceServerUrls)
        && Objects.equals(resourceServers, that.resourceServers)
        && itemType == that.itemType;
  }

  @Override
  public int hashCode() {
    return Objects.hash(itemId, providerId, resourceServerUrls, resourceServers, itemType);
  }
}
