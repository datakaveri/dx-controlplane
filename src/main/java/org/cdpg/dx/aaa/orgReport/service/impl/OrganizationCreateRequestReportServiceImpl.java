package org.cdpg.dx.aaa.orgReport.service.impl;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.ReadStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.credit.dao.ComputeRoleDAO;
import org.cdpg.dx.aaa.credit.dao.CreditDAOFactory;
import org.cdpg.dx.aaa.credit.dao.CreditRequestDAO;
import org.cdpg.dx.aaa.organization.dao.*;
import org.cdpg.dx.aaa.orgReport.helper.*;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.aaa.orgReport.service.OrganizationCreateReportService;

public class OrganizationCreateRequestReportServiceImpl implements OrganizationCreateReportService{
  private static final Logger LOGGER = LogManager.getLogger(OrganizationCreateRequestReportServiceImpl.class);
  private final OrganizationCreateRequestDAO organizationCreateRequestDAO;
  private final OrganizationDAO orgDAO;
  private final OrganizationJoinRequestDAO joinRequestDAO;
  private final ProviderRoleRequestDAO providerRequestDAO;
  private final CsvGenerator csvGenerator;
    private final CsvGeneratorJoin csvGeneratorJoin;
    private final CsvGeneratorOrg csvGeneratorOrg;
    private final CsvGeneratorProvider csvGeneratorProvider;
    private final CsvGeneratorCompute csvGeneratorCompute;
    private final CsvGeneratorCredit csvGeneratorCredit;
    private final ComputeRoleDAO computeRoleDAO;
    private final CreditRequestDAO creditRequestDAO;

  private final Vertx vertx;

  public OrganizationCreateRequestReportServiceImpl(OrganizationDAOFactory factory, CreditDAOFactory creditDAOFactory, Vertx vertx) {
    this.organizationCreateRequestDAO = factory.organizationCreateRequest();
    this.orgDAO = factory.organizationDAO();
    this.joinRequestDAO = factory.organizationJoinRequestDAO();
    this.providerRequestDAO = factory.providerRoleRequestDAO();
    this.computeRoleDAO = creditDAOFactory.computeRoleDAO();
    this.creditRequestDAO = creditDAOFactory.creditRequestDAO();

    this.vertx = vertx;
    csvGenerator = new CsvGenerator();
    csvGeneratorJoin = new CsvGeneratorJoin();
    csvGeneratorOrg = new CsvGeneratorOrg();
    csvGeneratorProvider = new CsvGeneratorProvider();
    csvGeneratorCompute = new CsvGeneratorCompute();
    csvGeneratorCredit = new CsvGeneratorCredit();
  }

  @Override
  public Future<ReadStream<Buffer>> streamConsumerCsvBatched(PaginatedRequest request) {
    LOGGER.info("Inside streamConsumerCsvBatched method");
    return Future.succeededFuture(
        new BatchedCsvReadStream(organizationCreateRequestDAO, csvGenerator, vertx, request));
  }

  @Override
  public Future<ReadStream<Buffer>> streamAdminCsvBatchedCreateRequest(PaginatedRequest request) {
    LOGGER.info("Inside streamAdminCsvBatched method");
    return Future.succeededFuture(
        new BatchedCsvReadStream(organizationCreateRequestDAO, csvGenerator, vertx, request));
  }

  @Override
  public Future<ReadStream<Buffer>> streamAdminCsvBatchedJoinRequest(PaginatedRequest request) {
    LOGGER.info("Inside streamAdminCsvBatched method");
    return Future.succeededFuture(
            new BatchedCsvReadStreamJoin(joinRequestDAO, csvGeneratorJoin, vertx, request));
  }

  @Override
  public Future<ReadStream<Buffer>> streamAdminCsvBatchedProviderRequest(PaginatedRequest request) {
    LOGGER.info("Inside streamAdminCsvBatched method");
    return Future.succeededFuture(
            new BatchedCsvReadStreamProvider(providerRequestDAO, csvGeneratorProvider, vertx, request));
  }

  @Override
  public Future<ReadStream<Buffer>> streamAdminCsvBatchedComputeRequest(PaginatedRequest request) {
    LOGGER.info("Inside streamAdminCsvBatched method");
    return Future.succeededFuture(
            new BatchedCsvReadStreamCompute(computeRoleDAO, csvGeneratorCompute, vertx, request));
  }

  @Override
  public Future<ReadStream<Buffer>> streamAdminCsvBatchedOrganization(PaginatedRequest request) {
    LOGGER.info("Inside streamAdminCsvBatched method");
    return Future.succeededFuture(
            new BatchedCsvReadStreamOrg(orgDAO, csvGeneratorOrg, vertx, request));
  }

  @Override
  public Future<ReadStream<Buffer>> streamAdminCsvBatchedCredit(PaginatedRequest request) {
    LOGGER.info("Inside streamAdminCsvBatched method");
    return Future.succeededFuture(
            new BatchedCsvReadStreamCredit(creditRequestDAO, csvGeneratorCredit, vertx, request));
  }
}
