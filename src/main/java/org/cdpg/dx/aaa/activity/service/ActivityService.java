package org.cdpg.dx.aaa.activity.service;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.activity.model.ActivityLog;
import org.cdpg.dx.aaa.activity.model.ActivityLogRequest;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.database.postgres.models.PaginatedResult;

public interface ActivityService {
  /**
   * This method is used to get the activity log for a given user.
   *
   * @param activityLogRequest The ID of the user for whom the activity log is to be fetched.
   * @return A Future containing the activity log for the specified user.
   */
  Future<PaginatedResult<ActivityLog>> getActivityLogByUserId(ActivityLogRequest activityLogRequest);

  /**
   * This method is used to get all activity logs for admin users.
   *
   * @return A Future containing a list of all activity logs.
   */
  Future<PaginatedResult<ActivityLog>> getAllActivityLogsForAdmin(PaginatedRequest paginatedRequest);

  /**
   * This method is used to create a new activity log.
   *
   * @param activityLogEntity The activity log to be created.
   * @return A Future containing the created activity log.
   */
  Future<Void> insertActivityLogIntoDb(ActivityLog activityLogEntity);

  /**
   * Retrieves a paginated list of activity logs for a consumer based on the provided request.
   *
   * @param paginatedRequest The paginated request containing filter and pagination information.
   * @return A `Future` containing the paginated activity logs for the consumer.
   */
  Future<PaginatedResult<ActivityLog>> getActivityLogForConsumer(PaginatedRequest paginatedRequest);
}