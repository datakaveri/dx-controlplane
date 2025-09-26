package org.cdpg.dx.aaa.activity.factory;

import org.cdpg.dx.aaa.activity.dao.ActivityLogDao;
import org.cdpg.dx.aaa.activity.dao.impl.ActivityLogDaoImpl;
import org.cdpg.dx.aaa.activity.service.ActivityService;
import org.cdpg.dx.aaa.activity.service.impl.ActivityServiceImpl;
import org.cdpg.dx.database.postgres.service.PostgresService;

public final class ActivityFactory {

  private static ActivityService activityService;

  private ActivityFactory() {}

  public static void init(PostgresService postgresService) {
    ActivityLogDao dao = new ActivityLogDaoImpl(postgresService);
    activityService = new ActivityServiceImpl(dao);
  }

  public static ActivityService getActivityService() {
    if (activityService == null) {
      throw new IllegalStateException("ActivityFactory not initialized. Call init() first.");
    }
    return activityService;
  }
}
