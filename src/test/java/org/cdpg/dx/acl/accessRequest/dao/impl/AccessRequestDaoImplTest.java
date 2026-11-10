package org.cdpg.dx.acl.accessRequest.dao.impl;

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
import org.cdpg.dx.acl.accessRequest.dao.model.AccessRequestDto;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.database.postgres.models.Condition;
import org.cdpg.dx.database.postgres.models.SelectQuery;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AccessRequestDaoImplTest {

  @Test
  @DisplayName("multi-select status filter derives expired from expiry_at")
  void multiStatusFilter() {
    PostgresService postgresService = mock(PostgresService.class);
    when(postgresService.select(any(SelectQuery.class), anyBoolean()))
        .thenReturn(Future.failedFuture("not under test"));

    new AccessRequestDaoImpl(postgresService, "request", "_id", AccessRequestDto::new)
        .getAllWithFilters(
            new PaginatedRequest(
                1,
                10,
                Map.of("owner_id", List.of("owner-1"), "status", List.of("REJECTED", "EXPIRED")),
                List.of(),
                List.of(),
                Map.of()));

    ArgumentCaptor<SelectQuery> captor = ArgumentCaptor.forClass(SelectQuery.class);
    verify(postgresService).select(captor.capture(), anyBoolean());
    List<Object> params = new ArrayList<>();
    String sql =
        new Condition(captor.getValue().toJson().getJsonObject("condition")).toSQL(params);

    assertThat(sql).contains("owner_id").contains(" OR ").contains("expiry_at <");
    assertThat(params).contains("owner-1", "REJECTED", "GRANTED").doesNotContain("EXPIRED");
  }
}
