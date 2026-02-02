package org.cdpg.dx.auditing.v2.service.impl;

import io.vertx.core.Future;
import org.cdpg.dx.auditing.v2.dao.UserActivityLogDao;
import org.cdpg.dx.auditing.v2.model.ActivityAuditLogEntity;
import org.cdpg.dx.auditing.v2.service.UserActivityAuditLogService;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.database.postgres.models.PaginatedResult;

public class UserActivityAuditLogServiceImpl implements UserActivityAuditLogService {

  private final UserActivityLogDao activityLogDAO;

  public UserActivityAuditLogServiceImpl(UserActivityLogDao activityLogDAO) {
    this.activityLogDAO = activityLogDAO;
  }

  @Override
  public Future<Void> insertUserActivityLogIntoDb(ActivityAuditLogEntity activityLogEntity) {

    return activityLogDAO.createActivityLog(activityLogEntity).mapEmpty();
  }

  @Override
  public Future<PaginatedResult<ActivityAuditLogEntity>> getUserActivityLogForConsumer(
      PaginatedRequest paginatedRequest) {
    return activityLogDAO.getAllWithFilters(paginatedRequest);
  }

  @Override
  public Future<PaginatedResult<ActivityAuditLogEntity>> getAllActivityLogsForAdmin(
      PaginatedRequest paginatedRequest) {
    return activityLogDAO.getAllWithFilters(paginatedRequest);
  }
}
