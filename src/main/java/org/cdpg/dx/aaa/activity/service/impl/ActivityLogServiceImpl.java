package org.cdpg.dx.aaa.activity.service.impl;

import io.vertx.core.Future;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.activity.dao.ActivityLogDao;
import org.cdpg.dx.aaa.activity.dao.ActivityLogDaoNew;
import org.cdpg.dx.aaa.activity.model.ActivityAuditLogEntity;
import org.cdpg.dx.aaa.activity.model.ActivityLogRequest;
import org.cdpg.dx.aaa.activity.service.ActivityLogService;
import org.cdpg.dx.aaa.activity.service.ActivityService;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.database.postgres.models.PaginatedResult;

public class ActivityLogServiceImpl implements ActivityLogService {
  private static final Logger LOGGER = LogManager.getLogger(ActivityLogServiceImpl.class);
  private final ActivityLogDaoNew activityLogDAO;

  public ActivityLogServiceImpl(ActivityLogDaoNew activityLogDAO) {
    this.activityLogDAO = activityLogDAO;
  }

  @Override
  public Future<PaginatedResult<ActivityAuditLogEntity>> getActivityLogByUserId(
      ActivityLogRequest activityLogRequest) {
    return activityLogDAO.getAllActivityLogsByUserId(activityLogRequest);
  }

  @Override
  public Future<PaginatedResult<ActivityAuditLogEntity>> getAllActivityLogsForAdmin(
      PaginatedRequest paginatedRequest) {
    return activityLogDAO.getAllWithFilters(paginatedRequest);
  }

  @Override
  public Future<Void> insertActivityLogIntoDb(ActivityAuditLogEntity activityLogEntity) {
    LOGGER.debug("Inserting activity log into DB: {}", activityLogEntity);
    return activityLogDAO.createActivityLog(activityLogEntity).mapEmpty();
  }

  @Override
  public Future<PaginatedResult<ActivityAuditLogEntity>> getActivityLogForConsumer(
      PaginatedRequest paginatedRequest) {
    return activityLogDAO.getAllWithFilters(paginatedRequest);
  }
}
