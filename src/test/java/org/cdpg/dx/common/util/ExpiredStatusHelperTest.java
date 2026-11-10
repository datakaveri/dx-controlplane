package org.cdpg.dx.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.util.ExpiredStatusHelper.StatusFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExpiredStatusHelperTest {

  private static final String LIVE = "GRANTED";
  private static final String EXPIRED = "EXPIRED";

  private final List<Object> params = new ArrayList<>();

  private static StatusFilter rewrite(Object status) {
    Map<String, Object> filters = new HashMap<>();
    filters.put("owner_id", List.of("owner-1"));
    if (status != null) {
      filters.put("status", status);
    }
    return ExpiredStatusHelper.rewriteStatusFilter(
        new PaginatedRequest(1, 10, filters, List.of(), List.of(), Map.of()),
        "status",
        "expiry_at",
        LIVE,
        EXPIRED);
  }

  private String sql(StatusFilter statusFilter) {
    return statusFilter.statusCondition().toSQL(params);
  }

  @Test
  @DisplayName("no status filter needs no rewrite")
  void noStatus() {
    assertThat(rewrite(null)).isNull();
  }

  @Test
  @DisplayName("only stored statuses need no rewrite")
  void storedStatusesOnly() {
    assertThat(rewrite(List.of("REJECTED", "WITHDRAWN"))).isNull();
  }

  @Test
  @DisplayName("single live status matches stored live and not yet expired")
  void singleLive() {
    StatusFilter statusFilter = rewrite(List.of(LIVE));

    assertThat(sql(statusFilter))
        .contains("status =")
        .contains("expiry_at >")
        .contains("expiry_at IS NULL")
        .doesNotContain(" IN ");
    assertThat(params).contains(LIVE);
    assertThat(statusFilter.request().filters()).doesNotContainKey("status").containsKey("owner_id");
  }

  @Test
  @DisplayName("single expired status matches stored live and past expiry")
  void singleExpired() {
    String sql = sql(rewrite(EXPIRED));

    assertThat(sql).contains("expiry_at <").doesNotContain("OR");
    assertThat(params).contains(LIVE).doesNotContain(EXPIRED);
  }

  @Test
  @DisplayName("expired with stored statuses ORs the expiry check with the stored statuses")
  void expiredWithStored() {
    String sql = sql(rewrite(List.of(EXPIRED, "REJECTED", "WITHDRAWN")));

    assertThat(sql).contains(" OR ").contains(" IN ").contains("expiry_at <");
    assertThat(params).contains("REJECTED", "WITHDRAWN", LIVE).doesNotContain(EXPIRED);
  }

  @Test
  @DisplayName("live with a stored status ORs the not-expired check with the stored status")
  void liveWithStored() {
    String sql = sql(rewrite(List.of(LIVE, "REJECTED")));

    assertThat(sql).contains(" OR ").contains("expiry_at >").contains("expiry_at IS NULL");
    assertThat(params).contains("REJECTED", LIVE);
  }

  @Test
  @DisplayName("live and expired together match every stored-live row without an expiry check")
  void liveAndExpired() {
    String sql = sql(rewrite(List.of(LIVE, EXPIRED, "REJECTED")));

    assertThat(sql).contains(" IN ").doesNotContain("expiry_at");
    assertThat(params).contains(LIVE, "REJECTED").doesNotContain(EXPIRED);
  }

  @Test
  @DisplayName("live and expired match case-insensitively; stored values keep their case")
  void caseInsensitive() {
    String sql = sql(rewrite(List.of("expired", "rejected")));

    assertThat(sql).contains("expiry_at <");
    assertThat(params).contains(LIVE, "rejected").doesNotContain("expired");
  }
}
