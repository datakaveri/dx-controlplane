package org.cdpg.dx.aaa.activity.service.impl;

import io.vertx.core.Future;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.activity.dao.ActivityLogDao;
import org.cdpg.dx.aaa.activity.model.ActivityLog;
import org.cdpg.dx.aaa.activity.model.ActivityLogRequest;
import org.cdpg.dx.aaa.activity.service.ActivityService;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.database.postgres.models.PaginatedResult;

public class ActivityServiceImpl implements ActivityService {
  private static final Logger LOGGER = LogManager.getLogger(ActivityServiceImpl.class);
  private final ActivityLogDao activityLogDAO;

  public ActivityServiceImpl(ActivityLogDao activityLogDAO) {
    this.activityLogDAO = activityLogDAO;
  }

  @Override
  public Future<PaginatedResult<ActivityLog>> getActivityLogByUserId(
      ActivityLogRequest activityLogRequest) {
    return activityLogDAO.getAllActivityLogsByUserId(activityLogRequest);
  }

  @Override
  public Future<PaginatedResult<ActivityLog>> getAllActivityLogsForAdmin(
      PaginatedRequest paginatedRequest) {
    return activityLogDAO.getAllWithFilters(paginatedRequest);
  }

  @Override
  public Future<Void> insertActivityLogIntoDb(ActivityLog activityLogEntity) {
    LOGGER.debug("Inserting activity log into DB: {}", activityLogEntity);
    return activityLogDAO.createActivityLog(activityLogEntity).mapEmpty();
  }

  @Override
  public Future<PaginatedResult<ActivityLog>> getActivityLogForConsumer(
      PaginatedRequest paginatedRequest) {
    return activityLogDAO.getAllWithFilters(paginatedRequest);
  }
}
