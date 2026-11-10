package org.cdpg.dx.aaa.common;

import static org.cdpg.dx.aaa.common.Constants.ITEM_TYPE_PREFIX;
import static org.cdpg.dx.aaa.common.Constants.ITEM_TYPE_PREFIXES;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Platform item types. The type string is "&lt;prefix&gt;:&lt;name&gt;", e.g. adex:DataBank. A
 * deployment accepts one or more prefixes, set from the {@code itemTypePrefixes} config option;
 * every accepted prefix is treated as the same type, and the first one is the primary prefix, used
 * wherever the server produces a type value itself (e.g. the keys of per-type counts).
 */
public enum ItemType {
  AI_MODEL("AiModel"),
  DATA_BANK("DataBank"),
  APPS("Apps");

  public static final String DEFAULT_PREFIX = "adex";

  private static volatile List<String> prefixes = List.of(DEFAULT_PREFIX);

  private final String name;

  ItemType(String name) {
    this.name = name;
  }

  /**
   * Sets the deployment's item type prefixes from {@code itemTypePrefixes} (array, primary first)
   * or, for older configs, {@code itemTypePrefix} (single string). The prefixes are shared by every
   * verticle in the JVM, so a config without either key leaves them unchanged rather than
   * resetting them; if no verticle sets them, {@link #DEFAULT_PREFIX} applies.
   */
  public static void configurePrefix(JsonObject config) {
    List<String> configured = readPrefixes(config);
    if (configured.isEmpty()) {
      return;
    }
    for (String prefix : configured) {
      if (prefix.contains(":")) {
        throw new IllegalArgumentException(
            ITEM_TYPE_PREFIXES + " entries must not contain ':': " + prefix);
      }
    }
    prefixes = List.copyOf(new LinkedHashSet<>(configured));
  }

  private static List<String> readPrefixes(JsonObject config) {
    Object value = config.getValue(ITEM_TYPE_PREFIXES);
    if (value == null) {
      value = config.getValue(ITEM_TYPE_PREFIX);
    }
    List<?> raw =
        value instanceof JsonArray array ? array.getList() : value == null ? List.of() : List.of(value);
    return raw.stream()
        .filter(p -> p != null && !p.toString().isBlank())
        .map(p -> p.toString().strip())
        .toList();
  }

  public static ItemType fromTypeValue(String value) {
    for (ItemType type : ItemType.values()) {
      if (type.matches(value)) {
        return type;
      }
    }
    throw new IllegalArgumentException("No matching ItemType for: " + value);
  }

  public static boolean isPlatformType(String value) {
    return Arrays.stream(ItemType.values()).anyMatch(type -> type.matches(value));
  }

  /** Every accepted type value, i.e. each type under each configured prefix. */
  public static List<String> allTypeValues() {
    return Arrays.stream(ItemType.values()).flatMap(type -> type.typeValues().stream()).toList();
  }

  /**
   * Adds the other-prefix variants of every platform item type in {@code values}, so a filter on
   * adex:DataBank also matches iudx:DataBank when both prefixes are configured. Other values pass
   * through unchanged.
   */
  public static List<Object> withAliases(List<?> values) {
    Set<Object> expanded = new LinkedHashSet<>();
    for (Object value : values) {
      expanded.add(value);
      if (value != null && isPlatformType(value.toString())) {
        expanded.addAll(fromTypeValue(value.toString()).typeValues());
      }
    }
    return new ArrayList<>(expanded);
  }

  /** Applies {@link #withAliases} to the list-valued filter under {@code key}, if present. */
  public static void expandFilterAliases(Map<String, Object> filters, String key) {
    filters.computeIfPresent(key, (k, v) -> v instanceof List<?> types ? withAliases(types) : v);
  }

  /**
   * Folds per-type counts of every accepted prefix into the primary-prefix key, so each platform
   * type is reported once regardless of the prefix its items were created with.
   */
  public static JsonObject mergeAliasCounts(JsonObject counts) {
    JsonObject merged = new JsonObject();
    for (String key : counts.fieldNames()) {
      Object value = counts.getValue(key);
      String mergedKey = isPlatformType(key) ? fromTypeValue(key).getTypeValue() : key;
      if (value instanceof Number count && merged.getValue(mergedKey) instanceof Number existing) {
        merged.put(mergedKey, existing.longValue() + count.longValue());
      } else {
        merged.put(mergedKey, value);
      }
    }
    return merged;
  }

  public boolean matches(String value) {
    return typeValues().stream().anyMatch(typeValue -> typeValue.equalsIgnoreCase(value));
  }

  /** This type under the primary prefix, e.g. adex:DataBank. */
  public String getTypeValue() {
    return prefixes.getFirst() + ":" + name;
  }

  /** This type under every configured prefix, primary first. */
  public List<String> typeValues() {
    return prefixes.stream().map(prefix -> prefix + ":" + name).toList();
  }
}
