package org.cdpg.dx.aaa.organization.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.UUID;
import org.cdpg.dx.aaa.organization.models.OrganizationHierarchyNode;
import org.junit.jupiter.api.Test;

class DepartmentPathsTest {

  private final UUID orgId = UUID.randomUUID();
  private final UUID google = UUID.randomUUID();
  private final UUID engineering = UUID.randomUUID();
  private final UUID platform = UUID.randomUUID();
  private final UUID dataServices = UUID.randomUUID();

  private OrganizationHierarchyNode node(UUID id, UUID parent, String name, int level, String path) {
    return new OrganizationHierarchyNode(id, orgId, parent, name, level, path, null, null);
  }

  private DepartmentPaths paths() {
    return new DepartmentPaths(
        List.of(
            node(dataServices, platform, "Data Services", 4, "google.engineering.platform.data_services"),
            node(google, null, "Google", 1, "google"),
            node(platform, engineering, "Platform", 3, "google.engineering.platform"),
            node(engineering, google, "Engineering", 2, "google.engineering")));
  }

  @Test
  void describesFullChainFromTopLevelDown() {
    JsonObject department = paths().describe(dataServices);

    assertThat(department.getString("id")).isEqualTo(dataServices.toString());
    assertThat(department.getString("name")).isEqualTo("Data Services");
    assertThat(department.getJsonArray("hierarchy").stream()
            .map(step -> ((JsonObject) step).getString("name")))
        .containsExactly("Google", "Engineering", "Platform", "Data Services");
    assertThat(department.getString("path"))
        .isEqualTo("google.engineering.platform.data_services");
    assertThat(department.containsKey("hierarchyLabel")).isFalse();
  }

  @Test
  void labelJoinsDisplayNamesForEmails() {
    assertThat(paths().label(dataServices))
        .isEqualTo("Google / Engineering / Platform / Data Services");
    assertThat(paths().label(null)).isNull();
    assertThat(paths().label(UUID.randomUUID())).isNull();
  }

  @Test
  void topLevelDepartmentIsItsOwnChain() {
    JsonObject department = paths().describe(google);
    assertThat(department.getJsonArray("hierarchy")).hasSize(1);
    assertThat(department.getString("path")).isEqualTo("google");
  }

  @Test
  void unknownOrMissingDepartmentAddsNothing() {
    JsonObject json = new JsonObject().put("id", "x");
    paths().addTo(json, null);
    paths().addTo(json, UUID.randomUUID());
    assertThat(json.containsKey(DepartmentPaths.DEPARTMENT)).isFalse();

    paths().addTo(json, platform);
    assertThat(json.getJsonObject(DepartmentPaths.DEPARTMENT).getString("path"))
        .isEqualTo("google.engineering.platform");
  }
}
