package org.cdpg.dx.aaa.activity.dao;

import io.vertx.core.Future;
import org.cdpg.dx.auditing.v2.model.ActivityAuditLogEntity;
import org.cdpg.dx.database.postgres.base.dao.BaseDAO;

public interface UserActivityLogDao extends BaseDAO<ActivityAuditLogEntity> {
  // Add any custom DAO methods if needed later

  Future<ActivityAuditLogEntity> createActivityLog(ActivityAuditLogEntity activityLogEntity);
}
