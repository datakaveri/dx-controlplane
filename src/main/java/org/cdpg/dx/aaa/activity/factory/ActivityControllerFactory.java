package org.cdpg.dx.aaa.activity.factory;

import org.cdpg.dx.aaa.activity.controller.ActivityController;
import org.cdpg.dx.aaa.activity.dao.UserActivityLogDao;
import org.cdpg.dx.aaa.activity.dao.impl.UserActivityLogDaoImpl;
import org.cdpg.dx.aaa.activity.service.UserActivityAuditLogService;
import org.cdpg.dx.aaa.activity.service.impl.UserActivityAuditLogServiceImpl;
import org.cdpg.dx.auth.v2.factory.AuthHandlersV2;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class ActivityControllerFactory {
  public static ActivityController create(
      PostgresService postgresService, URNGenerator urnGenerator, AuthHandlersV2 authV2) {
    UserActivityLogDao userActivityLogDao = new UserActivityLogDaoImpl(postgresService);

    UserActivityAuditLogService userActivityAuditLogService =
        new UserActivityAuditLogServiceImpl(userActivityLogDao);

    return new ActivityController(
        userActivityAuditLogService,
        urnGenerator,
        authV2.authorization());
  }
}