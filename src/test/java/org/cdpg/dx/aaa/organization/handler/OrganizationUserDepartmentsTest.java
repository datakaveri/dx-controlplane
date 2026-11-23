package org.cdpg.dx.aaa.organization.handler;

import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.UUID;
import org.cdpg.dx.aaa.organization.models.OrganizationHierarchyNode;
import org.cdpg.dx.aaa.organization.models.OrganizationUser;
import org.cdpg.dx.aaa.organization.models.Role;
import org.cdpg.dx.aaa.organization.util.DepartmentPaths;
import org.junit.jupiter.api.Test;

class OrganizationUserDepartmentsTest {

  private final UUID orgId = UUID.randomUUID();
  private final UUID transport = UUID.randomUUID();
  private final UUID metro = UUID.randomUUID();

  private final DepartmentPaths departments =
      new DepartmentPaths(
          List.of(
              new OrganizationHierarchyNode(transport, orgId, null, "Transport", 1, "transport", null, null),
              new OrganizationHierarchyNode(metro, orgId, transport, "Metro", 2, "transport.metro", null, null)));

  private OrganizationUser user(Role role, UUID node, UUID adminOf) {
    return new OrganizationUser(
        UUID.randomUUID(), orgId, UUID.randomUUID(), "name", role, "title", "emp", null,
        "a@b.c", node, adminOf, null, null);
  }

  @Test
  void deptAdminRowCarriesBothDepartments() {
    JsonObject json =
        OrganizationUserHandler.toJsonWithDepartments(
            user(Role.DEPT_ADMIN, metro, transport), departments);

    assertThat(json.getJsonObject("department").getString("path"))
        .isEqualTo("transport.metro");
    assertThat(json.getJsonObject("adminOfDepartment").getString("path"))
        .isEqualTo("transport");
  }

  @Test
  void memberOfFlatOrganisationHasNoDepartment() {
    JsonObject json =
        OrganizationUserHandler.toJsonWithDepartments(user(Role.USER, null, null), departments);

    assertThat(json.containsKey("department")).isFalse();
    assertThat(json.containsKey("adminOfDepartment")).isFalse();
  }
}
