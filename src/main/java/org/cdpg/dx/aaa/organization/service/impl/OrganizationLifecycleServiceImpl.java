package org.cdpg.dx.aaa.organization.service.impl;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.organization.dao.OrganizationCreateRequestDAO;
import org.cdpg.dx.aaa.organization.dao.OrganizationDAO;
import org.cdpg.dx.aaa.organization.dao.OrganizationUserDAO;
import org.cdpg.dx.aaa.organization.config.Constants;
import org.cdpg.dx.aaa.organization.models.*;
import org.cdpg.dx.aaa.organization.service.OrganizationLifecycleService;
import org.cdpg.dx.auth.authorization.model.DxRole;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.util.ServiceErrorHelper;
import org.cdpg.dx.database.postgres.models.PaginatedResult;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.cdpg.dx.aaa.organization.config.Constants.*;

public class OrganizationLifecycleServiceImpl implements OrganizationLifecycleService {

  private static final Logger LOGGER = LoggerFactory.getLogger(OrganizationLifecycleServiceImpl.class);

  private final OrganizationCreateRequestDAO createRequestDAO;
  private final OrganizationDAO orgDAO;
  private final OrganizationUserDAO orgUserDAO;
  private final KeycloakUserService keycloakUserService;

  public OrganizationLifecycleServiceImpl(
      OrganizationCreateRequestDAO createRequestDAO,
      OrganizationDAO orgDAO,
      OrganizationUserDAO orgUserDAO,
      KeycloakUserService keycloakUserService) {
    this.createRequestDAO = createRequestDAO;
    this.orgDAO = orgDAO;
    this.orgUserDAO = orgUserDAO;
    this.keycloakUserService = keycloakUserService;
  }

  @Override
  public Future<OrganizationCreateRequest> createOrganizationRequest(OrganizationCreateRequest request) {
    return createRequestDAO.create(request);
  }

  @Override
  public Future<OrganizationCreateRequest> getOrganizationCreateRequestById(UUID requestId) {
    return createRequestDAO.get(requestId);
  }

  @Override
  public Future<List<OrganizationCreateRequest>> getOrganizationCreateRequestsByUserId(UUID userId) {

    Map<String, Object> pendingFilter = Map.of(
      Constants.REQUESTED_BY, userId.toString(),
      Constants.STATUS, Status.PENDING.getStatus()
    );
    Map<String, Object> grantedFilter = Map.of(
      Constants.REQUESTED_BY, userId.toString(),
      Constants.STATUS, Status.GRANTED.getStatus()
    );

    Map<String, Object> rejectedFilter = Map.of(
      Constants.REQUESTED_BY, userId.toString(),
      Constants.STATUS, Status.REJECTED.getStatus()
    );

    Future<List<OrganizationCreateRequest>> pendingFuture = createRequestDAO.getAllWithFilters(pendingFilter);
    Future<List<OrganizationCreateRequest>> grantedFuture = createRequestDAO.getAllWithFilters(grantedFilter);
    Future<List<OrganizationCreateRequest>> rejectedFuture = createRequestDAO.getAllWithFilters(rejectedFilter);


    return Future.all(pendingFuture, grantedFuture,rejectedFuture)
      .map(cf -> {
        List<OrganizationCreateRequest> merged = new java.util.ArrayList<>();
        merged.addAll(cf.resultAt(0));
        merged.addAll(cf.resultAt(1));
        return merged;
      });
  }

  @Override
  public Future<List<OrganizationCreateRequest>> getAllPendingGrantedOrganizationCreateRequests() {
    Map<String, Object> filterMapPending = Map.of(
      Constants.STATUS, Status.PENDING.getStatus()
    );
    Map<String, Object> filterMapGranted = Map.of(
      Constants.STATUS, Status.GRANTED.getStatus()
    );

    Future<List<OrganizationCreateRequest>> pendingFuture = createRequestDAO.getAllWithFilters(filterMapPending);
    Future<List<OrganizationCreateRequest>> grantedFuture = createRequestDAO.getAllWithFilters(filterMapGranted);

    return Future.all(pendingFuture, grantedFuture)
      .map(cf -> {
        List<OrganizationCreateRequest> merged = new ArrayList<>();
        merged.addAll(cf.resultAt(0));
        merged.addAll(cf.resultAt(1));
        return merged;
      });
  }

  @Override
  public Future<PaginatedResult<OrganizationCreateRequest>> getAllOrganizationCreateRequests(PaginatedRequest request) {
    return createRequestDAO.getAllWithFilters(request);
  }

