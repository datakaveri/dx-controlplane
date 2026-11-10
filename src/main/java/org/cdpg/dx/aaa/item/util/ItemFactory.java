package org.cdpg.dx.aaa.item.util;

import static org.cdpg.dx.aaa.common.Constants.*;

import io.vertx.core.json.JsonObject;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.common.ItemType;
import org.cdpg.dx.aaa.item.model.AiModelItem;
import org.cdpg.dx.aaa.item.model.AppsItem;
import org.cdpg.dx.aaa.item.model.DataBankItem;
import org.cdpg.dx.aaa.item.model.Item;

public class ItemFactory {
  private static final Logger LOGGER = LogManager.getLogger(ItemFactory.class);

  public static Item parse(JsonObject body) {
    List<String> typeList = body.getJsonArray(TYPE).getList();

    if (typeList == null || typeList.isEmpty()) {
      throw new IllegalArgumentException("Missing 'type' field");
    }

    String type = typeList.getFirst();

    if (!ItemType.isPlatformType(type)) {
      throw new IllegalArgumentException("Unsupported type: " + type);
    }
    return switch (ItemType.fromTypeValue(type)) {
      case DATA_BANK -> DataBankItem.fromJson(body);
      case APPS -> AppsItem.fromJson(body);
      case AI_MODEL -> AiModelItem.fromJson(body);
    };
  }

  public static Item from(JsonObject json) {
    List<String> types = json.getJsonArray(TYPE).getList();
    if (types.stream().anyMatch(ItemType.AI_MODEL::matches)) {
      return AiModelItem.fromJson(json);
    } else if (types.stream().anyMatch(ItemType.APPS::matches)) {
      return AppsItem.fromJson(json);
    } else if (types.stream().anyMatch(ItemType.DATA_BANK::matches)) {
      return DataBankItem.fromJson(json);
    }
    throw new IllegalArgumentException("Unknown item type: " + types);
  }
}
