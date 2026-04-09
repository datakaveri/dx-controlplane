package org.cdpg.dx.aaa.credit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cdpg.dx.testutil.VertxFutureAssert.assertFutureFailure;
import static org.cdpg.dx.testutil.VertxFutureAssert.assertFutureSuccess;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.cdpg.dx.aaa.credit.dao.ComputeRoleDAO;
import org.cdpg.dx.aaa.credit.dao.CreditDAOFactory;
import org.cdpg.dx.aaa.credit.dao.CreditRequestDAO;
import org.cdpg.dx.aaa.credit.dao.CreditTransactionDAO;
import org.cdpg.dx.aaa.credit.dao.UserCreditDAO;
import org.cdpg.dx.aaa.credit.models.ComputeRole;
import org.cdpg.dx.aaa.credit.models.CreditRequest;
import org.cdpg.dx.aaa.credit.models.CreditTransaction;
import org.cdpg.dx.aaa.credit.models.Status;
import org.cdpg.dx.aaa.credit.models.UserCredit;
import org.cdpg.dx.auth.authorization.model.DxRole;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.exception.DxConflictException;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.exception.DxValidationException;
import org.cdpg.dx.common.exception.NoRowFoundException;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.util.PaginationInfo;
import org.cdpg.dx.database.postgres.models.PaginatedResult;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
import org.cdpg.dx.testutil.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
@DisplayName("CreditServiceImpl Tests")
class CreditServiceTest {

  @Mock private CreditDAOFactory factory;
  @Mock private CreditRequestDAO creditRequestDAO;
  @Mock private UserCreditDAO userCreditDAO;
  @Mock private CreditTransactionDAO creditTransactionDAO;
  @Mock private ComputeRoleDAO computeRoleDAO;
  @Mock private KeycloakUserService keycloakUserService;

  private CreditServiceImpl creditService;
  private JsonObject config;

  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID REQUEST_ID = UUID.randomUUID();
  private static final UUID TRANSACTED_BY = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    when(factory.creditRequestDAO()).thenReturn(creditRequestDAO);
    when(factory.userCreditDAO()).thenReturn(userCreditDAO);
    when(factory.creditTransactionDAO()).thenReturn(creditTransactionDAO);
    when(factory.computeRoleDAO()).thenReturn(computeRoleDAO);

