package org.cdpg.dx.auditing.v2.enrichment;

import io.vertx.core.Future;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.user.service.UserService;
import org.cdpg.dx.auditing.v2.model.ActivityAuditLogEntity;
import org.cdpg.dx.common.model.DxUser;

import java.util.UUID;

public class UserEnrichmentService {

  private static final Logger LOGGER = LogManager.getLogger(UserEnrichmentService.class);

  private final UserService userService;

  public UserEnrichmentService(UserService userService) {
    this.userService = userService;
  }

  /**
   * Best-effort user enrichment. - Never fails the audit pipeline - User / organisation data is
   * OPTIONAL
   */
  public Future<ActivityAuditLogEntity> enrich(ActivityAuditLogEntity entity) {

    UUID userId = entity.getUserId();

    if (userId == null) {
      return Future.succeededFuture(entity);
    }

    return userService
        .getUserInfoByID(userId)
        .map(user -> applyUserInfo(entity, user))
        .recover(
            err -> {
              LOGGER.warn("User enrichment failed for userId={}", userId, err);
              return Future.succeededFuture(entity);
            });
  }

  private ActivityAuditLogEntity applyUserInfo(ActivityAuditLogEntity entity, DxUser user) {

    if (user == null) {
      return entity;
    }
    entity.setUserName(user.name());
    String orgId = user.organisationId();
    if (orgId != null && !orgId.isBlank()) {
      try {
        entity.setOrgId(UUID.fromString(orgId));
      } catch (IllegalArgumentException ignored) {
        LOGGER.warn("No organisationId found/invalid for userId={}: {}", user.sub(), orgId);
      }
    }

    entity.setOrgName(user.organisationName());

    return entity;
  }
}
