package org.cdpg.dx.aaa.activity.dao;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.activity.model.ActivityLog;
import org.cdpg.dx.aaa.activity.model.ActivityLogRequest;
import org.cdpg.dx.database.postgres.base.dao.BaseDAO;
import org.cdpg.dx.database.postgres.models.PaginatedResult;

public interface ActivityLogDao extends BaseDAO<ActivityLog> {
  // Add any custom DAO methods if needed later

  Future<PaginatedResult<ActivityLog>> getAllActivityLogsByUserId(
      ActivityLogRequest activityLogRequest);

  Future<ActivityLog> createActivityLog(ActivityLog activityLogEntity);

  Future<PaginatedResult<ActivityLog>> getAllActivityLogsForAdmin(
      ActivityLogRequest activityLogAdminRequest);
}
