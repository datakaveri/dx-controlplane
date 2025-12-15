package org.cdpg.dx.aaa.activity.factory;

import org.cdpg.dx.aaa.activity.controller.ActivityController;
import org.cdpg.dx.aaa.activity.dao.ActivityLogDao;
import org.cdpg.dx.aaa.activity.dao.ActivityLogDaoNew;
import org.cdpg.dx.aaa.activity.dao.impl.ActivityLogDaoImpl;
import org.cdpg.dx.aaa.activity.dao.impl.ActivityLogDaoImplNew;
import org.cdpg.dx.aaa.activity.service.ActivityLogService;
import org.cdpg.dx.aaa.activity.service.ActivityService;
import org.cdpg.dx.aaa.activity.service.impl.ActivityLogServiceImpl;
import org.cdpg.dx.aaa.activity.service.impl.ActivityServiceImpl;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class ActivityControllerFactory {

  private static ActivityService activityService;

  public static ActivityController create(
      PostgresService postgresService, URNGenerator urnGenerator) {
    ActivityLogDao activityLogDao = new ActivityLogDaoImpl(postgresService);
    ActivityLogDaoNew activityLogDaoNew =
        new ActivityLogDaoImplNew(postgresService, "activity_audit_log");

    activityService = new ActivityServiceImpl(activityLogDao);

    ActivityLogService activityLogService = new ActivityLogServiceImpl(activityLogDaoNew);

    return new ActivityController(activityService, activityLogService, urnGenerator);
  }

  public static ActivityService getActivityService() {
    return activityService;
  }
}
