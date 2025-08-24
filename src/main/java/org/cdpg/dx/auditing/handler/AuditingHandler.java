package org.cdpg.dx.auditing.handler;

import io.vertx.ext.web.RoutingContext;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.auditing.model.AuditLog;
import org.cdpg.dx.common.util.RoutingContextHelper;
import org.cdpg.dx.databroker.service.DataBrokerService;

public class AuditingHandler {
  private static final Logger LOGGER = LogManager.getLogger(AuditingHandler.class);
  private static final List<Integer> STATUS_CODES_TO_AUDIT = List.of(200, 201, 204);

  private final DataBrokerService databrokerService;
  private final String auditingExchange;
  private final String routingKey;

  public AuditingHandler(
      DataBrokerService databrokerService, String auditingExchange, String routingKey) {
    this.auditingExchange = auditingExchange;
    this.routingKey = routingKey;
    this.databrokerService = databrokerService;
  }

  public void handleApiAudit(RoutingContext context) {
    context.addBodyEndHandler(
        v -> {
          try {
            if (!STATUS_CODES_TO_AUDIT.contains(context.response().getStatusCode())) {
              LOGGER.debug(
                  "Skipping audit for status code: {}", context.response().getStatusCode());
              return;
            }
            Optional<List<AuditLog>> auditLogData = RoutingContextHelper.getAuditingLog(context);
            if (auditLogData.isPresent()) {
              publishAuditLogs(auditLogData.get());
            } else {
              LOGGER.warn("No auditing log found in context");
            }

          } catch (Exception e) {
            LOGGER.error("Error: while publishing auditing log: {}", e.getMessage());
            throw new RuntimeException(e);
          }
        });
    context.next();
  }

  public void publishAuditLogs(List<AuditLog> auditLogList) throws Exception {
    LOGGER.trace("AuditingHandler() started");

    auditLogList.forEach(
        log -> {
          LOGGER.info("auditLogData : {}", log.toJson().toString());
          databrokerService
              .publishMessageInternal(log.toJson(), auditingExchange, routingKey)
              .onSuccess(success -> LOGGER.info("Auditing log published successfully"))
              .onFailure(
                  failure ->
                      LOGGER.error("Failed to publish auditing log: {}", failure.getMessage()));
        });
  }
}
