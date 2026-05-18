package org.cdpg.dx.aaa.credit.service;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.credit.dao.*;
import org.cdpg.dx.aaa.credit.models.*;
import org.cdpg.dx.aaa.organization.config.Constants;
import org.cdpg.dx.auth.model.DxRole;
import org.cdpg.dx.common.exception.*;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.util.ServiceErrorHelper;
import org.cdpg.dx.database.postgres.models.PaginatedResult;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.cdpg.dx.aaa.credit.models.Status.*;
import static org.cdpg.dx.aaa.credit.util.Constants.*;

public class CreditServiceImpl implements CreditService {

  private static final Logger LOGGER = LoggerFactory.getLogger(CreditServiceImpl.class);

  private final CreditRequestDAO creditRequestDAO;
  private final UserCreditDAO userCreditDAO;
  private final CreditTransactionDAO creditTransactionDAO;
  private final ComputeRoleDAO computeRoleDAO;
  private final KeycloakUserService keycloakUserService;
  private final JsonObject config;

  public CreditServiceImpl(CreditDAOFactory factory, KeycloakUserService keycloakUserService, JsonObject config) {
    this.creditRequestDAO = factory.creditRequestDAO();
    this.userCreditDAO = factory.userCreditDAO();
    this.creditTransactionDAO = factory.creditTransactionDAO();
    this.computeRoleDAO = factory.computeRoleDAO();
    this.keycloakUserService = keycloakUserService;
    this.config = config;
  }

  @Override
  public Future<CreditRequest> createCreditRequest(CreditRequest creditRequest) {
    Map<String,Object> filter = Map.of(USER_ID,creditRequest.userId().toString(),
                                        STATUS, PENDING.getStatus());

    return creditRequestDAO.getAllWithFilters(filter).compose(existingRequests -> {

      Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
      LOGGER.info("Now (UTC): {}", LocalDateTime.now(ZoneOffset.UTC));
      LOGGER.info("Now (System): {}", LocalDateTime.now());

      boolean hasRecentPendingRequest = existingRequests.stream()
        .anyMatch(req ->
          req.requestedAt()
            .toInstant(ZoneOffset.UTC)
            .isAfter(oneHourAgo)
        );

      LOGGER.info("hasRecentPendingRequest: {}",hasRecentPendingRequest);
      if (hasRecentPendingRequest) {
        return Future.failedFuture(
          new DxForbiddenException("A credit request is already pending. Please try again later.")
        );
      }

      // No recent pending request → allow creation
      return creditRequestDAO.create(creditRequest);
    });
  }


  @Override
  public Future<PaginatedResult<CreditRequest>> getAllCreditRequests(PaginatedRequest request) {
    return creditRequestDAO.getAllWithFilters(request);

  }

  @Override
  public Future<CreditTransaction> updateCreditRequestStatus(UUID requestId, Status status, UUID transactedBy,Double amount,String expirationDate) {
    return creditRequestDAO.get(requestId).compose(cr -> {

      if (cr.status().equalsIgnoreCase(GRANTED.getStatus()) || cr.status().equalsIgnoreCase(REJECTED.getStatus())) {
        LOGGER.warn("Credit request with ID {} is already {}, cannot update status", requestId, cr.status());
        return Future.failedFuture(new DxValidationException("Credit request is already granted/rejected"));
      }

      return creditRequestDAO.update(
        Map.of(CREDIT_REQUEST_ID, requestId.toString()),
        Map.of(STATUS, status.getStatus())
      ).compose(updated -> {
        if (status != GRANTED) {
          LOGGER.info("Credit request with ID {} is not granted, no further processing needed", requestId);
          return Future.succeededFuture(null); // No transaction needed
        }
        return processCreditGrant(requestId, transactedBy, amount, expirationDate);
      }).recover(ServiceErrorHelper.mapNotFound("No request found with given ID"));

    });
  }

  private Future<CreditTransaction> processCreditGrant(UUID requestId, UUID transactedBy, Double amount,String expirationDate) {
    return creditRequestDAO.get(requestId).compose(cr -> {
      UUID userId = cr.userId();
      LocalDateTime requestedAt = cr.requestedAt();

      return userCreditDAO.get(userId)
        .recover(err -> {
          LOGGER.error("Failed to get user credit for userId {}: {}", userId, err.getMessage());
          return Future.failedFuture(new DxValidationException("User needs to have compute access"));
        }).compose(userCredit -> {
        return isValidCredit(userCredit).compose(isValidJson -> {

            LOGGER.info("Updated Expiry Time: {}", expirationDate);

            Double currentBalance = userCredit.balance()>0 ? userCredit.balance() : 0.0;
          Double newBalance = isValidJson ? currentBalance + amount : amount;

          LOGGER.info("Current balance: {}, New balance: {}", currentBalance, newBalance);

          Map<String, Object> updateMap = Map.of(
            BALANCE, newBalance,
            EXPIRATION_DATE,expirationDate
          );
          Map<String, Object> conditionMap = Map.of(USER_ID, userId.toString());

          return userCreditDAO.update(conditionMap, updateMap).compose(updated -> {
            CreditTransaction transaction = new CreditTransaction(
              null,
              userId,
              amount,
              transactedBy,
              TransactionStatus.SUCCESS.getStatus(),
              TransactionType.CREDIT.getType(),
              null,
              requestedAt,
              newBalance
            );

            return creditTransactionDAO.create(transaction);
          });
        });
      });
    });
  }



