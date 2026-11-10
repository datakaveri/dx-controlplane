package org.cdpg.dx.acl.accessRequest.dao.model;

import org.cdpg.dx.aaa.common.ItemType;

/*Do not allow potential applications as they dont have ownerUserId*/
public enum AssetType {
  DATA_BANK(ItemType.DATA_BANK),
  AI_MODEL(ItemType.AI_MODEL);
  private final ItemType itemType;

  AssetType(ItemType itemType) {
    this.itemType = itemType;
  }

  public static AssetType fromString(String assetType) {
    for (AssetType assetTypeEnum : values()) {
      if (assetTypeEnum.itemType.matches(assetType)) {
        return assetTypeEnum;
      }
    }
    throw new IllegalArgumentException("Invalid assetType: " + assetType);
  }

  public String getAssetType() {
    return itemType.getTypeValue();
  }
}
