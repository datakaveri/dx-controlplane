package org.cdpg.dx.aaa.organization.service.impl;

import static org.cdpg.dx.common.util.DateTimeHelper.FORMATTER;

import static org.cdpg.dx.aaa.organization.config.Constants.*;
import static org.cdpg.dx.aaa.organization.models.Status.PENDING;
import static org.cdpg.dx.aaa.organization.models.Status.WITHDRAWN;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.organization.config.Constants;
import org.cdpg.dx.aaa.organization.dao.OrganizationDAO;
import org.cdpg.dx.aaa.organization.dao.OrganizationJoinRequestDAO;
import org.cdpg.dx.aaa.organization.dao.OrganizationUserDAO;
import org.cdpg.dx.aaa.organization.models.*;
import org.cdpg.dx.aaa.organization.service.OrganizationApprovalService;
import org.cdpg.dx.aaa.organization.service.OrganizationHierarchyService;
import org.cdpg.dx.aaa.organization.service.OrganizationMembershipService;
import org.cdpg.dx.auth.model.DxRole;
import org.cdpg.dx.auth.model.Scopes;
import org.cdpg.dx.common.exception.*;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.util.ServiceErrorHelper;
import org.cdpg.dx.database.postgres.models.PaginatedResult;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OrganizationMembershipServiceImpl implements OrganizationMembershipService {

  private static final Logger LOGGER = LoggerFactory.getLogger(OrganizationMembershipServiceImpl.class);

  private final OrganizationJoinRequestDAO joinRequestDAO;
  private final OrganizationUserDAO orgUserDAO;
  private final OrganizationDAO orgDAO;
  private final KeycloakUserService keycloakUserService;
  private final ItemService itemService;
  private final OrganizationHierarchyService hierarchyService;
  private final OrganizationApprovalService organizationApprovalService;

  public OrganizationMembershipServiceImpl(
      OrganizationJoinRequestDAO joinRequestDAO,
      OrganizationUserDAO orgUserDAO,
      OrganizationDAO orgDAO,
      KeycloakUserService keycloakUserService,
      ItemService itemService,
      OrganizationHierarchyService hierarchyService,
      OrganizationApprovalService organizationApprovalService) {
    this.joinRequestDAO = joinRequestDAO;
    this.orgUserDAO = orgUserDAO;
    this.orgDAO = orgDAO;
    this.keycloakUserService = keycloakUserService;
    this.itemService = itemService;
    this.hierarchyService = hierarchyService;
    this.organizationApprovalService = organizationApprovalService;
  }

  @Override
  public Future<OrganizationJoinRequest> joinOrganizationRequest(
      OrganizationJoinRequest organizationJoinRequest) {
    UUID organizationId = organizationJoinRequest.organizationId();
    UUID requestedNodeId = organizationJoinRequest.requestedNodeId();

    return orgDAO
        .get(organizationId)
        .compose(
            organization -> {
              if (organization == null) {
                return Future.failedFuture(new DxNotFoundException("Organization not found"));
              }

              return hierarchyService.hasHierarchy(organizationId);
            })
        .compose(
            hasHierarchy -> {
              if (!hasHierarchy) {
                if (organizationJoinRequest.requestedNodeId() != null) {
                  return Future.failedFuture(
                      new DxValidationException(
                          "Hierarchy node cannot be specified for a non-hierarchical organization"));
                }
                // Existing flat organization behaviour.
                // requestedNodeId should normally be null.
                return joinRequestDAO.create(organizationJoinRequest);
              }

              if (requestedNodeId == null) {
                return Future.failedFuture(
                    new DxValidationException("Hierarchy node is required for this organization"));
              }

              return hierarchyService
                  .getNode(organizationId, requestedNodeId)
                  .compose(
                      node -> {
                        if (node == null) {
                          return Future.failedFuture(
                              new DxNotFoundException("Requested hierarchy node not found"));
                        }

                        return joinRequestDAO
                            .create(organizationJoinRequest)
                            .compose(
                                createdRequest -> {

                                  /*
                                   * Hierarchical organisation:
                                   *
                                   * The request remains publicly "pending".
                                   * Internally, an approval task is created for L1.
                                   */
                                  return organizationApprovalService
                                      .createApproval(
                                          organizationId,
                                          createdRequest.id(),
                                          OrganizationApprovalType.ORGANIZATION_USER_JOIN,
                                          requestedNodeId)
                                      .map(ignored -> createdRequest);
                                });
                      });
            });
  }

  @Override
  public Future<OrganizationJoinRequest> getOrganizationJoinRequestById(UUID requestId) {
    return joinRequestDAO.get(requestId)
      .compose(request -> {
        if (request == null) {
          return Future.failedFuture(
            new DxNotFoundException("organization join request not found for ID: " + requestId)
          );
        }
        return Future.succeededFuture(request);
      })
      .recover(err -> {
        LOGGER.error("Failed to fetch organization join request for ID {}: {}", requestId, err.getMessage());
        if (err instanceof DxNotFoundException) {
          return Future.failedFuture(err);
        } else {
          return Future.failedFuture(new DxRuntimeException("Database error while fetching provider request", err));
        }
      });
  }

  @Override
  public Future<PaginatedResult<OrganizationJoinRequest>> getOrganizationPendingJoinRequests(PaginatedRequest paginatedRequest) {
    //TODO need to create another funtion for this
//        Map<String, Object> filterMap = Map.of(
//                Constants.ORGANIZATION_ID, orgId.toString()
//        );

    return joinRequestDAO.getAllWithFilters(paginatedRequest);
  }

  @Override
  public Future<Boolean> updateOrganizationJoinRequestStatus(
      UUID requestId, Status status, UUID approverUserId) {

    return joinRequestDAO
        .get(requestId)
        .compose(
            joinRequest -> {
              if (joinRequest == null) {
                return Future.succeededFuture(false);
              }

              UUID organizationId = joinRequest.organizationId();

              return orgDAO
                  .get(organizationId)
                  .compose(
                      organization -> {
                        if (organization == null) {
                          return Future.failedFuture(
                              new DxNotFoundException("Organisation not found: " + organizationId));
                        }

                        return hierarchyService.hasHierarchy(organizationId);
                      })
                  .compose(
                      hasHierarchy -> {
                        /*
                         * Existing organisation behaviour.
                         *
                         * No department tree means the organisation is flat and the
                         * existing Org Admin workflow continues to work exactly as before.
                         */
                        if (!hasHierarchy) {
                          return updateFlatOrganizationJoinRequest(requestId, status);
                        }

                        /*
                         * Hierarchical organisation.
                         *
                         * The old direct status update is not allowed to bypass
                         * the approval engine.
                         */
                        if (status == Status.GRANTED) {

                          return organizationApprovalService
                              .approve(
                                  organizationId,
                                  requestId,
                                  OrganizationApprovalType.ORGANIZATION_USER_JOIN,
                                  joinRequest.requestedNodeId(),
                                  approverUserId,
                                  null)
                              .compose(
                                  approval -> finalizeHierarchicalJoinRequest(requestId, approval));
                        }

                        if (status == Status.REJECTED) {

                          return organizationApprovalService
                              .reject(
                                  organizationId,
                                  requestId,
                                  OrganizationApprovalType.ORGANIZATION_USER_JOIN,
                                  joinRequest.requestedNodeId(),
                                  approverUserId,
                                  null)
                              .compose(
                                  approval -> updateJoinRequestStatus(requestId, Status.REJECTED));
                        }

                        return Future.failedFuture(
                            new DxValidationException(
                                "Only granted or rejected status is allowed"));
                      });
            });
  }

  private Future<Boolean> updateJoinRequestStatus(UUID requestId, Status status) {

    Map<String, Object> conditionMap = Map.of(Constants.ORG_JOIN_ID, requestId.toString());

    Map<String, Object> updateFields = new HashMap<>();

    updateFields.put(STATUS, status.getStatus());

    updateFields.put(PROCESSED_AT, LocalDateTime.now().format(FORMATTER));

    return joinRequestDAO
        .update(conditionMap, updateFields)
        .compose(
            updated -> {
              if (status == Status.GRANTED) {
                return addUserToOrganizationFromRequest(requestId);
              }
              return Future.succeededFuture(true);
            })
        .recover(ServiceErrorHelper.mapNotFound("No request found with given ID"));
  }

  private Future<Boolean> updateFlatOrganizationJoinRequest(UUID requestId, Status status) {

    Map<String, Object> conditionMap = Map.of(Constants.ORG_JOIN_ID, requestId.toString());
    Map<String, Object> updateDataMap = Map.of(Constants.STATUS, status.getStatus());

    return joinRequestDAO
        .update(conditionMap, updateDataMap)
        .compose(
            updated -> {
              if (status == Status.GRANTED) {
                return addUserToOrganizationFromRequest(requestId);
              }

              return Future.succeededFuture(true);
            })
        .recover(ServiceErrorHelper.mapNotFound("No request found with given ID"));
  }

  private Future<Boolean> finalizeHierarchicalJoinRequest(
      UUID requestId, OrganizationApproval approval) {

    if (approval.status() != OrganizationApprovalStage.APPROVED) {
      return Future.failedFuture(
          new DxValidationException("Join request approval is not in approved state"));
    }

    Map<String, Object> conditionMap = Map.of(Constants.ORG_JOIN_ID, requestId.toString());
    Map<String, Object> updateDataMap = Map.of(Constants.STATUS, Status.GRANTED.getStatus());

    return joinRequestDAO
        .update(conditionMap, updateDataMap)
        .compose(updated -> addUserToOrganizationFromRequest(requestId))
        .recover(ServiceErrorHelper.mapNotFound("No request found with given ID"));
  }

  @Override
  public Future<Boolean> addUserToOrganizationFromRequest(UUID requestId) {

    return joinRequestDAO
        .get(requestId)
        .compose(
            joinRequest -> {
              if (joinRequest == null) {
                return Future.failedFuture(
                    new DxNotFoundException("Organization join request not found"));
              }

              UUID orgId = joinRequest.organizationId();
              UUID userId = joinRequest.userId();

              return orgDAO
                  .get(orgId)
                  .compose(
                      organization -> {
                        if (organization == null) {
                          return Future.failedFuture(
                              new DxNotFoundException("Organisation not found"));
                        }

                        OrganizationUser organizationUser =
                            new OrganizationUser(
                                null,
                                orgId,
                                userId,
                                joinRequest.userName(),
                                Role.USER,
                                joinRequest.jobTitle(),
                                joinRequest.empId(),
                                null,
                                joinRequest.officialEmail(),
                                joinRequest.requestedNodeId(),
                                null,
                                null,
                                null);

                        return orgUserDAO
                            .create(organizationUser)
                            .compose(
                                created ->
                                    // Call Keycloak to set organization details
                                    keycloakUserService
                                        .setOrganisationDetails(
                                            userId, orgId, organization.orgName())
                                        .compose(
                                            success -> {
                                              if (!success) {
                                                return Future.failedFuture(
                                                    "Failed to set organization details in Keycloak");
                                              }

                                              return Future.succeededFuture(true);
                                            }));
                      });
            });
  }

  @Override
  public Future<OrganizationJoinRequest> withdrawJoinRequest(UUID userId, UUID requestId) {

    Map<String, Object> filterParams = Map.of(
      USER_ID, userId.toString(),
      ORG_JOIN_ID, requestId.toString()
    );

    Map<String, Object> updateMap = Map.of(
      STATUS, WITHDRAWN.getStatus()
    );

    return joinRequestDAO
      .getAllWithFilters(filterParams)
      .compose(res -> {

        if (res.isEmpty()) {
          return Future.failedFuture(
            new DxNotFoundException("No request found with given ID"));
        }

        if(!res.getFirst().status().equals(PENDING.getStatus()))
        {
          return Future.failedFuture(
            new DxForbiddenException("Only pending join request can be withhdrawn"));
        }

        return joinRequestDAO
          .update(filterParams, updateMap)
          .compose(updatedReq -> {

            if (!WITHDRAWN.getStatus().equals(updatedReq.status())) {
              return Future.failedFuture(
                new DxBadRequestException("Failed to withdraw join request"));
            }

            return Future.succeededFuture(updatedReq);
          });
      })
      .recover(ServiceErrorHelper.mapNotFound("No request found with given ID"));
  }

  @Override
  public Future<List<OrganizationJoinRequest>> getOrganizationJoinRequestsByUser(UUID userId) {
    Map<String, Object> pendingFilter = Map.of(
      USER_ID, userId.toString(),
      Constants.STATUS, Status.PENDING.getStatus()
    );
    Map<String, Object> grantedFilter = Map.of(
      USER_ID, userId.toString(),
      Constants.STATUS, Status.GRANTED.getStatus()
    );

    Map<String, Object> rejectedFilter = Map.of(
      USER_ID, userId.toString(),
      Constants.STATUS, Status.REJECTED.getStatus()
    );

    Future<List<OrganizationJoinRequest>> pendingFuture = joinRequestDAO.getAllWithFilters(pendingFilter);
    Future<List<OrganizationJoinRequest>> grantedFuture = joinRequestDAO.getAllWithFilters(grantedFilter);
    Future<List<OrganizationJoinRequest>> rejectedFuture = joinRequestDAO.getAllWithFilters(rejectedFilter);

    return Future.all(pendingFuture, grantedFuture,rejectedFuture)
      .map(cf -> {
        List<OrganizationJoinRequest> merged = new java.util.ArrayList<>();
        merged.addAll(cf.resultAt(0));
        merged.addAll(cf.resultAt(1));
        return merged;
      });
  }

  @Override
  public Future<List<OrganizationJoinRequest>> getOrganizationJoinRequestsByOrgId(UUID orgId) {
    Map<String, Object> pendingFilter = Map.of(
      ORGANIZATION_ID, orgId.toString(),
      Constants.STATUS, Status.PENDING.getStatus()
    );
    Map<String, Object> grantedFilter = Map.of(
      ORGANIZATION_ID, orgId.toString(),
      Constants.STATUS, Status.GRANTED.getStatus()
    );

    Map<String, Object> rejectedFilter = Map.of(
      ORGANIZATION_ID, orgId.toString(),
      Constants.STATUS, Status.REJECTED.getStatus()
    );

    Future<List<OrganizationJoinRequest>> pendingFuture = joinRequestDAO.getAllWithFilters(pendingFilter);
    Future<List<OrganizationJoinRequest>> grantedFuture = joinRequestDAO.getAllWithFilters(grantedFilter);
    Future<List<OrganizationJoinRequest>> rejectedFuture = joinRequestDAO.getAllWithFilters(rejectedFilter);

    return Future.all(pendingFuture, grantedFuture,rejectedFuture)
      .map(cf -> {
        List<OrganizationJoinRequest> merged = new java.util.ArrayList<>();
        merged.addAll(cf.resultAt(0));
        merged.addAll(cf.resultAt(1));
        return merged;
      });
  }

  @Override
  public Future<List<OrganizationJoinRequest>> getAllOrganizationJoinRequests() {
    return joinRequestDAO.getAll();
  }

  @Override
  public Future<PaginatedResult<OrganizationUser>> getOrganizationUsers(PaginatedRequest paginatedRequest) {
//        Map<String, Object> filterMap = Map.of(Constants.ORGANIZATION_ID, paginatedRequest);
    return orgUserDAO.getAllWithFilters(paginatedRequest);
  }

  @Override
  public Future<Boolean> updateUserRole(UUID orgId, UUID userId, Role role) {

    Map<String, Object> conditionMap = Map.of(
      Constants.ORGANIZATION_ID, orgId.toString(),
      USER_ID, userId.toString()
    );


    // Role.DEPT_ADMIN can only be granted via the dedicated dept-admin
    // assignment flow (it must also set admin_of_node_id) -- any other role
    // change through this generic path must not leave a stale administered
    // department behind.
    Map<String, Object> updateDataMap = new HashMap<>();
    updateDataMap.put(Constants.ROLE, role.getRoleName());
    if (role != Role.DEPT_ADMIN) {
      updateDataMap.put(Constants.ADMIN_OF_NODE_ID, null);
    }

    return orgUserDAO.update(conditionMap, updateDataMap)
      .compose(updated -> role == Role.DEPT_ADMIN
        ? Future.succeededFuture(true)
        : keycloakUserService.removeUserScopes(userId, Scopes.DEPT_ADMIN))
      .map(true)
      .recover(ServiceErrorHelper.mapNotFound("No matching user found in organization"));
  }

  @Override
  public Future<Boolean> deleteOrganizationUser(UUID orgId, UUID userId) {
    return orgUserDAO.deleteUserByOrgId(orgId, userId)
      .compose(deleted -> {
        if (deleted) {

          return keycloakUserService.updateUserAttributes(userId, Map.of(
              "organisation_id", "",
              "organisation_name", ""
            ))
            .compose(v -> keycloakUserService.removeUserScopes(userId, Scopes.DEPT_ADMIN))
            .map(v -> true);
        } else {
          return Future.succeededFuture(false);
        }
      });
  }

  @Override
  public Future<Boolean> deleteProviderUser(UUID userId, UUID orgAdminId, UUID orgId) {
    return orgUserDAO.deleteUserByOrgId(orgId, userId)
      .compose(deleted -> {
        if (deleted) {
          return keycloakUserService.updateUserAttributes(userId, Map.of(
              "organisation_id", "",
              "organisation_name", ""
            ))
            .compose(v->keycloakUserService.removeRoleFromUser(userId, DxRole.PROVIDER))
            .compose(v -> keycloakUserService.removeUserScopes(userId, Scopes.DEPT_ADMIN))
            .compose(ar-> itemService.ownerShipTransfer(userId.toString(), orgAdminId.toString(), orgId.toString()))
            .onFailure(err -> LOGGER.error("Failed to update user attributes in Keycloak after deleting organization user", err))
            .map(v -> true);
        } else {
          return Future.succeededFuture(false);
        }
      });
  }

  @Override
  public Future<OrganizationUser> getOrganizationUserInfo(UUID userId) {
    Map<String, Object> filterMap = Map.of(USER_ID, userId.toString());
    return orgUserDAO.getAllWithFilters(filterMap).compose(orgUserList -> {
      if (orgUserList.isEmpty()) {
        return Future.failedFuture(new DxNotFoundException("No user found !"));
      } else if (orgUserList.size() == 1) {
        return Future.succeededFuture(orgUserList.get(0));
      } else {
        LOGGER.error("multiple org users found");
        return Future.failedFuture(new DxPgException("Some thing went wrong"));
      }
    }).recover(
      err -> {
          LOGGER.error("Error :{}" , err.getMessage());
        // Log or transform the error if needed
        return Future.failedFuture(err);
      });
  }

  @Override
  public Future<OrganizationUser> getOrganisationUserByUserId(UUID userId) {

    Map<String, Object> filters = Map.of(USER_ID, userId.toString());

    return orgUserDAO.getAllWithFilters(filters)
      .compose(users -> {

        if (users == null || users.isEmpty()) {
          return Future.failedFuture(
            new DxNotFoundException("No organization user found with userId: " + userId)
          );
        }

        // Return the first record (should be unique per user)
        return Future.succeededFuture(users.get(0));
      });
  }

  @Override
  public Future<List<OrganizationUser>> getOrganisationAdminId(UUID orgId) {
    Map<String,Object> conditonMap = Map.of(
      Constants.ORGANIZATION_ID ,orgId.toString(),
      Constants.ROLE , Role.ADMIN.getRoleName());

    return orgUserDAO.getAllWithFilters(conditonMap);
  }

  @Override
  public Future<Boolean> isOrgAdmin(UUID orgid, UUID userid) {
    return orgUserDAO.isOrgAdmin(orgid, userid);
  }

  @Override
  public Future<UUID> getUserOrgAdminId(UUID orgId) {
    Map<String, Object> conditionMap = Map.of(
      Constants.ORGANIZATION_ID, orgId.toString(),
      Constants.ROLE, Role.ADMIN.getRoleName()
    );

    return orgUserDAO.getAllWithFilters(conditionMap).compose(orgUsers -> {
      if (orgUsers.isEmpty()) {
        return Future.failedFuture(new DxNotFoundException("No organization admin found for the given organization ID"));
      }
      return Future.succeededFuture(orgUsers.get(0).userId());
    });
  }

  @Override
  public Future<Boolean> deleteOrganizationJoinRequest(UUID orgId, UUID userId) {
    Map<String, Object> conditionMap = Map.of(
      USER_ID, userId.toString(),
      Constants.ORGANIZATION_ID, orgId.toString()
    );

    return joinRequestDAO.getAllWithFilters(conditionMap).compose(ar -> {
      OrganizationJoinRequest res = ar.isEmpty() ? null : ar.get(0);
      if (res == null) {
        return Future.failedFuture(new DxNotFoundException("No join request found for user in organization"));
      }

      UUID id = res.id();

      return joinRequestDAO.delete(id).compose(success -> {
        if (!success) {
          return Future.failedFuture(new DxPgException("Failed to delete join request"));
        }
        return Future.succeededFuture(true);
      });
    });
  }

  @Override
  public Future<Boolean> deleteOrganizationJoinRequestById(UUID requestId) {
    return orgDAO.get(requestId)
      .compose(request -> {
        if (request == null) {
          return Future.failedFuture(
            new DxNotFoundException("Join organisation request not found for ID: " + requestId));
        }

        return joinRequestDAO.delete(requestId)
          .compose(deleted -> {
            if (!deleted) {
              return Future.failedFuture(
                new DxNotFoundException("Failed to delete join organisation request with ID: " + requestId));
            }
            return Future.succeededFuture(true);
          });
      });
  }

  @Override
  public Future<List<JsonObject>> enrichWithUserInfo(List<JsonObject> computeReqs) {
     List<Future> futures = new ArrayList<>();

     for(JsonObject req:computeReqs)
     {
        UUID userId = UUID.fromString(req.getString("user_id"));

        Future<Void> enrichmentFuture =
          getOrganisationUserByUserId(userId)
            .recover(err -> {
              LOGGER.warn("The userId {} doesnt exist in org_user table", userId);
              return Future.succeededFuture(null);
            })
            .compose(userRes->{
            if (userRes == null) {
              LOGGER.warn("The userId {} doesnt exist in org_user table",userId);
              return Future.succeededFuture(null);
            }

            req.put("job_title", userRes.jobTitle());
            req.put("emp_id", userRes.empId());

            UUID orgId = userRes.organizationId();

            return orgDAO.get(orgId) .map(orgRes -> {
              req.put("org_name", orgRes.orgName());
              return null;
            });
          });

        futures.add(enrichmentFuture);
     }

     return CompositeFuture.all(futures).map(v->computeReqs);
  }
}
