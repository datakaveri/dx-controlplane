package org.cdpg.dx.aaa.activity.factory;

import org.cdpg.dx.aaa.activity.dao.ActivityLogDao;
import org.cdpg.dx.aaa.activity.dao.ActivityLogDaoNew;
import org.cdpg.dx.aaa.activity.dao.impl.ActivityLogDaoImpl;
import org.cdpg.dx.aaa.activity.dao.impl.ActivityLogDaoImplNew;
import org.cdpg.dx.aaa.activity.service.ActivityLogService;
import org.cdpg.dx.aaa.activity.service.ActivityService;
import org.cdpg.dx.aaa.activity.service.impl.ActivityLogServiceImpl;
import org.cdpg.dx.aaa.activity.service.impl.ActivityServiceImpl;
import org.cdpg.dx.database.postgres.service.PostgresService;

public final class ActivityFactory {

  private static ActivityLogService activityService;

  private ActivityFactory() {}

  public static void init(PostgresService postgresService) {
    ActivityLogDaoNew dao = new ActivityLogDaoImplNew(postgresService, "activity_audit_log");
    activityService = new ActivityLogServiceImpl(dao);
  }

  public static ActivityLogService getActivityService() {
    if (activityService == null) {
      throw new IllegalStateException("ActivityFactory not initialized. Call init() first.");
    }
    return activityService;
  }
}
