package org.cdpg.dx.aaa.orgReport.helper;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.ReadStream;
import java.util.LinkedList;
import java.util.Queue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.organization.dao.OrganizationJoinRequestDAO;
import org.cdpg.dx.common.exception.CsvLimitExceedNoRecordFound;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.database.postgres.models.PaginatedResult;

public class BatchedCsvReadStreamJoin implements ReadStream<Buffer> {
  private static final Logger LOGGER = LogManager.getLogger(BatchedCsvReadStreamJoin.class);
  private final OrganizationJoinRequestDAO dao;
  private final CsvGeneratorJoin generator;
  private final Vertx vertx;
  private final Queue<Buffer> bufferQueue = new LinkedList<>();
  private PaginatedRequest paginatedRequest;
  private boolean writeHeader = true;
  private Handler<Buffer> dataHandler;
  private Handler<Void> endHandler;
  private Handler<Throwable> exceptionHandler;
  private boolean paused = false;
  private boolean ended = false;

  public BatchedCsvReadStreamJoin(
          OrganizationJoinRequestDAO dao, CsvGeneratorJoin generator, Vertx vertx, PaginatedRequest paginatedRequest) {
    this.dao = dao;
    this.generator = generator;
    this.vertx = vertx;
    this.paginatedRequest = paginatedRequest;
    fetchNextBatch();
  }

  private void fetchNextBatch() {
    if (ended) {
      return;
    }
    dao.getAllWithFilters(paginatedRequest)
        .compose(
            batch -> {
              LOGGER.trace(
                  "page {} and size {} totalCount {} totalPages {}",
                  batch.paginationInfo().getPage(),
                  batch.paginationInfo().getSize(),
                  batch.paginationInfo().getTotalCount(),
                  batch.paginationInfo().getTotalPages());

              if (batch.data().isEmpty()) {
                ended = true;
                return Future.failedFuture(
                    new CsvLimitExceedNoRecordFound("No data available for CSV generation"));
              }
              return generator
                  .toCsvStream(batch.data(), vertx, writeHeader)
                  .map(csvStream -> new Object[] {batch, csvStream});
            })
        .onSuccess(
            pair -> {
              var batch = (PaginatedResult<?>) pair[0];
              var csvStream = (ReadStream<Buffer>) pair[1];
              writeHeader = false;
              csvStream.handler(
                  buffer -> {
                    if (paused) {
                      bufferQueue.add(buffer);
                    } else if (dataHandler != null) {
                      dataHandler.handle(buffer);
                    }
                  });
              csvStream.endHandler(
                  v -> {
                    if (batch.paginationInfo().isHasNext()) {
                      paginatedRequest =
                          new PaginatedRequest(
                              batch.paginationInfo().getPage() + 1,
                              batch.paginationInfo().getSize(),
                              paginatedRequest.filters(),
                              paginatedRequest.temporalRequests(),
                              paginatedRequest.orderByList());
                      if (!paused) {
                        fetchNextBatch();
                      }
                    } else {
                      ended = true;
                      if (endHandler != null) endHandler.handle(null);
                    }
                  });
              csvStream.exceptionHandler(
                  e -> {
                    if (exceptionHandler != null)
                      exceptionHandler.handle(
                          new CsvLimitExceedNoRecordFound("No data available for CSV generation"));
                  });
            })
        .onFailure(
            e -> {
              if (exceptionHandler != null) {
                exceptionHandler.handle(
                    new CsvLimitExceedNoRecordFound("Error in CSV generation: " + e.getMessage()));
              }
              ended = true;
            });
  }

  @Override
  public ReadStream<Buffer> handler(Handler<Buffer> handler) {
    this.dataHandler = handler;
    vertx.runOnContext(
        v -> {
          while (!paused && !bufferQueue.isEmpty() && dataHandler != null) {
            dataHandler.handle(bufferQueue.poll());
          }
        });
    return this;
  }

  @Override
  public ReadStream<Buffer> pause() {
    paused = true;
    return this;
  }

  @Override
  public ReadStream<Buffer> resume() {
    if (paused) {
      paused = false;
      vertx.runOnContext(
          v -> {
            while (!paused && !bufferQueue.isEmpty() && dataHandler != null) {
              dataHandler.handle(bufferQueue.poll());
            }
            if (!paused && !ended) {
              fetchNextBatch();
            }
          });
    }
    return this;
  }

  @Override
  public ReadStream<Buffer> fetch(long amount) {
    return this;
  }

  @Override
  public ReadStream<Buffer> endHandler(Handler<Void> handler) {
    this.endHandler = handler;
    return this;
  }

  @Override
  public ReadStream<Buffer> exceptionHandler(Handler<Throwable> handler) {
    this.exceptionHandler = handler;
    return this;
  }
}
