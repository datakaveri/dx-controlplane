package org.cdpg.dx.common.util;

import static org.cdpg.dx.common.util.DateTimeHelper.FORMATTER;

import io.vertx.core.Future;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.cdpg.dx.common.exception.BaseDxException;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.database.postgres.base.dao.AbstractBaseDAO;
import org.cdpg.dx.database.postgres.base.entity.BaseEntity;
import org.cdpg.dx.database.postgres.models.Condition;
import org.cdpg.dx.database.postgres.models.PaginatedResult;
import org.cdpg.dx.database.postgres.models.SelectQuery;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.cdpg.dx.database.postgres.util.ConditionBuilder;

/**
 * Expired is derived from {@code expiry_at} rather than stored: a row keeps its live status (e.g.
 * {@code GRANTED}, {@code ACTIVE}) after {@code expiry_at} passes, and is reported as expired when
 * read. A live row with no {@code expiry_at} never expires.
 */
public final class ExpiredStatusHelper {

  private ExpiredStatusHelper() {}

  /**
   * A status filter rewritten to match the derived status: {@code request} no longer filters on
   * status, and {@code statusCondition} must be ANDed with the request's own conditions.
   */
  public record StatusFilter(PaginatedRequest request, Condition statusCondition) {}

  /** Returns {@code expiredStatus} if a row stored as {@code liveStatus} is past its expiry. */
  public static String effectiveStatus(
      String status, LocalDateTime expiryAt, String liveStatus, String expiredStatus) {
    if (liveStatus.equalsIgnoreCase(status)
        && expiryAt != null
        && !expiryAt.isAfter(LocalDateTime.now())) {
      return expiredStatus;
    }
    return status;
  }

  /**
   * Rewrites a {@code statusKey} filter that includes {@code liveStatus} or {@code expiredStatus}:
   * live means stored live and expiry_at after now or null, expired means stored live and
   * expiry_at before now, and any other status matches the stored value. Multiple statuses are
   * OR-ed together. Returns null if the request needs no rewrite.
   */
  public static StatusFilter rewriteStatusFilter(
      PaginatedRequest request,
      String statusKey,
      String expiryKey,
      String liveStatus,
      String expiredStatus) {
    Object statusFilter = request.filters() != null ? request.filters().get(statusKey) : null;
    if (statusFilter == null) {
      return null;
    }

    // The request builder passes query params as a list, e.g. status=[EXPIRED, REJECTED].
    List<?> values = statusFilter instanceof List<?> list ? list : List.of(statusFilter);
    List<String> statuses = values.stream().map(String::valueOf).distinct().toList();
    boolean wantsLive = statuses.stream().anyMatch(liveStatus::equalsIgnoreCase);
    boolean wantsExpired = statuses.stream().anyMatch(expiredStatus::equalsIgnoreCase);
    if (!wantsLive && !wantsExpired) {
      return null;
    }

    List<Object> stored =
        new ArrayList<>(
            statuses.stream()
                .filter(s -> !liveStatus.equalsIgnoreCase(s) && !expiredStatus.equalsIgnoreCase(s))
                .toList());
    // Live and expired together cover every stored-live row, so no expiry check is needed.
    if (wantsLive && wantsExpired) {
      stored.add(liveStatus);
    }

    List<Condition> anyOf = new ArrayList<>();
    if (!stored.isEmpty()) {
      anyOf.add(
          new Condition(
              statusKey,
              stored.size() == 1 ? Condition.Operator.EQUALS : Condition.Operator.IN,
              stored));
    }
    if (wantsLive != wantsExpired) {
      String now = LocalDateTime.now().format(FORMATTER);
      Condition expiryCondition =
          wantsLive
              ? new Condition(
                  List.of(
                      new Condition(expiryKey, Condition.Operator.GREATER, List.of(now)),
                      new Condition(expiryKey, Condition.Operator.IS_NULL, List.of())),
                  Condition.LogicalOperator.OR)
              : new Condition(expiryKey, Condition.Operator.LESS, List.of(now));
      anyOf.add(
          and(new Condition(statusKey, Condition.Operator.EQUALS, List.of(liveStatus)),
              expiryCondition));
    }

    Map<String, Object> filters = new HashMap<>(request.filters());
    filters.remove(statusKey);

    return new StatusFilter(
        new PaginatedRequest(
            request.page(),
            request.size(),
            filters,
            request.temporalRequests(),
            request.orderByList(),
            request.fuzzyFilters()),
        anyOf.size() == 1 ? anyOf.get(0) : new Condition(anyOf, Condition.LogicalOperator.OR));
  }

  /** ANDs two conditions, either of which may be null. */
  public static Condition and(Condition first, Condition second) {
    if (first == null) {
      return second;
    }
    if (second == null) {
      return first;
    }
    return new Condition(List.of(first, second), Condition.LogicalOperator.AND);
  }

  /**
   * Same paged select as {@link AbstractBaseDAO#getAllWithFilters(PaginatedRequest)}, with the
   * rewritten filter's status condition ANDed in.
   */
  public static <T extends BaseEntity<T>> Future<PaginatedResult<T>> getAllWithStatusFilter(
      AbstractBaseDAO<T> dao,
      PostgresService postgresService,
      String tableName,
      StatusFilter statusFilter) {
    PaginatedRequest request = statusFilter.request();
    int page = request.page() > 0 ? request.page() : 1;
    int size = request.size() > 0 ? request.size() : 10;
    int offset = (page - 1) * size;

    Condition condition =
        and(
            ConditionBuilder.fromFilters(
                request.filters(), request.temporalRequests(), request.fuzzyFilters()),
            statusFilter.statusCondition());

    SelectQuery query =
        new SelectQuery(
            tableName, List.of("*"), condition, null, request.orderByList(), size, offset);

    return postgresService
        .select(query, true)
        .map(result -> dao.toPaginatedResult(result, page, size))
        .recover(err -> Future.failedFuture(BaseDxException.from(err)));
  }
}
