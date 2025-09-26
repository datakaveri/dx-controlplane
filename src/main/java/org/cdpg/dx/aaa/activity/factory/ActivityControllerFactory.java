package org.cdpg.dx.aaa.activity.factory;

import org.cdpg.dx.aaa.activity.controller.ActivityController;
import org.cdpg.dx.aaa.activity.dao.ActivityLogDao;
import org.cdpg.dx.aaa.activity.dao.impl.ActivityLogDaoImpl;
import org.cdpg.dx.aaa.activity.service.ActivityService;
import org.cdpg.dx.aaa.activity.service.impl.ActivityServiceImpl;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class ActivityControllerFactory {

  private static ActivityService activityService;

  public static ActivityController create(
      PostgresService postgresService, URNGenerator urnGenerator) {
    ActivityLogDao activityLogDao = new ActivityLogDaoImpl(postgresService);

    activityService = new ActivityServiceImpl(activityLogDao);

    return new ActivityController(activityService, urnGenerator);
  }

  public static ActivityService getActivityService() {
    return activityService;
  }
}
