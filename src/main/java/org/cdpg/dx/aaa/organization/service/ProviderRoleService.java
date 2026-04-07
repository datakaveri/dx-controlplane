package org.cdpg.dx.aaa.organization.service;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.organization.models.ProviderRoleRequest;
import org.cdpg.dx.aaa.organization.models.Status;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.database.postgres.models.PaginatedResult;

import java.util.List;
import java.util.UUID;

public interface ProviderRoleService {

  Future<ProviderRoleRequest> createProviderRequest(ProviderRoleRequest providerRoleRequest);

  Future<Boolean> updateProviderRequestStatus(UUID requestId, Status status);

  Future<List<ProviderRoleRequest>> getAllPendingProviderRoleRequests(UUID orgId);

  Future<PaginatedResult<ProviderRoleRequest>> getAllPendingProviderRoleRequests(PaginatedRequest paginatedRequest);

  Future<Boolean> hasPendingProviderRole(UUID userId, UUID orgId);

  Future<Boolean> createProviderRole(ProviderRoleRequest providerRoleRequest);

  Future<ProviderRoleRequest> getProviderRequestById(UUID requestId);

  Future<ProviderRoleRequest> getProviderRoleRequestByUserId(UUID userId);

  Future<Boolean> deleteProviderRoleRequest(UUID orgId, UUID userId);

  Future<Boolean> deleteProviderRoleRequestById(UUID id);
}
