package org.cdpg.dx.aaa.organization.service.impl;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.organization.dao.OrganizationUserDAO;
import org.cdpg.dx.aaa.organization.dao.ProviderRoleRequestDAO;
import org.cdpg.dx.aaa.organization.config.Constants;
import org.cdpg.dx.aaa.organization.models.ProviderRoleRequest;
import org.cdpg.dx.aaa.organization.models.Status;
import org.cdpg.dx.aaa.organization.service.ProviderRoleService;
import org.cdpg.dx.auth.authorization.model.DxRole;
import org.cdpg.dx.common.exception.DxConflictException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.exception.DxPgException;
import org.cdpg.dx.common.exception.DxRuntimeException;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.util.ServiceErrorHelper;
import org.cdpg.dx.database.postgres.models.PaginatedResult;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.cdpg.dx.aaa.organization.config.Constants.*;
import static org.cdpg.dx.common.util.DateTimeHelper.FORMATTER;

public class ProviderRoleServiceImpl implements ProviderRoleService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProviderRoleServiceImpl.class);

  private final ProviderRoleRequestDAO providerRequestDAO;
  private final OrganizationUserDAO orgUserDAO;
  private final KeycloakUserService keycloakUserService;

  public ProviderRoleServiceImpl(
      ProviderRoleRequestDAO providerRequestDAO,
      OrganizationUserDAO orgUserDAO,
      KeycloakUserService keycloakUserService) {
    this.providerRequestDAO = providerRequestDAO;
    this.orgUserDAO = orgUserDAO;
    this.keycloakUserService = keycloakUserService;
  }

  @Override
  public Future<ProviderRoleRequest> createProviderRequest(ProviderRoleRequest providerRoleRequest) {
    Map<String, Object> filterMap = Map.of(USER_ID, providerRoleRequest.userId().toString());

    // check if there is a pending or granted request for the same user
    // if yes then dont create a new request
    // if status is rejected then only allow to create a new request

    return providerRequestDAO.getAllWithFilters(filterMap)
      .compose(requests -> {
        if (!requests.isEmpty()) {
          // If there is a pending or granted request, do not create a new one
          ProviderRoleRequest existingRequest = requests.get(0);
          if (Status.PENDING.getStatus().equals(existingRequest.status()) ||
            Status.GRANTED.getStatus().equals(existingRequest.status())) {
            return Future.failedFuture(new DxConflictException("A pending or granted provider role request already exists for this user"));
          }
          else if( Status.REJECTED.getStatus().equals(existingRequest.status())) {
            // If the existing request is rejected, allow to create a new one
            LOGGER.info("Existing request is rejected, allowing to create a new provider role request");
            return providerRequestDAO.create(providerRoleRequest);
          } else {
            return Future.failedFuture(new DxConflictException("Provider role request is not in a state that allows creation of a new request"));
          }
        }
        else {
          // No existing requests found, proceed to create a new one
          return providerRequestDAO.create(providerRoleRequest);
        }
      });
  }

  @Override
  public Future<Boolean> updateProviderRequestStatus(UUID requestId, Status status) {
    Map<String, Object> conditionMap = Map.of(
      Constants.ORG_CREATE_ID, requestId.toString()
    );
    Map<String, Object> updateDataMap = Map.of(
      Constants.STATUS, status.getStatus(),
      Constants.UPDATED_AT, FORMATTER.format(LocalDateTime.now())
    );

    return providerRequestDAO.update(conditionMap, updateDataMap)
      .compose(updated -> {
        if (Status.GRANTED.getStatus().equals(status.getStatus())) {
          return providerRequestDAO.get(requestId)
            .compose(providerRequest -> {
              // Update role in Keycloak
              return keycloakUserService.addRoleToUser(
                  providerRequest.userId(),
                  DxRole.PROVIDER
                )
                .compose(success -> {
                  if (!success) {
                    return Future.failedFuture("Failed to assign PROVIDER role in Keycloak");
                  }
                  return Future.succeededFuture(true);
                });
            });
        }

        return Future.succeededFuture(true);
      })
      .recover(ServiceErrorHelper.mapNotFound("No request found with given ID"));
  }

  @Override
  public Future<List<ProviderRoleRequest>> getAllPendingProviderRoleRequests(UUID orgId) {
    Map<String, Object> filterMap = Map.of(
      Constants.ORGANIZATION_ID, orgId.toString()
    );
    return providerRequestDAO.getAllWithFilters(filterMap);
  }

  @Override
  public Future<PaginatedResult<ProviderRoleRequest>> getAllPendingProviderRoleRequests(PaginatedRequest paginatedRequest) {
    return providerRequestDAO.getAllWithFilters(paginatedRequest);
  }

  @Override
  public Future<Boolean> hasPendingProviderRole(UUID userId, UUID orgId) {
    Map<String, Object> filterMap = Map.of(
      Constants.STATUS, Status.PENDING.getStatus(), USER_ID, userId.toString(), Constants.ORGANIZATION_ID, orgId.toString()
    );

    return providerRequestDAO.getAllWithFilters(filterMap)
      .map(list -> !list.isEmpty());
  }

  @Override
  public Future<Boolean> createProviderRole(ProviderRoleRequest providerRoleRequest) {

    // Checks:
    // 1. User id is present in organisation of org admin
    // 2. If user Id is present in provider request and role status is pending, then change the status to granted

    UUID orgId = providerRoleRequest.orgId();
    UUID userId = providerRoleRequest.userId();
    String status = providerRoleRequest.status();

    Map<String, Object> filterMap = Map.of(
      Constants.ORGANIZATION_ID, orgId.toString(),
      USER_ID, userId.toString()
    );

    return orgUserDAO.getAllWithFilters(filterMap).compose(ar -> {
      if (ar.isEmpty()) {
        return Future.failedFuture(new DxNotFoundException("User not found in organization"));
      } else {
        Map<String, Object> filterMapProviderRole = Map.of(
          Constants.ORGANIZATION_ID, orgId.toString(),
          USER_ID, userId.toString()
        );

        return providerRequestDAO.getAllWithFilters(filterMapProviderRole)
          .compose(providerRoleRequests -> {
            if (providerRoleRequests.isEmpty()) {
              return providerRequestDAO.create(providerRoleRequest)
                .compose(createdRequest -> keycloakUserService.addRoleToUser(userId, DxRole.PROVIDER)
                  .compose(success -> {
                    if (!success) {
                      return Future.failedFuture("Failed to assign PROVIDER role in Keycloak");
                    }
                    return Future.succeededFuture(true);
                  }))
                .recover(ServiceErrorHelper.mapNotFound("No User found in Keycloak"));
            } else {
              ProviderRoleRequest existingRequest = providerRoleRequests.get(0);
              if (!Status.PENDING.getStatus().equals(existingRequest.status())) {
                return Future.failedFuture(new DxConflictException("Provider role request is not in PENDING status"));
              }

              Map<String, Object> updateDataMap = Map.of(
                Constants.STATUS, status
              );

              Map<String, Object> conditionMap = Map.of(
                Constants.ORG_CREATE_ID, existingRequest.id().toString()
              );
              return providerRequestDAO.update(conditionMap, updateDataMap)
                .compose(updated -> {
                  return keycloakUserService.addRoleToUser(userId, DxRole.PROVIDER)
                    .compose(success -> {
                      if (!success) {
                        return Future.failedFuture("Failed to assign PROVIDER role in Keycloak");
                      }
                      return Future.succeededFuture(true);
                    });
                });
            }
          }).recover(ServiceErrorHelper.mapNotFound("No Request or  user found in organization"));
      }
    });
  }

  @Override
  public Future<ProviderRoleRequest> getProviderRequestById(UUID requestId) {
    return providerRequestDAO.get(requestId)
      .compose(request -> {
        if (request == null) {
          return Future.failedFuture(
            new DxNotFoundException("Provider request not found for ID: " + requestId)
          );
        }
        return Future.succeededFuture(request);
      })
      .recover(err -> {
        LOGGER.error("Failed to fetch provider request for ID {}: {}", requestId, err.getMessage());
        if (err instanceof DxNotFoundException) {
          return Future.failedFuture(err);
        } else {
          return Future.failedFuture(new DxRuntimeException("Database error while fetching provider request", err));
        }
      });
  }

  @Override
  public Future<ProviderRoleRequest> getProviderRoleRequestByUserId(UUID userId) {
    Map<String, Object> filter = Map.of(
      USER_ID, userId.toString(),
      Constants.STATUS, Status.PENDING.getStatus()
    );

    return providerRequestDAO.getAllWithFilters(filter)
      .compose(requests -> {
        if (requests.isEmpty()) {
          return Future.failedFuture(new DxNotFoundException(
            "No pending provider request found for userId: " + userId));
        }
        return Future.succeededFuture(requests.get(0));
      });
  }

  @Override
  public Future<Boolean> deleteProviderRoleRequest(UUID orgId, UUID userId) {
    Map<String, Object> conditionMap = Map.of(
      USER_ID, userId.toString(),
      Constants.ORGANIZATION_ID, orgId.toString()
    );

    return providerRequestDAO.getAllWithFilters(conditionMap).compose(ar -> {
      ProviderRoleRequest res = ar.isEmpty() ? null : ar.get(0);
      if (res == null) {
        return Future.failedFuture(new DxNotFoundException("No provider request found for user in organization"));
      }

      UUID id = res.id();

      return providerRequestDAO.delete(id).compose(success -> {
        if (!success) {
          return Future.failedFuture(new DxPgException("Failed to delete provider request"));
        }
        return Future.succeededFuture(true);
      });
    });
  }

  @Override
  public Future<Boolean> deleteProviderRoleRequestById(UUID id) {
    return providerRequestDAO.delete(id)
      .compose(deleted -> {
        if (!deleted) {
          return Future.failedFuture(
            new DxNotFoundException("Failed to delete provider role request with ID: " + id)
          );
        }
        return Future.succeededFuture(true);
      });
  }
}
