package org.cdpg.dx.common.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.cdpg.dx.catalogueService.models.ItemType;

/**
 * A class representing a resource object with item ID, provider ID, resource server URLs,
 * and item type (AIMODEL or DATABANK).
 */
public class ResourceObj {
  private final UUID itemId;
  private final UUID providerId;
  private final List<String> resourceServerUrls;
  private final ItemType itemType;
  private UUID organizationId;

  /**
   * Constructs a new ResourceObj with the given item ID, provider ID, resource server URLs,
   * and item type.
   *
   * @param itemId             The unique ID of the resource item.
   * @param providerId         The unique ID of the provider who owns the resource.
   * @param resourceServerUrls The list of resource server URLs the resource belongs to.
   * @param itemType           The type of item (AIMODEL or DATABANK).
   */
  public ResourceObj(
      UUID itemId,
      UUID providerId,
      List<String> resourceServerUrls,
      ItemType itemType) {
    this.itemId = itemId;
    this.providerId = providerId;
    this.resourceServerUrls = resourceServerUrls;
    this.itemType = itemType;
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
   * Get the provider ID of the resource/resource_group.
   *
   * @return The provider ID as a UUID.
   */
  public UUID getProviderId() {
    return providerId;
  }

  /** @return The list of resource server URLs. */
  public List<String> getResourceServerUrls() {
    return resourceServerUrls;
  }

  /** @return The item type (AIMODEL or DATABANK). */
  public ItemType getItemType() {
    return itemType;
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
        && itemType == that.itemType;
  }

  @Override
  public int hashCode() {
    return Objects.hash(itemId, providerId, resourceServerUrls, itemType);
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public void setOrganizationId(UUID organizationId) {
    this.organizationId = organizationId;
  }
}
