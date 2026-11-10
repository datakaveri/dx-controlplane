package org.cdpg.dx.scheduler.verticle;

import static org.cdpg.dx.common.config.ServiceProxyAddressConstants.*;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.serviceproxy.ServiceBinder;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.cdpg.dx.databroker.service.DataBrokerService;
import org.cdpg.dx.scheduler.service.SchedulerService;
import org.cdpg.dx.scheduler.service.SchedulerServiceImpl;

public class SchedulerVerticle extends AbstractVerticle {
  private ServiceBinder binder;
  private MessageConsumer<JsonObject> consumer;
  private PostgresService postgresService;
  private DataBrokerService dataBrokerService;
  private SchedulerVerticle schedulerVerticle;

  @Override
  public void start() throws Exception {
    postgresService = PostgresService.createProxy(vertx, POSTGRES_SERVICE_ADDRESS);
    dataBrokerService = DataBrokerService.createProxy(vertx, DATA_BROKER_SERVICE_ADDRESS);
    int timeIntervalInHours = config().getInteger("schedulerTimeIntervalInMinutes", 2);

    binder = new ServiceBinder(vertx);
    SchedulerService schedulerService =
        new SchedulerServiceImpl(vertx, postgresService, dataBrokerService, timeIntervalInHours);

    consumer =
        binder
            .setAddress(SCHEDULER_SERVICE_ADDRESS)
            .register(SchedulerService.class, schedulerService);
  }

  @Override
  public void stop() throws Exception {
    super.stop();
  }
}
