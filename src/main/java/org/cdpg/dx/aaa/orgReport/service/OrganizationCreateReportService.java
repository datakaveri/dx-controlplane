package org.cdpg.dx.aaa.orgReport.service;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.ReadStream;
import org.cdpg.dx.common.request.PaginatedRequest;

public interface OrganizationCreateReportService {

  Future<ReadStream<Buffer>> streamAdminCsvBatchedCreateRequest(PaginatedRequest request);

  Future<ReadStream<Buffer>> streamConsumerCsvBatched(PaginatedRequest request);

  Future<ReadStream<Buffer>> streamAdminCsvBatchedJoinRequest(PaginatedRequest request);

  Future<ReadStream<Buffer>> streamAdminCsvBatchedProviderRequest(PaginatedRequest request);

  Future<ReadStream<Buffer>> streamAdminCsvBatchedComputeRequest(PaginatedRequest request);

  Future<ReadStream<Buffer>> streamAdminCsvBatchedOrganization(PaginatedRequest request);

  Future<ReadStream<Buffer>> streamAdminCsvBatchedCredit(PaginatedRequest request);

}
