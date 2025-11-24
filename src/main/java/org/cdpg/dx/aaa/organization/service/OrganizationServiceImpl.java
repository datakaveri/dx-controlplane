package org.cdpg.dx.aaa.organization.service;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.credit.models.CreditRequest;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.organization.dao.*;
import org.cdpg.dx.aaa.organization.models.*;
import org.cdpg.dx.aaa.organization.config.Constants;
import org.cdpg.dx.auth.authorization.model.DxRole;
import org.cdpg.dx.common.exception.*;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.database.postgres.models.PaginatedResult;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Map;

import static org.cdpg.dx.aaa.organization.config.Constants.USER_ID;
import static org.cdpg.dx.common.util.DateTimeHelper.FORMATTER;

public class OrganizationServiceImpl implements OrganizationService {

  private static final Logger LOGGER = LoggerFactory.getLogger(OrganizationServiceImpl.class);

  private final OrganizationCreateRequestDAO createRequestDAO;
  private final OrganizationUserDAO orgUserDAO;
  private final OrganizationDAO orgDAO;
  private final OrganizationJoinRequestDAO organizationJoinRequestDAO;
  private final OrganizationJoinRequestDAO joinRequestDAO;
  private final ProviderRoleRequestDAO providerRequestDAO;
  private final KeycloakUserService keycloakUserService;
  private final ItemService itemService;


  public OrganizationServiceImpl(OrganizationDAOFactory factory, KeycloakUserService keycloakUserService,ItemService itemService) {
    this.createRequestDAO = factory.organizationCreateRequest();
    this.orgUserDAO = factory.organizationUserDAO();
    this.orgDAO = factory.organizationDAO();
    this.organizationJoinRequestDAO = factory.organizationJoinRequestDAO();
    this.joinRequestDAO = factory.organizationJoinRequestDAO();
    this.providerRequestDAO = factory.providerRoleRequestDAO();
    this.keycloakUserService = keycloakUserService;
    this.itemService = itemService;
  }

  @Override
  public Future<OrganizationCreateRequest> createOrganizationRequest(OrganizationCreateRequest request) {
    return createRequestDAO.create(request);
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
  public
  Future <PaginatedResult<OrganizationCreateRequest>> getAllOrganizationCreateRequests(PaginatedRequest request) {
    return createRequestDAO.getAllWithFilters(request);
  }

  @Override
  public Future<OrganizationCreateRequest> getOrganizationCreateRequestById(UUID requestId) {
    return createRequestDAO.get(requestId);
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
      }).recover(dxEx -> {
        if (dxEx instanceof NoRowFoundException) {
          return Future.failedFuture(
            new DxNotFoundException("No request found with given ID", dxEx)
          );
        }
        return Future.failedFuture(dxEx);
      });

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
  public Future<OrganizationJoinRequest> joinOrganizationRequest(OrganizationJoinRequest organizationJoinRequest) {
    return joinRequestDAO.create(organizationJoinRequest);
  }


  @Override
  public Future<Boolean> updateOrganizationJoinRequestStatus(UUID requestId, Status status) {
    Map<String, Object> conditionMap = Map.of(
      Constants.ORG_JOIN_ID, requestId.toString()
    );
    Map<String, Object> updateDataMap = Map.of(
      Constants.STATUS, status.getStatus()
    );

    return joinRequestDAO.update(conditionMap, updateDataMap)
      .compose(approved -> {
        if (Status.GRANTED.getStatus().equals(status.getStatus())) {
          return addUserToOrganizationFromRequest(requestId);
        }
        return Future.succeededFuture(true);
      }).recover(err -> {
        BaseDxException dxEx = BaseDxException.from(err);
        if (dxEx instanceof NoRowFoundException) {
          return Future.failedFuture(
            new DxNotFoundException("No request found with given ID", dxEx)
          );
        }
        return Future.failedFuture(dxEx);
      });
  }