  @Override
  public Future<CreditTransaction> addCredits(CreditTransaction creditTransaction)
  {
    LocalDateTime reqAt = creditTransaction.requestedAt();
    LOGGER.debug("Processing credit transaction at: {}", reqAt);
    UUID userId = creditTransaction.userId();

    if (creditTransaction.amount() == null) {
      return Future.failedFuture(new DxBadRequestException("Amount is missing in CreditTransaction"));
    }

    Double amount = creditTransaction.amount();
    Map<String, Object> filter = Map.of(REQUESTED_AT, reqAt.toString(), USER_ID, userId.toString());

    return creditTransactionDAO.getAllWithFilters(filter).compose(existing -> {
      if (existing != null && !existing.isEmpty()) {
        return Future.failedFuture(new DxConflictException("Duplicate transaction request"));
      }

      return userCreditDAO.get(userId).compose(userCredit -> {
        double updatedBalance = userCredit.balance() + amount;

        Map<String, Object> conditionMap = Map.of(USER_ID, userId.toString());
        Map<String, Object> updatedMap = Map.of(BALANCE, updatedBalance);

        return userCreditDAO.update(conditionMap, updatedMap).compose(v -> {
          CreditTransaction transaction = new CreditTransaction(
            null,
            userId,
            amount,
            creditTransaction.transactedBy(),
            TransactionStatus.SUCCESS.getStatus(),
            TransactionType.CREDIT.getType(),
            null,
            reqAt,
            updatedBalance
          );

          return creditTransactionDAO.create(transaction);

        });
      });
    }).recover(ServiceErrorHelper.mapNotFound("User entry not found"));
  }

  @Override
  public Future<CreditTransaction> deductCredits(CreditTransaction creditTransaction) {
    LocalDateTime reqAt = creditTransaction.requestedAt();
    LOGGER.debug("Processing credit transaction at: {}", reqAt);
    UUID userId = creditTransaction.userId();

    if (creditTransaction.amount() == null) {
      return Future.failedFuture(new DxBadRequestException("Amount is missing in CreditTransaction"));
    }

    Double amount = creditTransaction.amount();
    Map<String, Object> filter = Map.of(REQUESTED_AT, reqAt.toString(),USER_ID, userId.toString());

    return creditTransactionDAO.getAllWithFilters(filter).compose(existing -> {
      if (existing != null && !existing.isEmpty()) {
        return Future.failedFuture(new DxConflictException("Duplicate transaction request"));
      }

      return getBalance(userId).compose(res -> {
        Double balance = res.getDouble("balance");
        if (balance < amount) {
          return Future.failedFuture(new DxValidationException("No sufficient balance"));
        }

        double updatedBalance = balance - amount;

        Map<String, Object> conditionMap = Map.of(USER_ID, userId.toString());
        Map<String, Object> updatedMap = Map.of(BALANCE, updatedBalance);

        return userCreditDAO.update(conditionMap, updatedMap).compose(v -> {
          CreditTransaction transaction = new CreditTransaction(
            null,
            userId,
            amount,
            creditTransaction.transactedBy(),
            TransactionStatus.SUCCESS.getStatus(),
            TransactionType.DEBIT.getType(),
            null,
            reqAt,
            updatedBalance
          );
          return creditTransactionDAO.create(transaction);
        });
      });
    }).recover(ServiceErrorHelper.mapNotFound("User or balance entry not found"));
  }

  @Override
  public Future<ComputeRole> createComputeRoleRequest(ComputeRole computeRole) {
    return computeRoleDAO.create(computeRole);
  }

  @Override
  public Future<List<ComputeRole>> getAllComputeRequests() {
    return computeRoleDAO.getAll();
  }

  @Override
  public Future<ComputeRole> getComputeRoleRequestByUserId(UUID userId){
    Map<String, Object> filter = Map.of(Constants.USER_ID, userId.toString());
    return computeRoleDAO.getAllWithFilters(filter)
      .compose(requests -> {
        if (requests.isEmpty()) {
          return Future.failedFuture(new DxNotFoundException("No compute request found for userId: " + userId));
        }
        return Future.succeededFuture(requests.get(0)); // Return the first request
      });
  }

