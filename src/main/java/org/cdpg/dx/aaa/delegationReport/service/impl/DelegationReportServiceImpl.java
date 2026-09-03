package org.cdpg.dx.aaa.delegationReport.service.impl;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.ReadStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.delegation.dao.DelegationGrantDAO;
import org.cdpg.dx.aaa.delegationReport.helper.BatchedCsvReadStream;
import org.cdpg.dx.aaa.delegationReport.helper.CsvGenerator;
import org.cdpg.dx.aaa.delegationReport.service.DelegationReportService;
import org.cdpg.dx.common.request.PaginatedRequest;

public class DelegationReportServiceImpl implements DelegationReportService {

  private static final Logger LOGGER = LogManager.getLogger(DelegationReportServiceImpl.class);

  private final DelegationGrantDAO delegationGrantDAO;
  private final CsvGenerator csvGenerator;
  private final Vertx vertx;

  public DelegationReportServiceImpl(DelegationGrantDAO delegationGrantDAO, Vertx vertx) {

    this.delegationGrantDAO = delegationGrantDAO;
    this.vertx = vertx;
    this.csvGenerator = new CsvGenerator();
  }

  @Override
  public Future<ReadStream<Buffer>> streamDelegationCsvBatched(PaginatedRequest request) {

    LOGGER.info("Inside streamDelegationCsvBatched method");

    return Future.succeededFuture(
        new BatchedCsvReadStream(delegationGrantDAO, csvGenerator, vertx, request));
  }
}
