package org.cdpg.dx.aaa.activity.dao.impl;

import io.vertx.core.Future;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.activity.dao.UserActivityLogDao;
import org.cdpg.dx.auditing.v2.model.ActivityAuditLogEntity;
import org.cdpg.dx.database.postgres.base.dao.AbstractBaseDAO;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class UserActivityLogDaoImpl extends AbstractBaseDAO<ActivityAuditLogEntity>
    implements UserActivityLogDao {

  private static final Logger LOGGER = LogManager.getLogger(UserActivityLogDaoImpl.class);

  private static final List<String> MINIMAL_COLUMNS =
      List.of("id", "user_id", "asset_name", "operation");

  public UserActivityLogDaoImpl(PostgresService postgresService) {
    super(postgresService, "user_activity_audit_log", "id", ActivityAuditLogEntity::fromJson);
  }

  @Override
  public Future<ActivityAuditLogEntity> createActivityLog(
      ActivityAuditLogEntity activityLogEntity) {
    LOGGER.debug("createActivityLog() called with entity: {}", activityLogEntity);
    return create(activityLogEntity);
  }
}
