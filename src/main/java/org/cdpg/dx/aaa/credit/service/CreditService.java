package org.cdpg.dx.aaa.credit.service;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.credit.models.*;
import org.cdpg.dx.aaa.organization.models.Organization;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.database.postgres.models.PaginatedResult;

import java.util.List;
import java.util.UUID;

public interface CreditService {

  // ************ CREDIT REQUEST *********
  Future<CreditRequest> createCreditRequest(CreditRequest creditRequest); // done -> creates a new entry in credit request table

  Future<PaginatedResult<CreditRequest>> getAllCreditRequests(PaginatedRequest paginatedRequest);

  Future<CreditTransaction> updateCreditRequestStatus(UUID requestId, Status status,UUID transactedBy,Double amount); //done -> on approval will create a new entry in userCredit table

  // ************ USER CREDIT *********

  Future<CreditTransaction> deductCredits(CreditTransaction creditTransaction);

  Future<CreditTransaction> addCredits(CreditTransaction creditTransaction);

  Future<Double> getBalance(UUID userId);

  // ************ COMPUTE ROLE **********

  Future<ComputeRole> createComputeRoleRequest(ComputeRole computeRole);

  Future<List<ComputeRole>> getAllComputeRequests();

  Future<PaginatedResult<ComputeRole>> getAllComputeRequests(PaginatedRequest paginatedRequest);

  Future<Boolean> hasPendingComputeRequest(UUID userId);

  Future<ComputeRole> getComputeRoleRequestByUserId(UUID userId);

  Future<Boolean> updateComputeRoleStatus(UUID requestId, Status status,UUID approvedBy);

  Future<Boolean> hasUserComputeAccess(UUID userId);

  Future<ComputeRole> getComputeRequestById(UUID requestId);

  Future<CreditRequest> getCreditRequestById(UUID requestId);

  Future<Boolean> deleteComputeRoleRequest(UUID userId);

  Future<Boolean> deleteCreditRequest(UUID userId);
}
