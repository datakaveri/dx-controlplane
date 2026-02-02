package org.cdpg.dx.aaa.activity.service;

import io.vertx.core.Future;
import org.cdpg.dx.auditing.v2.model.ActivityAuditLogEntity;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.database.postgres.models.PaginatedResult;

public interface UserActivityAuditLogService {

  /**
   * This method is used to create a new activity log.
   *
   * @param activityLogEntity The activity log to be created.
   * @return A Future containing the created activity log.
   */
  Future<Void> insertUserActivityLogIntoDb(ActivityAuditLogEntity activityLogEntity);

  /**
   * Retrieves a paginated list of activity logs for a consumer based on the provided request.
   *
   * @param paginatedRequest The paginated request containing filter and pagination information.
   * @return A `Future` containing the paginated activity logs for the consumer.
   */
  Future<PaginatedResult<ActivityAuditLogEntity>> getUserActivityLogForConsumer(
      PaginatedRequest paginatedRequest);

  /**
   * This method is used to get all activity logs for admin users.
   *
   * @return A Future containing a list of all activity logs.
   */
  Future<PaginatedResult<ActivityAuditLogEntity>> getAllActivityLogsForAdmin(
      PaginatedRequest paginatedRequest);
}
