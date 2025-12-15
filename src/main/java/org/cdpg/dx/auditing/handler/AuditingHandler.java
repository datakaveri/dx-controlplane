package org.cdpg.dx.auditing.handler;

import io.vertx.ext.web.RoutingContext;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.activity.model.ActivityAuditLogEntity;

import org.cdpg.dx.aaa.activity.service.ActivityLogService;

import org.cdpg.dx.auditing.model.ActivityAuditLogBuilder;
import org.cdpg.dx.common.util.RoutingContextHelper;
import org.cdpg.dx.databroker.service.DataBrokerService;

public class AuditingHandler {

  private static final Logger LOGGER = LogManager.getLogger(AuditingHandler.class);
  private static final List<Integer> STATUS_CODES_TO_AUDIT = List.of(200, 201, 204);

  private final DataBrokerService databrokerService;
  private final ActivityLogService activityService;
  private final String auditingExchange;
  private final String routingKey;
  private final boolean isRemoteAudit;

  public AuditingHandler(
      DataBrokerService databrokerService,
      ActivityLogService activityService,
      String auditingExchange,
      String routingKey,
      boolean isRemoteAudit) {
    this.auditingExchange = auditingExchange;
    this.routingKey = routingKey;
    this.databrokerService = databrokerService;
    this.activityService = activityService;
    this.isRemoteAudit = isRemoteAudit;
  }

  public void handleApiAudit(RoutingContext context) {
    LOGGER.debug("AuditingHandler invoked for isRemoteAudit path: {}", isRemoteAudit);
    context.addBodyEndHandler(
        v -> {
          int statusCode = context.response().getStatusCode();

          if (!STATUS_CODES_TO_AUDIT.contains(statusCode)) {
            LOGGER.debug("Skipping audit for status code: {}", statusCode);
            return;
          }

          RoutingContextHelper.getAuditingLogNew(context)
              .ifPresentOrElse(
                  auditLogs -> {
                    LOGGER.info("isRemoteAudit value in AuditingHandler: {}", isRemoteAudit);
                    if (isRemoteAudit) {
                      publishAuditLogs(auditLogs);
                    } else {

                      insertAuditLogIntoDb(auditLogs);
                    }
                  },
                  () -> LOGGER.warn("No auditing log found in context"));
        });

    context.next();
  }

  private void publishAuditLogs(List<ActivityAuditLogBuilder> auditLogs) {
    LOGGER.trace("Publishing audit logs");

    auditLogs.forEach(
        log ->
            databrokerService
                .publishMessageInternal(log.toJson(), auditingExchange, routingKey)
                .onSuccess(
                    success ->
                        LOGGER.info(
                            "Auditing log published successfully for {}", log.getOriginServer()))
                .onFailure(
                    err ->
                        LOGGER.error(
                            "Failed to publish auditing log {}: {}",
                            log.getOriginServer(),
                            err.getMessage(),
                            err)));
  }

  private void insertAuditLogIntoDb(List<ActivityAuditLogBuilder> auditLogs) {
    LOGGER.trace("Inserting audit logs into DB");

    auditLogs.forEach(
        auditLog -> {
          ActivityAuditLogEntity activityLog = ActivityAuditLogEntity.fromJson(auditLog.toJson());

          LOGGER.debug("Inserting activity log into DB: {}", activityLog.toJson());

          activityService
              .insertActivityLogIntoDb(activityLog)
              .onSuccess(
                  success ->
                      LOGGER.info(
                          "Activity log inserted successfully for server {}",
                          auditLog.getOriginServer()))
              .onFailure(
                  err ->
                      LOGGER.error(
                          "Failed to insert activity log for server {}: {}",
                          auditLog.getOriginServer(),
                          err.getMessage(),
                          err));
        });
  }
}
