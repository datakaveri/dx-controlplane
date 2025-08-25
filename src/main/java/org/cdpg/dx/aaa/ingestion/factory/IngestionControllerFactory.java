package org.cdpg.dx.aaa.ingestion.factory;

import org.cdpg.dx.aaa.ingestion.controller.IngestionAdaptorController;
import org.cdpg.dx.aaa.ingestion.service.IngestionService;
import org.cdpg.dx.aaa.ingestion.service.IngestionServiceImpl;
import org.cdpg.dx.databroker.service.DataBrokerService;

public class IngestionControllerFactory {

  public static IngestionAdaptorController create(DataBrokerService dataBroker) {
    IngestionService ingestionService = new IngestionServiceImpl(dataBroker);

    return new IngestionAdaptorController(ingestionService);
  }
}
