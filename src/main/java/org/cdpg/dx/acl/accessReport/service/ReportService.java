package org.cdpg.dx.acl.accessReport.service;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.ReadStream;
import org.cdpg.dx.common.request.PaginatedRequest;

public interface ReportService {

  Future<ReadStream<Buffer>> streamAdminCsvBatched(PaginatedRequest request);

  Future<ReadStream<Buffer>> streamConsumerCsvBatched(PaginatedRequest request);
}
