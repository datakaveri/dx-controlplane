package org.cdpg.dx.aaa.delegationReport.service;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.ReadStream;
import org.cdpg.dx.common.request.PaginatedRequest;

public interface DelegationReportService {
  Future<ReadStream<Buffer>> streamDelegationCsvBatched(PaginatedRequest request);
}
