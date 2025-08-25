package org.cdpg.dx.aaa.ActivityReport.service;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.ReadStream;
import org.cdpg.dx.common.request.PaginatedRequest;

public interface ActivityReportService {

  Future<ReadStream<Buffer>> streamAdminCsvBatched(PaginatedRequest request);

  Future<ReadStream<Buffer>> streamConsumerCsvBatched(PaginatedRequest request);
}
