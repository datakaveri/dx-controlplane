package org.cdpg.dx.aaa.organization.service.impl;

import static org.cdpg.dx.aaa.organization.config.Constants.*;
import static org.cdpg.dx.common.util.DateTimeHelper.FORMATTER;

import io.vertx.core.Future;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.cdpg.dx.aaa.organization.config.Constants;
import org.cdpg.dx.aaa.organization.dao.OrganizationCreateRequestDAO;
import org.cdpg.dx.aaa.organization.dao.OrganizationDAO;
import org.cdpg.dx.aaa.organization.dao.OrganizationUserDAO;
import org.cdpg.dx.aaa.organization.models.*;
import org.cdpg.dx.aaa.organization.service.OrganizationHierarchyService;
import org.cdpg.dx.aaa.organization.service.OrganizationLifecycleService;
import org.cdpg.dx.auth.model.DxRole;
import org.cdpg.dx.common.exception.BaseDxException;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.exception.DxConflictException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.exception.NoRowFoundException;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.util.ServiceErrorHelper;
import org.cdpg.dx.database.postgres.models.PaginatedResult;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OrganizationLifecycleServiceImpl implements OrganizationLifecycleService {

  private static final Logger LOGGER = LoggerFactory.getLogger(OrganizationLifecycleServiceImpl.class);
  private static final String CREATE_REQUEST_NOT_FOUND = "No request found with given ID";

  private final OrganizationCreateRequestDAO createRequestDAO;
  private final OrganizationDAO orgDAO;
  private final OrganizationUserDAO orgUserDAO;
  private final KeycloakUserService keycloakUserService;
  private final OrganizationHierarchyService hierarchyService;

  public OrganizationLifecycleServiceImpl(
      OrganizationCreateRequestDAO createRequestDAO,
      OrganizationDAO orgDAO,
      OrganizationUserDAO orgUserDAO,
      KeycloakUserService keycloakUserService,
      OrganizationHierarchyService hierarchyService) {
    this.createRequestDAO = createRequestDAO;
    this.orgDAO = orgDAO;
    this.orgUserDAO = orgUserDAO;
    this.keycloakUserService = keycloakUserService;
    this.hierarchyService = hierarchyService;
  }

  @Override
  public Future<OrganizationCreateRequest> createOrganizationRequest(OrganizationCreateRequest request) {
    if (!request.hasProposedHierarchy()) {
      return createRequestDAO.create(request);
    }
    // Reject a malformed tree up front rather than at approval time, after the
    // organisation has already been created.
    return hierarchyService
        .validateTree(request.departments())
        .compose(valid -> createRequestDAO.create(request));
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
    if (status != Status.GRANTED && status != Status.REJECTED) {
      return Future.failedFuture(
          new DxBadRequestException("Status must be either granted or rejected"));
    }

    // Only a pending request can be decided -- otherwise a withdrawn (or an
    // already granted) request could be approved and create an organisation.
    return getPendingCreateRequest(requestId)
        .compose(request -> transitionPendingCreateRequest(requestId, status))
        .compose(
            updated -> {
              if (status == Status.GRANTED) {
                return createOrganizationFromRequest(requestId);
              }
              return Future.succeededFuture(true);
            });
  }

  @Override
  public Future<OrganizationCreateRequest> withdrawOrganizationCreateRequest(
      UUID userId, UUID requestId) {
    return createRequestDAO
        .get(requestId)
        .recover(ServiceErrorHelper.mapNotFound(CREATE_REQUEST_NOT_FOUND))
        .compose(
            request -> {
              // Someone else's request is reported as missing, not forbidden, so
              // request ids cannot be probed.
              if (request == null || !userId.equals(request.requestedBy())) {
                return Future.failedFuture(new DxNotFoundException(CREATE_REQUEST_NOT_FOUND));
              }
              if (!Status.PENDING.getStatus().equals(request.status())) {
                return Future.failedFuture(
                    new DxConflictException(
                        "Only a pending request can be withdrawn; this request is already "
                            + request.status()));
              }
              return transitionPendingCreateRequest(requestId, Status.WITHDRAWN);
            });
  }

  private Future<OrganizationCreateRequest> getPendingCreateRequest(UUID requestId) {
    return createRequestDAO
        .get(requestId)
        .recover(ServiceErrorHelper.mapNotFound(CREATE_REQUEST_NOT_FOUND))
        .compose(
            request -> {
              if (request == null) {
                return Future.failedFuture(new DxNotFoundException(CREATE_REQUEST_NOT_FOUND));
              }
              if (!Status.PENDING.getStatus().equals(request.status())) {
                return Future.failedFuture(
                    new DxConflictException("Request has already been " + request.status()));
              }
              return Future.succeededFuture(request);
            });
  }

  /**
   * Moves a request out of {@code pending}. The status is part of the WHERE clause, so a
   * concurrent withdraw and approve cannot both succeed: whichever runs second matches no row
   * and gets a conflict.
   */
  private Future<OrganizationCreateRequest> transitionPendingCreateRequest(
      UUID requestId, Status target) {
    Map<String, Object> conditionMap =
        Map.of(
            Constants.ORG_CREATE_ID, requestId.toString(),
            Constants.STATUS, Status.PENDING.getStatus());
    Map<String, Object> updateDataMap = Map.of(Constants.STATUS, target.getStatus());

    return createRequestDAO
        .update(conditionMap, updateDataMap)
        .recover(
            err ->
                BaseDxException.from(err) instanceof NoRowFoundException
                    ? Future.failedFuture(
                        new DxConflictException("Request is no longer pending"))
                    : Future.failedFuture(err));
  }

  public Future<Boolean> createOrganizationFromRequest(UUID requestId) {
    return createRequestDAO
        .get(requestId)
        .compose(
            request -> {
              Organization org =
                  new Organization(
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
                      null);
              return orgDAO
                  .create(org)
                  .compose(
                      createdOrg -> {
                        OrganizationUser orgUser =
                            new OrganizationUser(
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
                                null,
                                null,
                                null);
                        // TODO need to revert the update status and create organisation request if
                        // fails
                        return Future.all(
                                keycloakUserService.addRoleToUser(
                                    request.requestedBy(), DxRole.ORG_ADMIN),
                                keycloakUserService.addRoleToUser(
                                    request.requestedBy(), DxRole.PROVIDER),
                                keycloakUserService.setOrganisationDetails(
                                    request.requestedBy(), createdOrg.id(), createdOrg.orgName()))
                            .compose(
                                compositeResult -> {
                                  boolean roleAssigned1 = compositeResult.resultAt(0);
                                  boolean roleAssigned2 = compositeResult.resultAt(1);

                                  boolean orgDetailsSet = compositeResult.resultAt(2);

                                  if (!roleAssigned1) {
                                    return Future.failedFuture(
                                        "Failed to assign ORG_ADMIN role to user");
                                  }

                                  if (!roleAssigned2) {
                                    return Future.failedFuture(
                                        "Failed to assign PROVIDER role to user");
                                  }

                                  if (!orgDetailsSet) {
                                    return Future.failedFuture(
                                        "Failed to set organization details for user");
                                  }

                                  return orgUserDAO
                                      .create(orgUser)
                                      .compose(
                                          user -> applyProposedHierarchy(request, createdOrg.id()));
                                });
                      });
            });
  }

  /**
   * If the create request carried a department tree, insert it for the newly created organisation
   * -- so a single cos_admin approval yields a hierarchical organisation. No-op for a flat request.
   */
  private Future<Boolean> applyProposedHierarchy(OrganizationCreateRequest request, UUID orgId) {
    if (!request.hasProposedHierarchy()) {
      return Future.succeededFuture(true);
    }
    return hierarchyService
        .bulkCreateTree(orgId, request.departments())
        .map(
            nodes -> {
              LOGGER.info(
                  "Applied proposed hierarchy ({} departments) to organisation {} from create"
                      + " request {}",
                  nodes.size(),
                  orgId,
                  request.id());
              return true;
            })
        .onFailure(
            err ->
                LOGGER.error(
                    "Organisation {} was created from request {} but applying its"
                        + " proposed hierarchy failed: {}",
                    orgId,
                    request.id(),
                    err.getMessage()));
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

    // Always refresh updatedAt when the organisation is updated
    updateDataMap.put(Constants.UPDATED_AT, LocalDateTime.now().format(FORMATTER));

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
