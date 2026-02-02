package org.cdpg.dx.auditing.handler;

import io.vertx.ext.web.RoutingContext;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.activity.model.ActivityAuditLogEntity;

import org.cdpg.dx.aaa.activity.service.ActivityLogService;

import org.cdpg.dx.auditing.model.ActivityAuditLogBuilder;
import org.cdpg.dx.auditing.v2.model.UserActivityAuditLogBuilder;
import org.cdpg.dx.auditing.v2.service.UserActivityAuditLogService;
import org.cdpg.dx.common.util.RoutingContextHelper;
import org.cdpg.dx.databroker.service.DataBrokerService;

public class AuditingHandler {

  private static final Logger LOGGER = LogManager.getLogger(AuditingHandler.class);
  private static final List<Integer> STATUS_CODES_TO_AUDIT = List.of(200, 201, 204);

  private final DataBrokerService databrokerService;
  private final String auditingExchange;
  private final String routingKey;
  private final boolean isRemoteAudit;

  public AuditingHandler(
      DataBrokerService databrokerService,
      String auditingExchange,
      String routingKey,
      boolean isRemoteAudit) {
    this.auditingExchange = auditingExchange;
    this.routingKey = routingKey;
    this.databrokerService = databrokerService;
    this.isRemoteAudit = isRemoteAudit;
  }

  public void handleApiAudit(RoutingContext context) {

    context.addBodyEndHandler(
        v -> {
          int statusCode = context.response().getStatusCode();

          if (!STATUS_CODES_TO_AUDIT.contains(statusCode)) {
            return;
          }

          RoutingContextHelper.getAuditingLogV2(context)
              .ifPresentOrElse(
                  this::publishAuditLogs, () -> LOGGER.warn("No auditing log found in context"));
        });

    context.next();
  }

  private void publishAuditLogs(List<UserActivityAuditLogBuilder> auditLogs) {

    auditLogs.forEach(
        log ->
            databrokerService
                .publishMessageInternal(log.toJson(), auditingExchange, routingKey)
                .onSuccess(v -> LOGGER.debug("Audit log published: {}", log.toJson()))
                .onFailure(
                    err -> LOGGER.error("Failed to publish audit log: {}", err.getMessage(), err)));
  }
}
