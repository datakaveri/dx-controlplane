package org.cdpg.dx.deploy.util;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.cdpg.dx.common.exception.DxInternalServerErrorException;

public class ConfigHelper {

  /**
   * Merges required configs into each module config based on the "required" field.
   * If a required config is a JsonObject, its fields are merged into the Verticle config.
   *
   * @param rootConfig The root config JsonObject (parsed from config.json).
   * @return The updated root config with required configs merged into each module.
   */
  public static JsonObject mergeRequiredConfigs(JsonObject rootConfig) {
    if (!rootConfig.containsKey("modules")) {
      throw new DxInternalServerErrorException("Invalid config, modules not present");
    }
    JsonArray modules = rootConfig.getJsonArray("modules");
    for (int i = 0; i < modules.size(); i++) {
      JsonObject module = modules.getJsonObject(i);
      JsonArray requiredArr = module.getJsonArray("required");
      if (requiredArr == null) {
        continue;
      }
      for (int j = 0; j < requiredArr.size(); j++) {
        String key = requiredArr.getString(j);
        if (!rootConfig.containsKey(key)) {
          continue;
        }
        JsonObject value = rootConfig.getJsonObject(key);
        module.mergeIn(value, true);
      }
    }
    return rootConfig;
  }

}
