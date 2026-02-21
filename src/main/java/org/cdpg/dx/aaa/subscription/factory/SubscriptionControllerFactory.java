package org.cdpg.dx.aaa.subscription.factory;

import org.cdpg.dx.aaa.subscription.controller.SubscriptionController;
import org.cdpg.dx.aaa.subscription.dao.SubscriptionServiceDAO;
import org.cdpg.dx.aaa.subscription.dao.SubscriptionServiceDAOImpl;
import org.cdpg.dx.aaa.subscription.service.SubscriptionService;
import org.cdpg.dx.aaa.subscription.service.SubscriptionServiceImpl;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.cdpg.dx.databroker.service.DataBrokerService;

public class SubscriptionControllerFactory {
  public static SubscriptionController create(
      AuditingHandler auditingHandler,
      DataBrokerService dataBrokerService,
      PostgresService postgresService,
      URNGenerator urnGenerator,
      String controlPlaneDomain) {
    SubscriptionServiceDAO subscriptionServiceDAO = new SubscriptionServiceDAOImpl(postgresService);
    SubscriptionService subscriptionService =
        new SubscriptionServiceImpl(subscriptionServiceDAO, dataBrokerService);
    return new SubscriptionController(
        subscriptionService, urnGenerator, auditingHandler, controlPlaneDomain);
  }
}