  public Future<Boolean> addUserToOrganizationFromRequest(UUID requestId) {
    return joinRequestDAO.get(requestId)
      .compose(joinRequest -> {
        UUID orgId = joinRequest.organizationId();
        UUID userId = joinRequest.userId();

        return orgDAO.get(orgId)
          .compose(organization -> {
            OrganizationUser newOrgUser = new OrganizationUser(
              null,
              orgId,
              userId,
              joinRequest.userName(),
              Role.USER,
              joinRequest.jobTitle(),
              joinRequest.empId(),
              null,
              joinRequest.officialEmail(),
              null,
              null
            );

            return orgUserDAO.create(newOrgUser)
              .compose(createdUser ->
                // Call Keycloak to set organization details
                keycloakUserService.setOrganisationDetails(
                  userId,
                  orgId,
                  organization.orgName()
                )
              )
              .compose(success -> {
                if (!success) {
                  return Future.failedFuture("Failed to set organization details in Keycloak");
                }
                return Future.succeededFuture(true);
              });
          });
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


    Map<String, Object> updateDataMap = Map.of(
      Constants.ROLE, role.getRoleName()
    );

    return orgUserDAO.update(conditionMap, updateDataMap).map(
      true
    ).recover(err -> {
      BaseDxException dxEx = BaseDxException.from(err);
      if (dxEx instanceof NoRowFoundException) {
        return Future.failedFuture(
          new DxNotFoundException("No matching user found in organization", dxEx)
        );
      }
      return Future.failedFuture(dxEx);
    });
  }

  @Override
  public Future<Boolean> isOrgAdmin(UUID orgid, UUID userid) {
    return orgUserDAO.isOrgAdmin(orgid, userid);
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
      .recover(err -> {
        BaseDxException dxEx = BaseDxException.from(err);
        if (dxEx instanceof NoRowFoundException) {
          return Future.failedFuture(
            new DxNotFoundException("No request found with given ID", dxEx)
          );
        }
        return Future.failedFuture(dxEx);
      });
  }

  //TODO need to create another one
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

  public Future<Boolean> hasPendingProviderRole(UUID userId, UUID orgId){
    Map<String, Object> filterMap = Map.of(
      Constants.STATUS, Status.PENDING.getStatus(), USER_ID, userId.toString(), Constants.ORGANIZATION_ID, orgId.toString()
    );

    return providerRequestDAO.getAllWithFilters(filterMap)
      .map(list -> !list.isEmpty());
  }

  public Future<List<OrganizationJoinRequest>> getOrganizationJoinRequestsByUser(UUID userId){
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

  public Future<List<OrganizationCreateRequest>> getOrganizationCreateRequestsByUserId(UUID userId){

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
                .recover(err -> {
                  BaseDxException dxEx = BaseDxException.from(err);
                  if (dxEx instanceof NoRowFoundException) {
                    return Future.failedFuture(
                      new DxNotFoundException("No User found in Keycloak", dxEx)
                    );
                  }
                  return Future.failedFuture(dxEx);
                });
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
          }).recover(err -> {
            BaseDxException dxEx = BaseDxException.from(err);
            if (dxEx instanceof NoRowFoundException) {
              return Future.failedFuture(
                new DxNotFoundException("No Request or  user found in organization", dxEx)
              );
            }
            return Future.failedFuture(dxEx);
          });
      }
    });
  }

  @Override
  public Future<List<OrganizationUser>> getOrganisationAdminId(UUID orgId)
  {
    Map<String,Object> conditonMap = Map.of(
      Constants.ORGANIZATION_ID ,orgId.toString(),
      Constants.ROLE , Role.ADMIN.getRoleName());

    return orgUserDAO.getAllWithFilters(conditonMap);
  }

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
  public Future<Boolean> deleteOrganizationRequestById(UUID requestId) {
    return orgDAO.get(requestId)
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

  @Override
  public Future<Boolean> deleteOrganizationJoinRequestById(UUID requestId) {
    return orgDAO.get(requestId)
      .compose(request -> {
        if (request == null) {
          return Future.failedFuture(
            new DxNotFoundException("Join organisation request not found for ID: " + requestId));
        }

        return organizationJoinRequestDAO.delete(requestId)
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



}

