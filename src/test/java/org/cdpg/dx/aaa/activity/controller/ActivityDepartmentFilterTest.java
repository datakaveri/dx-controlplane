package org.cdpg.dx.aaa.activity.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.vertx.core.MultiMap;
import io.vertx.ext.web.RoutingContext;
import java.util.List;
import org.cdpg.dx.aaa.organization.models.DeptAdminScope;
import org.junit.jupiter.api.Test;

class ActivityDepartmentFilterTest {

  private static RoutingContext withParams(MultiMap params) {
    RoutingContext ctx = mock(RoutingContext.class);
    when(ctx.queryParams()).thenReturn(params);
    return ctx;
  }

  @Test
  void withoutUserIdAllDepartmentUsersAreKept() {
    List<String> users = List.of("u1", "u2");
    assertThat(ActivityController.narrowToRequestedUser(
            withParams(MultiMap.caseInsensitiveMultiMap()), users))
        .isEqualTo(users);
  }

  @Test
  void userIdInsideTheDepartmentNarrowsToThatUser() {
    assertThat(ActivityController.narrowToRequestedUser(
            withParams(MultiMap.caseInsensitiveMultiMap().add("userId", "u2")),
            List.of("u1", "u2")))
        .containsExactly("u2");
  }

  @Test
  void userIdOutsideTheDepartmentMatchesNothing() {
    assertThat(ActivityController.narrowToRequestedUser(
            withParams(MultiMap.caseInsensitiveMultiMap().add("userId", "someone-else")),
            List.of("u1", "u2")))
        .containsExactly(DeptAdminScope.NO_DEPARTMENT);
  }
}