  @Override
  public Future<PaginatedResult<ComputeRole>> getAllComputeRequests(PaginatedRequest paginatedRequest) {
    return computeRoleDAO.getAll(paginatedRequest);
  }

  @Override
  public Future<Boolean> updateComputeRoleStatus(UUID requestId, Status status, UUID approvedBy) {
    Map<String, Object> conditionMap = Map.of(COMPUTE_ROLE_ID, requestId.toString());
    Map<String, Object> updateMap = Map.of(
      Constants.STATUS, status.getStatus(),
      APPROVED_BY, approvedBy.toString()
    );

    return computeRoleDAO.update(conditionMap, updateMap).compose(updated -> {
      return computeRoleDAO.get(requestId).compose(req -> {
        UUID userId = req.userId();

        if (GRANTED.equals(status)) {
          return userCreditDAO.create(new UserCredit(null, userId, config.getInteger("initialCreditBalance"),LocalDateTime.now().plusDays(30), LocalDateTime.now()))
            .recover(dxEx -> {
              if (dxEx instanceof UniqueConstraintViolationException) {
                LOGGER.info("UserCredit already exists, continuing role assignment.");
                return Future.succeededFuture();
              }
              return Future.failedFuture(dxEx);
            })
            .compose(v -> keycloakUserService.addRoleToUser(userId, DxRole.COMPUTE))
            .compose(success -> {
              if (!success) {
                return Future.failedFuture("Failed to assign Compute role in Keycloak");
              }
              return Future.succeededFuture(true);
            });

        } else if (REJECTED.equals(status)) {
          return keycloakUserService.removeRoleFromUser(userId, DxRole.COMPUTE)
            .compose(success -> {
              if (!success) {
                return Future.failedFuture("Failed to remove Compute role in Keycloak");
              }
              return Future.succeededFuture(true);
            });
        }

        return Future.succeededFuture(true);
      });
    }).recover(ServiceErrorHelper.mapNotFound("No matching requestId found in computeRole table"));
  }

  @Override
  public Future<Boolean> hasUserComputeAccess(UUID userId) {
    return computeRoleDAO.hasUserComputeAccess(userId);
  }

  //expiresAt , Balance ,
    @Override
    public Future<JsonObject> getBalance(UUID userId) {
    LOGGER.info("Fetching balance for userId: {}", userId);

    return userCreditDAO.get(userId)
      .compose(result -> {
         return isValidCredit(result).compose(isValid -> {
          if (!isValid) {
            LOGGER.warn("Invalid credit for userId: {}", userId);
            return Future.failedFuture(
              new DxValidationException("User does not have valid credits or has insufficient balance")
            );
          }

          Double balance = result.toJson().getDouble("balance");
          LocalDateTime expiry = result.expirationDate();

          LOGGER.info("Balance for userId {}: {}, Expiry: {}", userId, balance, expiry);

          return Future.succeededFuture(
            new JsonObject()
              .put(EXPIRATION_DATE, expiry)
              .put(BALANCE, balance != null ? balance : 0.0)
              .put("isValid", true)
          );
        });
      })
      .recover(err -> {
        if (err.getMessage() != null) {
          LOGGER.warn("Credit Invalid for user {}", userId);
          return Future.succeededFuture(
            new JsonObject()
              .put(BALANCE, 0.0)
              .put("isValid", false)
              .put(EXPIRATION_DATE, (String) null)
          );
        }

        LOGGER.error("Failed to fetch balance for userId: {}", userId, err);
        return Future.failedFuture(err);
      });
  }

  @Override
  public Future<ComputeRole> getComputeRequestById(UUID requestId)
  {
    return computeRoleDAO.get(requestId)
      .recover(ServiceErrorHelper.mapNotFound("No matching requestId found in computeRole table"));
  }

  @Override
  public Future<CreditRequest> getCreditRequestById(UUID requestId)
  {
    return creditRequestDAO.get(requestId)
      .recover(ServiceErrorHelper.mapNotFound("No matching requestId found in creditRequest table"));
  }


  @Override
  public Future<Boolean> hasPendingComputeRequest(UUID userId) {
    Map<String, Object> filter = Map.of(
      Constants.STATUS, PENDING.getStatus(),
      Constants.USER_ID, userId.toString()
    );

    return computeRoleDAO.getAllWithFilters(filter)
      .map(list -> !list.isEmpty());
  }

