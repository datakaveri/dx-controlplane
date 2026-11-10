package org.cdpg.dx.aaa.ActivityReport.service.impl;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.ReadStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.ActivityReport.dao.ActivityReportLogDao;
import org.cdpg.dx.aaa.ActivityReport.helper.BatchedCsvReadStream;
import org.cdpg.dx.aaa.ActivityReport.helper.CsvGenerator;
import org.cdpg.dx.aaa.ActivityReport.service.ActivityReportService;
import org.cdpg.dx.common.request.PaginatedRequest;

public class ActivityReportServiceImpl implements ActivityReportService {
  private static final Logger LOGGER = LogManager.getLogger(ActivityReportServiceImpl.class);
  private final ActivityReportLogDao activityLogDAO;
  private final CsvGenerator csvGenerator;
  private final Vertx vertx;

  public ActivityReportServiceImpl(ActivityReportLogDao activityLogDAO, Vertx vertx) {
    this.activityLogDAO = activityLogDAO;
    this.vertx = vertx;
    csvGenerator = new CsvGenerator();
  }

  @Override
  public Future<ReadStream<Buffer>> streamConsumerCsvBatched(PaginatedRequest request) {
    LOGGER.info("Inside streamConsumerCsvBatched method");
    return Future.succeededFuture(
        new BatchedCsvReadStream(activityLogDAO, csvGenerator, vertx, request));
  }

  @Override
  public Future<ReadStream<Buffer>> streamAdminCsvBatched(PaginatedRequest request) {
    LOGGER.info("Inside streamAdminCsvBatched method");
    return Future.succeededFuture(
        new BatchedCsvReadStream(activityLogDAO, csvGenerator, vertx, request));
  }
}
