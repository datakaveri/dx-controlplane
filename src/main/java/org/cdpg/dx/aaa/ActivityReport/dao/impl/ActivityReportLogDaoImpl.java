package org.cdpg.dx.aaa.ActivityReport.dao.impl;

import static org.cdpg.dx.aaa.ActivityReport.util.ActivityConstants.ACTIVITY_LOG_TABLE_NAME;
import static org.cdpg.dx.aaa.activity.util.ActivityConstants.ID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.ActivityReport.dao.ActivityReportLogDao;
import org.cdpg.dx.aaa.ActivityReport.model.ActivityLog;
import org.cdpg.dx.database.postgres.base.dao.AbstractBaseDAO;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class ActivityReportLogDaoImpl extends AbstractBaseDAO<ActivityLog> implements ActivityReportLogDao {
  private static final Logger LOGGER = LogManager.getLogger(ActivityReportLogDaoImpl.class);

  public ActivityReportLogDaoImpl(PostgresService postgresService) {
    super(postgresService, ACTIVITY_LOG_TABLE_NAME, ID, ActivityLog::fromJson);
  }
}