    config = new JsonObject().put("initialCreditBalance", 100);
    creditService = new CreditServiceImpl(factory, keycloakUserService, config);
  }

  // ---------------------------------------------------------------------------
  // 1. createCreditRequest
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("createCreditRequest")
  class CreateCreditRequest {

    @Test
    @DisplayName("should create credit request when no recent pending request exists")
    void success_noPendingRequest(VertxTestContext ctx) {
      CreditRequest request = TestDataFactory.aCreditRequest(USER_ID);
      CreditRequest created = TestDataFactory.aCreditRequest(USER_ID, "pending");

      when(creditRequestDAO.getAllWithFilters(anyMap()))
          .thenReturn(Future.succeededFuture(Collections.emptyList()));
      when(creditRequestDAO.create(request)).thenReturn(Future.succeededFuture(created));

      Future<CreditRequest> future = creditService.createCreditRequest(request);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isNotNull();
            assertThat(result.userId()).isEqualTo(USER_ID);
            verify(creditRequestDAO).create(request);
          });
    }

    @Test
    @DisplayName("should fail with DxForbiddenException when recent pending request exists within 1 hour")
    void fail_recentPendingRequest(VertxTestContext ctx) {
      CreditRequest request = TestDataFactory.aCreditRequest(USER_ID);

      // Create a pending request with requestedAt = now (within 1 hour)
      CreditRequest recentPending =
          new CreditRequest(
              UUID.randomUUID(),
              USER_ID,
              "Test User",
              new JsonObject().put("reason", "testing"),
              "pending",
              LocalDateTime.now(),
              null);

      when(creditRequestDAO.getAllWithFilters(anyMap()))
          .thenReturn(Future.succeededFuture(List.of(recentPending)));

      Future<CreditRequest> future = creditService.createCreditRequest(request);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(DxForbiddenException.class);
            assertThat(err.getMessage()).contains("already pending");
            verify(creditRequestDAO, never()).create(any());
          });
    }

    @Test
    @DisplayName("should create credit request when old pending request exists (over 1 hour ago)")
    void success_oldPendingRequest(VertxTestContext ctx) {
      CreditRequest request = TestDataFactory.aCreditRequest(USER_ID);
      CreditRequest created = TestDataFactory.aCreditRequest(USER_ID, "pending");

      // Create a pending request with requestedAt = 2 hours ago (outside 1 hour window)
      // Use UTC because CreditServiceImpl converts requestedAt to Instant via ZoneOffset.UTC
      CreditRequest oldPending =
          new CreditRequest(
              UUID.randomUUID(),
              USER_ID,
              "Test User",
              new JsonObject().put("reason", "old"),
              "pending",
              LocalDateTime.now(java.time.ZoneOffset.UTC).minusHours(2),
              null);

      when(creditRequestDAO.getAllWithFilters(anyMap()))
          .thenReturn(Future.succeededFuture(List.of(oldPending)));
      when(creditRequestDAO.create(request)).thenReturn(Future.succeededFuture(created));

      Future<CreditRequest> future = creditService.createCreditRequest(request);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isNotNull();
            verify(creditRequestDAO).create(request);
          });
    }
  }

  // ---------------------------------------------------------------------------
  // 2. getAllCreditRequests
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("getAllCreditRequests")
  class GetAllCreditRequests {

    @Test
    @DisplayName("should return paginated credit requests successfully")
    void success_withPagination(VertxTestContext ctx) {
      PaginatedRequest paginatedRequest =
          new PaginatedRequest(1, 10, Map.of(), List.of(), List.of());

      CreditRequest cr1 = TestDataFactory.aCreditRequest(USER_ID, "pending");
      CreditRequest cr2 = TestDataFactory.aCreditRequest(UUID.randomUUID(), "granted");
      PaginationInfo paginationInfo = PaginationInfo.from(1, 10, 2);
      PaginatedResult<CreditRequest> paginatedResult =
          new PaginatedResult<>(paginationInfo, List.of(cr1, cr2));

      when(creditRequestDAO.getAllWithFilters(paginatedRequest))
          .thenReturn(Future.succeededFuture(paginatedResult));

      Future<PaginatedResult<CreditRequest>> future =
          creditService.getAllCreditRequests(paginatedRequest);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isNotNull();
            assertThat(result.data()).hasSize(2);
            assertThat(result.paginationInfo().getTotalCount()).isEqualTo(2);
          });
    }
  }

  // ---------------------------------------------------------------------------
  // 3. updateCreditRequestStatus
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("updateCreditRequestStatus")
  class UpdateCreditRequestStatus {

    private final String expirationDate = LocalDateTime.now().plusDays(30).toString();

    @Test
    @DisplayName("should grant credit request and create transaction")
    void success_grant(VertxTestContext ctx) {
      CreditRequest pendingRequest = TestDataFactory.aCreditRequest(USER_ID, "pending");
      UUID requestUserId = pendingRequest.userId();
      UserCredit userCredit = TestDataFactory.aUserCredit(requestUserId, 50.0);
      CreditTransaction createdTxn =
          TestDataFactory.aCreditTransaction(requestUserId, 100.0, "credit", TRANSACTED_BY);

      // get the pending request (first call in updateCreditRequestStatus)
      when(creditRequestDAO.get(REQUEST_ID))
          .thenReturn(Future.succeededFuture(pendingRequest));
      // update status to granted
      when(creditRequestDAO.update(anyMap(), anyMap()))
          .thenReturn(Future.succeededFuture(pendingRequest));
      // processCreditGrant fetches user credit
      when(userCreditDAO.get(requestUserId))
          .thenReturn(Future.succeededFuture(userCredit));
      // update balance
      when(userCreditDAO.update(anyMap(), anyMap()))
          .thenReturn(Future.succeededFuture(userCredit));
      // create transaction
      when(creditTransactionDAO.create(any(CreditTransaction.class)))
          .thenReturn(Future.succeededFuture(createdTxn));

      Future<CreditTransaction> future =
          creditService.updateCreditRequestStatus(
              REQUEST_ID, Status.GRANTED, TRANSACTED_BY, 100.0, expirationDate);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isNotNull();
            assertThat(result.userId()).isEqualTo(requestUserId);
            assertThat(result.amount()).isEqualTo(100.0);
            verify(creditTransactionDAO).create(any(CreditTransaction.class));
          });
    }

    @Test
    @DisplayName("should reject credit request without creating a transaction")
    void success_reject(VertxTestContext ctx) {
      CreditRequest pendingRequest = TestDataFactory.aCreditRequest(USER_ID, "pending");

      when(creditRequestDAO.get(REQUEST_ID))
          .thenReturn(Future.succeededFuture(pendingRequest));
      when(creditRequestDAO.update(anyMap(), anyMap()))
          .thenReturn(Future.succeededFuture(pendingRequest));

      Future<CreditTransaction> future =
          creditService.updateCreditRequestStatus(
              REQUEST_ID, Status.REJECTED, TRANSACTED_BY, null, null);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            // When rejected, the result is null (no transaction created)
            assertThat(result).isNull();
            verify(creditTransactionDAO, never()).create(any());
          });
    }

    @Test
    @DisplayName("should fail with DxValidationException when request is already granted")
    void fail_alreadyGranted(VertxTestContext ctx) {
      CreditRequest grantedRequest = TestDataFactory.aCreditRequest(USER_ID, "granted");

      when(creditRequestDAO.get(REQUEST_ID))
          .thenReturn(Future.succeededFuture(grantedRequest));

      Future<CreditTransaction> future =
          creditService.updateCreditRequestStatus(
              REQUEST_ID, Status.GRANTED, TRANSACTED_BY, 100.0, expirationDate);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(DxValidationException.class);
            assertThat(err.getMessage()).contains("already granted/rejected");
          });
    }

    @Test
    @DisplayName("should fail with DxValidationException when request is already rejected")
    void fail_alreadyRejected(VertxTestContext ctx) {
      CreditRequest rejectedRequest = TestDataFactory.aCreditRequest(USER_ID, "rejected");

      when(creditRequestDAO.get(REQUEST_ID))
          .thenReturn(Future.succeededFuture(rejectedRequest));

      Future<CreditTransaction> future =
          creditService.updateCreditRequestStatus(
              REQUEST_ID, Status.GRANTED, TRANSACTED_BY, 100.0, expirationDate);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(DxValidationException.class);
            assertThat(err.getMessage()).contains("already granted/rejected");
          });
    }
  }

  // ---------------------------------------------------------------------------
  // 4. addCredits
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("addCredits")
  class AddCredits {

    @Test
    @DisplayName("should add credits successfully")
    void success(VertxTestContext ctx) {
      CreditTransaction inputTxn =
          TestDataFactory.aCreditTransaction(USER_ID, 50.0, "credit", TRANSACTED_BY);
      UserCredit userCredit = TestDataFactory.aUserCredit(USER_ID, 100.0);
      CreditTransaction createdTxn =
          TestDataFactory.aCreditTransaction(USER_ID, 50.0, "credit", TRANSACTED_BY);

      when(creditTransactionDAO.getAllWithFilters(anyMap()))
          .thenReturn(Future.succeededFuture(Collections.emptyList()));
      when(userCreditDAO.get(USER_ID)).thenReturn(Future.succeededFuture(userCredit));
      when(userCreditDAO.update(anyMap(), anyMap()))
          .thenReturn(Future.succeededFuture(userCredit));
      when(creditTransactionDAO.create(any(CreditTransaction.class)))
          .thenReturn(Future.succeededFuture(createdTxn));

      Future<CreditTransaction> future = creditService.addCredits(inputTxn);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isNotNull();
            assertThat(result.userId()).isEqualTo(USER_ID);
            assertThat(result.amount()).isEqualTo(50.0);
            verify(userCreditDAO).update(anyMap(), anyMap());
            verify(creditTransactionDAO).create(any(CreditTransaction.class));
          });
    }

    @Test
    @DisplayName("should fail with DxConflictException on duplicate transaction")
    void fail_duplicate(VertxTestContext ctx) {
      CreditTransaction inputTxn =
          TestDataFactory.aCreditTransaction(USER_ID, 50.0, "credit", TRANSACTED_BY);
      CreditTransaction existingTxn =
          TestDataFactory.aCreditTransaction(USER_ID, 50.0, "credit", TRANSACTED_BY);

      when(creditTransactionDAO.getAllWithFilters(anyMap()))
          .thenReturn(Future.succeededFuture(List.of(existingTxn)));

      Future<CreditTransaction> future = creditService.addCredits(inputTxn);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(DxConflictException.class);
            assertThat(err.getMessage()).contains("Duplicate transaction");
          });
    }

    @Test
    @DisplayName("should fail with DxBadRequestException when amount is null")
    void fail_nullAmount(VertxTestContext ctx) {
      CreditTransaction inputTxn =
          new CreditTransaction(
              null,
              USER_ID,
              null,
              TRANSACTED_BY,
              "success",
              "credit",
              null,
              LocalDateTime.now(),
              null);

      Future<CreditTransaction> future = creditService.addCredits(inputTxn);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(DxBadRequestException.class);
            assertThat(err.getMessage()).contains("Amount is missing");
          });
    }
  }

  // ---------------------------------------------------------------------------
  // 5. deductCredits
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("deductCredits")
  class DeductCredits {

    @Test
    @DisplayName("should deduct credits successfully")
    void success(VertxTestContext ctx) {
      CreditTransaction inputTxn =
          TestDataFactory.aCreditTransaction(USER_ID, 30.0, "debit", TRANSACTED_BY);
      UserCredit userCredit = TestDataFactory.aUserCredit(USER_ID, 100.0);
      CreditTransaction createdTxn =
          TestDataFactory.aCreditTransaction(USER_ID, 30.0, "debit", TRANSACTED_BY);

      when(creditTransactionDAO.getAllWithFilters(anyMap()))
          .thenReturn(Future.succeededFuture(Collections.emptyList()));
      // getBalance calls userCreditDAO.get
      when(userCreditDAO.get(USER_ID)).thenReturn(Future.succeededFuture(userCredit));
      when(userCreditDAO.update(anyMap(), anyMap()))
          .thenReturn(Future.succeededFuture(userCredit));
      when(creditTransactionDAO.create(any(CreditTransaction.class)))
          .thenReturn(Future.succeededFuture(createdTxn));

      Future<CreditTransaction> future = creditService.deductCredits(inputTxn);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isNotNull();
            assertThat(result.userId()).isEqualTo(USER_ID);
            assertThat(result.amount()).isEqualTo(30.0);
            verify(userCreditDAO).update(anyMap(), anyMap());
            verify(creditTransactionDAO).create(any(CreditTransaction.class));
          });
    }

    @Test
    @DisplayName("should fail with DxValidationException when insufficient balance")
    void fail_insufficientBalance(VertxTestContext ctx) {
      CreditTransaction inputTxn =
          TestDataFactory.aCreditTransaction(USER_ID, 200.0, "debit", TRANSACTED_BY);
      UserCredit userCredit = TestDataFactory.aUserCredit(USER_ID, 50.0);

      when(creditTransactionDAO.getAllWithFilters(anyMap()))
          .thenReturn(Future.succeededFuture(Collections.emptyList()));
      when(userCreditDAO.get(USER_ID)).thenReturn(Future.succeededFuture(userCredit));

      Future<CreditTransaction> future = creditService.deductCredits(inputTxn);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(DxValidationException.class);
            assertThat(err.getMessage()).contains("No sufficient balance");
          });
    }

    @Test
    @DisplayName("should fail with DxConflictException on duplicate deduction")
    void fail_duplicate(VertxTestContext ctx) {
      CreditTransaction inputTxn =
          TestDataFactory.aCreditTransaction(USER_ID, 30.0, "debit", TRANSACTED_BY);
      CreditTransaction existingTxn =
          TestDataFactory.aCreditTransaction(USER_ID, 30.0, "debit", TRANSACTED_BY);

      when(creditTransactionDAO.getAllWithFilters(anyMap()))
          .thenReturn(Future.succeededFuture(List.of(existingTxn)));

      Future<CreditTransaction> future = creditService.deductCredits(inputTxn);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(DxConflictException.class);
            assertThat(err.getMessage()).contains("Duplicate transaction");
          });
    }

    @Test
    @DisplayName("should fail with DxBadRequestException when amount is null")
    void fail_nullAmount(VertxTestContext ctx) {
      CreditTransaction inputTxn =
          new CreditTransaction(
              null,
              USER_ID,
              null,
              TRANSACTED_BY,
              "success",
              "debit",
              null,
              LocalDateTime.now(),
              null);

      Future<CreditTransaction> future = creditService.deductCredits(inputTxn);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(DxBadRequestException.class);
            assertThat(err.getMessage()).contains("Amount is missing");
          });
    }
  }

  // ---------------------------------------------------------------------------
  // 6. createComputeRoleRequest
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("createComputeRoleRequest")
  class CreateComputeRoleRequest {

    @Test
    @DisplayName("should create compute role request successfully")
    void success(VertxTestContext ctx) {
      ComputeRole computeRole = TestDataFactory.aComputeRole(USER_ID);
      ComputeRole created = TestDataFactory.aComputeRole(USER_ID, "pending");

      when(computeRoleDAO.create(computeRole)).thenReturn(Future.succeededFuture(created));

      Future<ComputeRole> future = creditService.createComputeRoleRequest(computeRole);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isNotNull();
            assertThat(result.userId()).isEqualTo(USER_ID);
            assertThat(result.status()).isEqualTo("pending");
            verify(computeRoleDAO).create(computeRole);
          });
    }
  }

  // ---------------------------------------------------------------------------
  // 7. updateComputeRoleStatus
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("updateComputeRoleStatus")
  class UpdateComputeRoleStatus {

    @Test
    @DisplayName("should grant compute role, create user credit, and add Keycloak role")
    void success_grant(VertxTestContext ctx) {
      UUID approvedBy = UUID.randomUUID();
      ComputeRole computeRole = TestDataFactory.aComputeRole(USER_ID, "pending");

      when(computeRoleDAO.update(anyMap(), anyMap()))
          .thenReturn(Future.succeededFuture(computeRole));
      when(computeRoleDAO.get(REQUEST_ID)).thenReturn(Future.succeededFuture(computeRole));
      when(userCreditDAO.create(any(UserCredit.class)))
          .thenReturn(Future.succeededFuture(null));
      when(keycloakUserService.addRoleToUser(eq(USER_ID), eq(DxRole.COMPUTE)))
          .thenReturn(Future.succeededFuture(true));

      Future<Boolean> future =
          creditService.updateComputeRoleStatus(REQUEST_ID, Status.GRANTED, approvedBy);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isTrue();
            verify(userCreditDAO).create(any(UserCredit.class));
            verify(keycloakUserService).addRoleToUser(eq(USER_ID), eq(DxRole.COMPUTE));
          });
    }

    @Test
    @DisplayName("should reject compute role and remove Keycloak role")
    void success_reject(VertxTestContext ctx) {
      UUID approvedBy = UUID.randomUUID();
      ComputeRole computeRole = TestDataFactory.aComputeRole(USER_ID, "pending");

      when(computeRoleDAO.update(anyMap(), anyMap()))
          .thenReturn(Future.succeededFuture(computeRole));
      when(computeRoleDAO.get(REQUEST_ID)).thenReturn(Future.succeededFuture(computeRole));
      when(keycloakUserService.removeRoleFromUser(eq(USER_ID), eq(DxRole.COMPUTE)))
          .thenReturn(Future.succeededFuture(true));

      Future<Boolean> future =
          creditService.updateComputeRoleStatus(REQUEST_ID, Status.REJECTED, approvedBy);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isTrue();
            verify(keycloakUserService).removeRoleFromUser(eq(USER_ID), eq(DxRole.COMPUTE));
            verify(userCreditDAO, never()).create(any());
          });
    }

    @Test
    @DisplayName("should fail with DxNotFoundException when request ID does not exist")
    void fail_notFound(VertxTestContext ctx) {
      UUID approvedBy = UUID.randomUUID();

      when(computeRoleDAO.update(anyMap(), anyMap()))
          .thenReturn(Future.failedFuture(new NoRowFoundException("No row found")));

      Future<Boolean> future =
          creditService.updateComputeRoleStatus(REQUEST_ID, Status.GRANTED, approvedBy);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(DxNotFoundException.class);
            assertThat(err.getMessage()).contains("No matching requestId");
          });
    }
  }

  // ---------------------------------------------------------------------------
  // 8. hasUserComputeAccess
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("hasUserComputeAccess")
  class HasUserComputeAccess {

    @Test
    @DisplayName("should return true when user has compute access")
    void success_hasAccess(VertxTestContext ctx) {
      when(computeRoleDAO.hasUserComputeAccess(USER_ID))
          .thenReturn(Future.succeededFuture(true));

      Future<Boolean> future = creditService.hasUserComputeAccess(USER_ID);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isTrue();
          });
    }

    @Test
    @DisplayName("should return false when user does not have compute access")
    void success_noAccess(VertxTestContext ctx) {
      when(computeRoleDAO.hasUserComputeAccess(USER_ID))
          .thenReturn(Future.succeededFuture(false));

      Future<Boolean> future = creditService.hasUserComputeAccess(USER_ID);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isFalse();
          });
    }
  }

  // ---------------------------------------------------------------------------
  // 9. getBalance
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("getBalance")
  class GetBalance {

    @Test
    @DisplayName("should return balance when user has valid credit")
    void success_validCredit(VertxTestContext ctx) {
      UserCredit userCredit = TestDataFactory.aUserCredit(USER_ID, 150.0);

      when(userCreditDAO.get(USER_ID)).thenReturn(Future.succeededFuture(userCredit));

      Future<JsonObject> future = creditService.getBalance(USER_ID);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isNotNull();
            assertThat(result.getDouble("balance")).isEqualTo(150.0);
            assertThat(result.getBoolean("isValid")).isTrue();
          });
    }

    @Test
    @DisplayName("should return zero balance and isValid false when no credit record exists")
    void fail_noRecord(VertxTestContext ctx) {
      when(userCreditDAO.get(USER_ID))
          .thenReturn(Future.failedFuture(new NoRowFoundException("No user credit found")));

      Future<JsonObject> future = creditService.getBalance(USER_ID);

      // The service recovers and returns a JSON with balance 0.0 and isValid false
      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isNotNull();
            assertThat(result.getDouble("balance")).isEqualTo(0.0);
            assertThat(result.getBoolean("isValid")).isFalse();
          });
    }
  }

  // ---------------------------------------------------------------------------
  // 10. getCreditRequestById
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("getCreditRequestById")
  class GetCreditRequestById {

    @Test
    @DisplayName("should return credit request when found")
    void success(VertxTestContext ctx) {
      CreditRequest creditRequest = TestDataFactory.aCreditRequest(USER_ID, "pending");

      when(creditRequestDAO.get(REQUEST_ID))
          .thenReturn(Future.succeededFuture(creditRequest));

      Future<CreditRequest> future = creditService.getCreditRequestById(REQUEST_ID);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isNotNull();
            assertThat(result.userId()).isEqualTo(USER_ID);
            assertThat(result.status()).isEqualTo("pending");
          });
    }

    @Test
    @DisplayName("should fail with DxNotFoundException when request not found")
    void fail_notFound(VertxTestContext ctx) {
      when(creditRequestDAO.get(REQUEST_ID))
          .thenReturn(Future.failedFuture(new NoRowFoundException("No row found")));

      Future<CreditRequest> future = creditService.getCreditRequestById(REQUEST_ID);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(DxNotFoundException.class);
            assertThat(err.getMessage()).contains("No matching requestId");
          });
    }
  }

  // ---------------------------------------------------------------------------
  // 11. deletePendingCreditRequestById
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("deletePendingCreditRequestById")
  class DeletePendingCreditRequestById {

    @Test
    @DisplayName("should delete pending credit request successfully")
    void success(VertxTestContext ctx) {
      when(creditRequestDAO.delete(REQUEST_ID))
          .thenReturn(Future.succeededFuture(true));

      Future<Boolean> future = creditService.deletePendingCreditRequestById(REQUEST_ID);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isTrue();
            verify(creditRequestDAO).delete(REQUEST_ID);
          });
    }

    @Test
    @DisplayName("should fail with DxNotFoundException when request not found for deletion")
    void fail_notFound(VertxTestContext ctx) {
      when(creditRequestDAO.delete(REQUEST_ID))
          .thenReturn(Future.succeededFuture(false));

      Future<Boolean> future = creditService.deletePendingCreditRequestById(REQUEST_ID);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(DxNotFoundException.class);
            assertThat(err.getMessage()).contains("Failed to delete");
          });
    }
  }

  // ---------------------------------------------------------------------------
  // 12. getCreditRequestsByUserId
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("getCreditRequestsByUserId")
  class GetCreditRequestsByUserId {

    @Test
    @DisplayName("should return credit requests for the given user")
    void success(VertxTestContext ctx) {
      CreditRequest cr1 = TestDataFactory.aCreditRequest(USER_ID, "pending");
      CreditRequest cr2 = TestDataFactory.aCreditRequest(USER_ID, "granted");

      when(creditRequestDAO.getAllWithFilters(anyMap()))
          .thenReturn(Future.succeededFuture(List.of(cr1, cr2)));

      Future<List<CreditRequest>> future = creditService.getCreditRequestsByUserId(USER_ID);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).hasSize(2);
            assertThat(result).allMatch(cr -> cr.userId().equals(USER_ID));
          });
    }

    @Test
    @DisplayName("should fail with DxNotFoundException when no requests found for user")
    void fail_empty(VertxTestContext ctx) {
      when(creditRequestDAO.getAllWithFilters(anyMap()))
          .thenReturn(Future.succeededFuture(Collections.emptyList()));

      Future<List<CreditRequest>> future = creditService.getCreditRequestsByUserId(USER_ID);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(DxNotFoundException.class);
            assertThat(err.getMessage()).contains("No pending credit request found");
          });
    }
  }

  // ---------------------------------------------------------------------------
  // 13. hasPendingComputeRequest
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("hasPendingComputeRequest")
  class HasPendingComputeRequest {

    @Test
    @DisplayName("should return true when user has a pending compute request")
    void success_hasPending(VertxTestContext ctx) {
      ComputeRole pendingRole = TestDataFactory.aComputeRole(USER_ID, "pending");

      when(computeRoleDAO.getAllWithFilters(anyMap()))
          .thenReturn(Future.succeededFuture(List.of(pendingRole)));

      Future<Boolean> future = creditService.hasPendingComputeRequest(USER_ID);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isTrue();
          });
    }

    @Test
    @DisplayName("should return false when user has no pending compute request")
    void success_noPending(VertxTestContext ctx) {
      when(computeRoleDAO.getAllWithFilters(anyMap()))
          .thenReturn(Future.succeededFuture(Collections.emptyList()));

      Future<Boolean> future = creditService.hasPendingComputeRequest(USER_ID);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isFalse();
          });
    }
  }
}
