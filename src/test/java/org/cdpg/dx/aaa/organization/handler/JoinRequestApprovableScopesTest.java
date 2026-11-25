package org.cdpg.dx.aaa.organization.handler;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JoinRequestApprovableScopesTest {

  @Test
  void ownDepartmentShowsMemberRequestsButNotAdminRequests() {
    UUID own = UUID.randomUUID();
    UUID child = UUID.randomUUID();

    List<String> scopes =
        OrganizationJoinRequestHandler.approvableScopes(
            List.of(own.toString(), child.toString()), own);

    assertThat(scopes)
        .containsExactlyInAnyOrder(
            "member:" + own, "member:" + child, "dept_admin:" + child)
        .doesNotContain("dept_admin:" + own);
  }
}
