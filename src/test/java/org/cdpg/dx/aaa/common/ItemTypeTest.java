package org.cdpg.dx.aaa.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ItemTypeTest {

  private static void configure(String... prefixes) {
    ItemType.configurePrefix(new JsonObject().put("itemTypePrefixes", new JsonArray(List.of(prefixes))));
  }

  @AfterEach
  void resetPrefixes() {
    configure(ItemType.DEFAULT_PREFIX);
  }

  @Test
  void defaultsToAdexPrefix() {
    assertThat(ItemType.DATA_BANK.getTypeValue()).isEqualTo("adex:DataBank");
    assertThat(ItemType.allTypeValues())
        .containsExactly("adex:AiModel", "adex:DataBank", "adex:Apps");
  }

  @Test
  void singlePrefixAcceptsOnlyThatPrefix() {
    configure("iudx");

    assertThat(ItemType.DATA_BANK.getTypeValue()).isEqualTo("iudx:DataBank");
    assertThat(ItemType.fromTypeValue("iudx:AiModel")).isEqualTo(ItemType.AI_MODEL);
    assertThat(ItemType.isPlatformType("iudx:Apps")).isTrue();
    assertThat(ItemType.isPlatformType("adex:Apps")).isFalse();
  }

  @Test
  void multiplePrefixesAreTheSameTypeWithFirstAsPrimary() {
    configure("adex", "iudx");

    assertThat(ItemType.DATA_BANK.getTypeValue()).isEqualTo("adex:DataBank");
    assertThat(ItemType.DATA_BANK.typeValues()).containsExactly("adex:DataBank", "iudx:DataBank");
    assertThat(ItemType.fromTypeValue("iudx:DataBank")).isEqualTo(ItemType.DATA_BANK);
    assertThat(ItemType.isPlatformType("tgdex:DataBank")).isFalse();
    assertThat(ItemType.allTypeValues())
        .containsExactly(
            "adex:AiModel", "iudx:AiModel",
            "adex:DataBank", "iudx:DataBank",
            "adex:Apps", "iudx:Apps");
  }

  @Test
  void readsLegacySingleStringKey() {
    ItemType.configurePrefix(new JsonObject().put("itemTypePrefix", "tgdex"));

    assertThat(ItemType.APPS.getTypeValue()).isEqualTo("tgdex:Apps");
  }

  @Test
  void arrayKeyTakesPrecedenceOverLegacyKey() {
    ItemType.configurePrefix(
        new JsonObject()
            .put("itemTypePrefix", "tgdex")
            .put("itemTypePrefixes", new JsonArray().add("forestdx")));

    assertThat(ItemType.APPS.getTypeValue()).isEqualTo("forestdx:Apps");
  }

  @Test
  void missingOrBlankPrefixesKeepCurrentPrefixes() {
    configure("tgdex");

    ItemType.configurePrefix(new JsonObject());
    configure(" ");
    configure();

    assertThat(ItemType.APPS.getTypeValue()).isEqualTo("tgdex:Apps");
  }

  @Test
  void dropsBlankAndDuplicatePrefixes() {
    configure(" iudx ", "", "iudx", "adex");

    assertThat(ItemType.APPS.typeValues()).containsExactly("iudx:Apps", "adex:Apps");
  }

  @Test
  void rejectsPrefixWithColon() {
    assertThatThrownBy(() -> configure("adex", "iudx:"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void fromTypeValue_rejectsOtherTypes() {
    assertThat(ItemType.isPlatformType("iudx:Resource")).isFalse();
    assertThatThrownBy(() -> ItemType.fromTypeValue("iudx:Resource"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void withAliases_addsEveryConfiguredPrefixAndKeepsUnknownValues() {
    configure("adex", "iudx");

    assertThat(ItemType.withAliases(List.of("adex:DataBank", "iudx:Resource")))
        .containsExactly("adex:DataBank", "iudx:DataBank", "iudx:Resource");
    assertThat(ItemType.withAliases(List.of("iudx:Apps", "adex:Apps")))
        .containsExactly("iudx:Apps", "adex:Apps");
  }

  @Test
  void withAliases_isIdentityForSinglePrefix() {
    assertThat(ItemType.withAliases(List.of("adex:AiModel"))).containsExactly("adex:AiModel");
  }

  @Test
  void expandFilterAliases_widensListFilterOnly() {
    configure("adex", "iudx");
    Map<String, Object> filters = new HashMap<>();
    filters.put("asset_type", List.of("iudx:DataBank"));
    filters.put("status", List.of("PENDING"));

    ItemType.expandFilterAliases(filters, "asset_type");

    assertThat(filters.get("asset_type")).isEqualTo(List.of("iudx:DataBank", "adex:DataBank"));
    assertThat(filters.get("status")).isEqualTo(List.of("PENDING"));
  }

  @Test
  void mergeAliasCounts_foldsCountsIntoPrimaryPrefix() {
    configure("iudx", "adex");
    JsonObject counts =
        new JsonObject()
            .put("adex:DataBank", 74)
            .put("iudx:DataBank", 6)
            .put("adex:AiModel", 3)
            .put("iudx:Resource", 5);

    JsonObject merged = ItemType.mergeAliasCounts(counts);

    assertThat(merged.getLong("iudx:DataBank")).isEqualTo(80L);
    assertThat(merged.getLong("iudx:AiModel")).isEqualTo(3L);
    assertThat(merged.getLong("iudx:Resource")).isEqualTo(5L);
    assertThat(merged.fieldNames()).doesNotContain("adex:DataBank", "adex:AiModel");
  }
}
