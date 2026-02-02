package org.cdpg.dx.auditing.v2.dao;

import io.vertx.core.Future;

import org.cdpg.dx.aaa.activity.model.ActivityLogRequest;
import org.cdpg.dx.auditing.v2.model.ActivityAuditLogEntity;
import org.cdpg.dx.database.postgres.base.dao.BaseDAO;
import org.cdpg.dx.database.postgres.models.PaginatedResult;

public interface UserActivityLogDao extends BaseDAO<ActivityAuditLogEntity> {
  // Add any custom DAO methods if needed later

  Future<PaginatedResult<ActivityAuditLogEntity>> getAllActivityLogsByUserId(
      ActivityLogRequest activityLogRequest);

  Future<ActivityAuditLogEntity> createActivityLog(ActivityAuditLogEntity activityLogEntity);

  Future<PaginatedResult<ActivityAuditLogEntity>> getAllActivityLogsForAdmin(
      ActivityLogRequest activityLogAdminRequest);
}
