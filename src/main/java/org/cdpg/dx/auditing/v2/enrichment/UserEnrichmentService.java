package org.cdpg.dx.auditing.v2.enrichment;

import io.vertx.core.Future;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.cdpg.dx.auditing.v2.model.ActivityAuditLogEntity;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

import java.util.UUID;

public class UserEnrichmentService {

  private static final Logger LOGGER = LogManager.getLogger(UserEnrichmentService.class);

  private final KeycloakUserService keycloakUserService;

  public UserEnrichmentService(KeycloakUserService keycloakUserService) {
    this.keycloakUserService = keycloakUserService;
  }

  /**
   * Best-effort user enrichment.
   *
   * <p>Rules: - Never fails the audit pipeline - User / organisation data is OPTIONAL - Logs
   * success once, failures as WARN
   */
  public Future<ActivityAuditLogEntity> enrich(ActivityAuditLogEntity entity) {

    UUID userId = entity.getUserId();

    if (userId == null) {
      LOGGER.debug("User enrichment skipped (no userId)");
      return Future.succeededFuture(entity);
    }

    return keycloakUserService
        .getUserById(userId)
        .map(user -> applyUserInfo(entity, user))
        .onSuccess(
            e ->
                LOGGER.info(
                    "User enrichment successful [userId={}, userName={}, orgId={}]",
                    e.getUserId(),
                    e.getUserName(),
                    e.getOrgId()))
        .recover(
            err -> {
              LOGGER.warn(
                  "User enrichment failed [userId={}]. Proceeding without enrichment", userId, err);
              return Future.succeededFuture(entity);
            });
  }

  private ActivityAuditLogEntity applyUserInfo(ActivityAuditLogEntity entity, DxUser user) {

    if (user == null) {
      LOGGER.warn("User enrichment skipped: user not found [userId={}]", entity.getUserId());
      return entity;
    }


    entity.setUserName(user.name());


    String orgId = user.organisationId();
    if (orgId != null && !orgId.isBlank()) {
      try {
        entity.setOrgId(UUID.fromString(orgId));
      } catch (IllegalArgumentException e) {
        LOGGER.warn(
            "Invalid organisationId in user profile [userId={}, orgId={}]", user.sub(), orgId);
      }
    }

    entity.setOrgName(user.organisationName());

    return entity;
  }
}
