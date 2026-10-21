package org.cdpg.dx.aaa.organization.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.cdpg.dx.aaa.organization.models.DeptAdminScope.DepartmentFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DeptAdminScopeTest {

  private final UUID orgId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();
  private final UUID adminNode = UUID.randomUUID();
  private final UUID childNode = UUID.randomUUID();
  private final UUID grandchildNode = UUID.randomUUID();

  private DeptAdminScope scopeWithSubtree(List<UUID> nodeIds) {
    return new DeptAdminScope(orgId, userId, adminNode, nodeIds);
  }

  @Test
  @DisplayName("ALL covers the administered department and every sub-department")
  void allCoversWholeSubtree() {
    DeptAdminScope scope = scopeWithSubtree(List.of(adminNode, childNode, grandchildNode));

    assertThat(scope.nodeIdStrings(DepartmentFilter.ALL))
        .containsExactlyInAnyOrder(
            adminNode.toString(), childNode.toString(), grandchildNode.toString());
  }

  @Test
  @DisplayName("OWN covers only the administered department")
  void ownCoversOnlyAdministeredDepartment() {
    DeptAdminScope scope = scopeWithSubtree(List.of(adminNode, childNode, grandchildNode));

    assertThat(scope.nodeIdStrings(DepartmentFilter.OWN)).containsExactly(adminNode.toString());
  }

  @Test
  @DisplayName("SUB covers only the sub-departments")
  void subExcludesAdministeredDepartment() {
    DeptAdminScope scope = scopeWithSubtree(List.of(adminNode, childNode, grandchildNode));

    assertThat(scope.nodeIdStrings(DepartmentFilter.SUB))
        .containsExactlyInAnyOrder(childNode.toString(), grandchildNode.toString());
  }

  @Test
  @DisplayName("SUB on a leaf department matches nothing instead of an empty (dropped) filter")
  void subOnLeafDepartmentMatchesNothing() {
    DeptAdminScope scope = scopeWithSubtree(List.of(adminNode));

    assertThat(scope.nodeIdStrings(DepartmentFilter.SUB))
        .containsExactly(DeptAdminScope.NO_DEPARTMENT);
  }

  @Test
  @DisplayName("departmentScope param defaults to ALL and is case-insensitive")
  void fromParamParsesValues() {
    assertThat(DepartmentFilter.fromParam(null)).isEqualTo(DepartmentFilter.ALL);
    assertThat(DepartmentFilter.fromParam(" ")).isEqualTo(DepartmentFilter.ALL);
    assertThat(DepartmentFilter.fromParam("own")).isEqualTo(DepartmentFilter.OWN);
    assertThat(DepartmentFilter.fromParam("SUB")).isEqualTo(DepartmentFilter.SUB);
  }

  @Test
  @DisplayName("an unknown departmentScope value is rejected")
  void fromParamRejectsUnknownValue() {
    assertThatThrownBy(() -> DepartmentFilter.fromParam("children"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("departmentScope");
  }
}
