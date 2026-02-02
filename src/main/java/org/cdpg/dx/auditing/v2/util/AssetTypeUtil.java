package org.cdpg.dx.auditing.v2.util;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.Map;
import java.util.Set;

public final class AssetTypeUtil {

  private AssetTypeUtil() {}

  /** Canonical platform asset types */
  private static final Map<String, String> TYPE_MAP =
      Map.of(
          "DATABANK", "DATABANK",
          "AIMODEL", "AI_MODEL",
          "APPS", "USECASE");

  private static final Set<String> REQUIRED_KEYS = TYPE_MAP.keySet();

  /**
   * Extract platform asset type from item JSON.
   *
   * <p>Rules: - `type` is an array - array may contain unrelated values - one of (Apps | DataBank |
   * AiModel) is guaranteed - scan and return the first matching platform type
   */
  public static String extractAssetType(JsonObject itemJson) {

    if (itemJson == null) return null;

    JsonArray types = itemJson.getJsonArray("type");
    if (types == null || types.isEmpty()) return null;

    for (int i = 0; i < types.size(); i++) {

      String raw = types.getString(i);
      if (raw == null) continue;

      // Strip namespace: adex:AiModel → AiModel
      String normalized = raw.contains(":") ? raw.substring(raw.indexOf(':') + 1) : raw;

      // Normalize: AiModel / AI-Model / ai_model → AIMODEL
      normalized = normalized.replace("-", "").replace("_", "").toUpperCase();

      if (REQUIRED_KEYS.contains(normalized)) {
        return TYPE_MAP.get(normalized);
      }
    }

    // Should not happen as per contract, but safe fallback
    return null;
  }
}