  @Override
  public Future<Boolean> updateOrganizationCreateRequestStatus(UUID requestId, Status status) {
    Map<String, Object> conditionMap = Map.of(
      Constants.ORG_CREATE_ID, requestId.toString()
    );
    Map<String, Object> updateDataMap = Map.of(
      Constants.STATUS, status.getStatus()
    );

    return createRequestDAO.update(conditionMap, updateDataMap).
      compose(updated -> {
        if (Status.GRANTED.getStatus().equals(status.getStatus())) {
          return createOrganizationFromRequest(requestId);
        }
        return Future.succeededFuture(true);
      }).recover(ServiceErrorHelper.mapNotFound("No request found with given ID"));

  }

  public Future<Boolean> createOrganizationFromRequest(UUID requestId) {
    return createRequestDAO.get(requestId)
      .compose(request -> {
        Organization org = new Organization(
          null,
          request.name(),
          request.logoPath(),
          request.entityType(),
          request.orgSector(),
          request.websiteLink(),
          request.address(),
          request.certificatePath(),
          request.pancardPath(),
          request.relevantDocPath(),
          request.orgDocuments(),
          null,
          null
        );
        return orgDAO.create(org)
          .compose(createdOrg ->
          {
            OrganizationUser orgUser = new OrganizationUser(
              null,
              createdOrg.id(),
              request.requestedBy(),
              request.userName(),
              Role.ADMIN,
              request.jobTitle(),
              request.empId(),
              request.orgManagerphoneNo(),
              request.managerEmail(),
              null,
              null
            );
            //TODO need to revert the update status and create organisation request if fails
            return Future.all(
              keycloakUserService.addRoleToUser(request.requestedBy(), DxRole.ORG_ADMIN),
              keycloakUserService.addRoleToUser(request.requestedBy(), DxRole.PROVIDER),
              keycloakUserService.setOrganisationDetails(request.requestedBy(), createdOrg.id(), createdOrg.orgName())
            ).compose(compositeResult -> {
              boolean roleAssigned1 = compositeResult.resultAt(0);
              boolean roleAssigned2 = compositeResult.resultAt(1);

              boolean orgDetailsSet = compositeResult.resultAt(2);

              if (!roleAssigned1) {
                return Future.failedFuture("Failed to assign ORG_ADMIN role to user");
              }

              if (!roleAssigned2) {
                return Future.failedFuture("Failed to assign PROVIDER role to user");
              }

              if (!orgDetailsSet) {
                return Future.failedFuture("Failed to set organization details for user");
              }

              return orgUserDAO.create(orgUser).map(user -> true);
            });

          });
      });
  }

  @Override
  public Future<Organization> getOrganizationById(UUID orgId) {
    return orgDAO.get(orgId);
  }

  @Override
  public Future<List<Organization>> getOrganizations() {
    return orgDAO.getAll();
  }

  @Override
  public Future<PaginatedResult<Organization>> getOrganizations(PaginatedRequest paginatedRequest) {
    return orgDAO.getAll(paginatedRequest);
  }

  @Override
  public Future<Organization> getOrganizationByName(String orgName) {

    Map<String, Object> filterMap = Map.of(Constants.ORG_NAME, orgName);

    return orgDAO.getAllWithFilters(filterMap).compose(orgList -> {
      if (orgList.isEmpty()) {
        return Future.failedFuture("Organization not found with name: " + orgName);
      } else if (orgList.size() == 1) {
        return Future.succeededFuture(orgList.get(0));
      } else {
        return Future.failedFuture("Multiple organizations found with name: " + orgName);
      }
    }).recover(
      err -> {
        // Log or transform the error if needed
        return Future.failedFuture("Failed to fetch organization: " + err.getMessage());
      });
  }

  @Override
  public Future<Organization> updateOrganizationById(UUID orgId, UpdateOrgDTO updateOrgDTO) {

    Map<String, Object> conditionMap = Map.of(
      Constants.ORG_CREATE_ID, orgId.toString()
    );
    Map<String, Object> updateDataMap = updateOrgDTO.toNonEmptyFieldsMap();

    return orgDAO.update(conditionMap, updateDataMap).compose(
      updated -> orgDAO.get(orgId)
    );
  }

  @Override
  public Future<Boolean> deleteOrganization(UUID orgId) {
    return orgDAO.delete(orgId);
  }

  @Override
  public Future<Boolean> deleteOrganizationRequestById(UUID requestId) {
    return createRequestDAO.get(requestId)
      .compose(request -> {
        if (request == null) {
          return Future.failedFuture(
            new DxNotFoundException("Organization request not found for ID: " + requestId));
        }

        return createRequestDAO.delete(requestId)
          .compose(deleted -> {
            if (!deleted) {
              return Future.failedFuture(
                new DxNotFoundException("Failed to delete organization request with ID: " + requestId));
            }
            return Future.succeededFuture(true);
          });
      });
  }
}
