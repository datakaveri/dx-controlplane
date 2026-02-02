package org.cdpg.dx.auditing.v2.factory;

import org.cdpg.dx.aaa.activity.service.ActivityService;
import org.cdpg.dx.auditing.v2.controller.ActivityController;
import org.cdpg.dx.auditing.v2.dao.UserActivityLogDao;
import org.cdpg.dx.auditing.v2.dao.impl.UserActivityLogDaoImpl;
import org.cdpg.dx.auditing.v2.service.UserActivityAuditLogService;
import org.cdpg.dx.auditing.v2.service.impl.UserActivityAuditLogServiceImpl;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class ActivityControllerFactory {

  private static ActivityService activityService;

  public static ActivityController create(
      PostgresService postgresService, URNGenerator urnGenerator) {
    UserActivityLogDao userActivityLogDao = new UserActivityLogDaoImpl(postgresService);

    UserActivityAuditLogService userActivityAuditLogService =
        new UserActivityAuditLogServiceImpl(userActivityLogDao);

    return new ActivityController(userActivityAuditLogService, urnGenerator);
  }
}