  public Future<Boolean> deleteCreditRequest(UUID userId)
  {
    Map<String,Object> filter = Map.of(
      Constants.USER_ID,userId.toString()
    );

    creditRequestDAO.getAllWithFilters(filter).compose(ar->{
      if (ar.isEmpty()) {
        return Future.failedFuture(new DxNotFoundException("No credit request found for userId: " + userId));
      }
      List<Future> futures = new ArrayList<>();
      for (CreditRequest cr : ar) {
        futures.add(creditRequestDAO.delete(cr.id()).map(deleted -> {;
          if (!deleted) {
            throw new DxNotFoundException("Failed to delete credit request with ID: " + cr.id());
          }
          return true;
        }));
      }
      return CompositeFuture.all(futures);
    }).recover(ServiceErrorHelper.mapNotFound("No matching requestId found in credit Request table"));
    return Future.succeededFuture(true);
  }


  public Future<Boolean> deleteComputeRoleRequest(UUID userId)
  {
    Map<String,Object> filter = Map.of(
      Constants.USER_ID,userId.toString()
    );

    computeRoleDAO.getAllWithFilters(filter).compose(ar->{
      if (ar.isEmpty()) {
        return Future.failedFuture(new DxNotFoundException("No compute request found for userId: " + userId));
      }
      List<Future> futures = new ArrayList<>();
      for (ComputeRole cr : ar) {
        futures.add(computeRoleDAO.delete(cr.id()).map(deleted -> {;
          if (!deleted) {
            throw new DxNotFoundException("Failed to delete compute request with ID: " + cr.id());
          }
          return true;
        }));
      }
      return CompositeFuture.all(futures);
    }).recover(ServiceErrorHelper.mapNotFound("No matching requestId found in compute request table"));
    return Future.succeededFuture(true);
  }


  private Future<Boolean> isValidCredit(UserCredit result) {
    if (result == null) {
      return Future.succeededFuture(false);
    }

    Double balance = result.toJson().getDouble("balance");
    LocalDateTime expiry = result.expirationDate();

    Boolean isValid = false;

    if(balance==null) {
      LOGGER.info("Balance is null for userId: {}", result.userId());
      throw new DxValidationException("balance is null");
    }
    else if(balance==0.0)
    {
      LOGGER.info("Balance is 0.0 for userId: {}", result.userId());
    }
    else if(expiry==null || expiry.isBefore(LocalDateTime.now())) {
      LOGGER.info("Expiry is null or in the past for userId: {}", result.userId());
    }
    else {
      isValid = true;
    }

    return Future.succeededFuture(isValid);
  }

  @Override
  public Future<UserCredit> getExpirationDateByUserId(UUID userId) {
    Map<String, Object> filter = Map.of(
      Constants.USER_ID, userId.toString()
    );

    return userCreditDAO.getAllWithFilters(filter)
      .compose(userCredits -> {
        if (userCredits.isEmpty()) {
          return Future.failedFuture(new DxNotFoundException("No user credit found for userId: " + userId));
        }
        return Future.succeededFuture(userCredits.get(0)); // Return the first matching UserCredit
      })
      .recover(ServiceErrorHelper.mapNotFound("No matching userId found in userCredit table"));
  }

  @Override
  public Future<List<CreditRequest>> getCreditRequestsByUserId(UUID userId) {
    Map<String, Object> filter = Map.of(
      Constants.USER_ID, userId.toString()
    );

    return creditRequestDAO.getAllWithFilters(filter)
      .compose(requests -> {
        if (requests.isEmpty()) {
          return Future.failedFuture(new DxNotFoundException(
            "No pending credit request found for userId: " + userId));
        }
        return Future.succeededFuture(requests);
      });
  }

  @Override
  public Future<ComputeRole> getComputeRequestByUserId(UUID userId) {
    Map<String, Object> filter = Map.of(
      Constants.USER_ID, userId.toString()
    );

    return computeRoleDAO.getAllWithFilters(filter)
      .compose(requests -> {
        if (requests.isEmpty()) {
          return Future.failedFuture(new DxNotFoundException(
            "No pending compute request found for userId: " + userId));
        }
        return Future.succeededFuture(requests.get(0));  // since compute request can be made only once
      });
  }

  @Override
  public Future<Boolean> deletePendingCreditRequestById(UUID requestId) {
        return creditRequestDAO.delete(requestId)
          .compose(deleted -> {
            if (!deleted) {
              return Future.failedFuture(new DxNotFoundException(
                "Failed to delete pending credit request with ID: " + requestId));
            }
            return Future.succeededFuture(true);
          });

  }

  @Override
  public Future<Boolean> deletePendingComputeRequestById(UUID requestId) {
        return computeRoleDAO.delete(requestId)
          .compose(deleted -> {
            if (!deleted) {
              return Future.failedFuture(
                new DxNotFoundException("Failed to delete pending compute request with ID: " + requestId)
              );
            }
            return Future.succeededFuture(true);
          });
  }




}

