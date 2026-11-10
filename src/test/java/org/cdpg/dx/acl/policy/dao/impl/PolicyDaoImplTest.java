package org.cdpg.dx.acl.policy.dao.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.database.postgres.models.Condition;
import org.cdpg.dx.database.postgres.models.SelectQuery;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PolicyDaoImplTest {

  private static final PaginatedRequest MULTI_STATUS_REQUEST =
      new PaginatedRequest(
          1,
          10,
          Map.of("owner_id", List.of("owner-1"), "status", List.of("DELETED", "EXPIRED")),
          List.of(),
          List.of(),
          Map.of());

  private PostgresService postgresService;
  private PolicyDaoImpl dao;
  private final List<Object> params = new ArrayList<>();

  @BeforeEach
  void setUp() {
    postgresService = mock(PostgresService.class);
    when(postgresService.select(any(SelectQuery.class), anyBoolean()))
        .thenReturn(Future.failedFuture("not under test"));
    dao = new PolicyDaoImpl(postgresService);
  }

  private String executedWhere() {
    ArgumentCaptor<SelectQuery> captor = ArgumentCaptor.forClass(SelectQuery.class);
    verify(postgresService).select(captor.capture(), anyBoolean());
    return new Condition(captor.getValue().toJson().getJsonObject("condition")).toSQL(params);
  }

  @Test
  @DisplayName("list: multi-select status filter derives expired from expiry_at")
  void listMultiStatusFilter() {
    dao.getAllWithFilters(MULTI_STATUS_REQUEST);

    assertThat(executedWhere()).contains("owner_id").contains(" OR ").contains("expiry_at <");
    assertThat(params).contains("owner-1", "DELETED", "ACTIVE").doesNotContain("EXPIRED");
  }

  @Test
  @DisplayName("access-controlled list: multi-select status filter derives expired from expiry_at")
  void accessControlledMultiStatusFilter() {
    dao.getPoliciesWithAccessControl(MULTI_STATUS_REQUEST, Set.of("policy-1"), "consumer-1");

    assertThat(executedWhere()).contains("consumer_id").contains(" OR ").contains("expiry_at <");
    assertThat(params).contains("DELETED", "ACTIVE", "consumer-1").doesNotContain("EXPIRED");
  }
}
