package org.cdpg.dx.aaa.organization.handler;


import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.audit.util.AuditingHelper;
import org.cdpg.dx.aaa.credit.service.CreditService;
import org.cdpg.dx.aaa.delegation.service.DelegationService;
import org.cdpg.dx.aaa.email.util.EmailComposer;
import org.cdpg.dx.aaa.organization.models.*;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.aaa.organization.util.ProviderRoleRequestMapper;
import org.cdpg.dx.aaa.orgReport.service.OrganizationCreateReportService;
import org.cdpg.dx.aaa.user.service.UserService;
import org.cdpg.dx.auditing.model.AuditLog;
import org.cdpg.dx.auth.authorization.model.DxScope;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.exception.DxConflictException;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.request.PaginationRequestBuilder;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.common.util.PaginationInfo;
import org.cdpg.dx.common.util.RequestHelper;
import org.cdpg.dx.common.util.RoutingContextHelper;
import org.cdpg.dx.keycloak.config.KeycloakConstants;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.cdpg.dx.aaa.credit.util.Constants.ALLOWED_FILTER_MAP_FOR_CREDIT_REQUEST;
import static org.cdpg.dx.aaa.credit.util.Constants.API_TO_DB_CREDIT_REQUEST;
import static org.cdpg.dx.aaa.organization.config.Constants.*;
import static org.cdpg.dx.database.postgres.util.Constants.DEFAULT_SORTING_ORDER;


public class OrganizationHandler {

  private static final Logger LOGGER = LogManager.getLogger(OrganizationHandler.class);
  private final OrganizationService organizationService;
  private final UserService userService;
  private final EmailComposer emailComposer;
  private final OrganizationCreateReportService organizationCreateReportService;
  private final KeycloakUserService keycloakUserService;
  private final CreditService creditService;
  private final URNGenerator urnGenerator;
  private final DelegationService delegationService;


  public OrganizationHandler(OrganizationService organizationService, UserService userService , EmailComposer emailComposer, OrganizationCreateReportService organizationCreateReportService,CreditService creditService, KeycloakUserService keycloakUserService,URNGenerator urnGenerator,DelegationService delegationService) {
    this.organizationService = organizationService;
    this.userService = userService;
    this.emailComposer = emailComposer;
    this.organizationCreateReportService = organizationCreateReportService;
    this.creditService = creditService;
    this.keycloakUserService = keycloakUserService;
    this.urnGenerator = urnGenerator;
    this.delegationService = delegationService;
  }

  public Future<Boolean> validateEntityId(UUID delegatorId,UUID orgId)
  {

    return delegationService.getDelegationScopeByEntityId(orgId)
      .compose(scopeConstraints -> {

        boolean exists = scopeConstraints.stream()
          .anyMatch(scopeConstraint ->
            "org_management".equalsIgnoreCase(scopeConstraint.scope()) &&
              orgId.equals(scopeConstraint.entityId())
          );

        return Future.succeededFuture(exists);
      });
  }

  public Future<Void> verifyUserBelongsToOrg(UUID userId, UUID orgId) {

    return organizationService.getOrganisationUserByUserId(userId)
      .compose(orgUser -> {

        if (orgUser == null) {
          return Future.failedFuture(new DxNotFoundException(
            "User does not belong to any organisation"
          ));
        }

        if (!orgUser.organizationId().equals(orgId)) {
          return Future.failedFuture(new DxForbiddenException(
            "User does not belong to this organisation"
          ));
        }

        return Future.succeededFuture();
      });
  }
  public Future<Void> validateDelegatedAccess(UUID requesterId, UUID orgId, DxScope requiredScope) {

    return userService.getUserInfoByID(requesterId)
      .compose(dxUser -> {

        List<String> roles = dxUser.roles();

        // Case 1: org_admin / cos_admin fully allowed
        if (roles.contains("org_admin") || roles.contains("cos_admin")) {
          return Future.succeededFuture();
        }

        // Case 2: Delegate
        if (roles.contains("delegate")) {

          JsonArray delegationScopes =
            dxUser.scopes().getJsonArray("delegation_scope", new JsonArray());

          // Scope check
          if (!delegationScopes.contains(requiredScope.getScope())) {
            return Future.failedFuture(
              new DxForbiddenException("User does not have required delegation scope: " + requiredScope)
            );
          }

          UUID delegatorId = UUID.fromString(dxUser.did());

          // Case 2A: orgId is NOT provided → fetch delegator's org
          if (orgId == null) {

            return keycloakUserService.getUserById(delegatorId)
              .compose(delegatorUser -> {

                UUID derivedOrgId = UUID.fromString(delegatorUser.organisationId());

                if (derivedOrgId == null) {
                  return Future.failedFuture(
                    new DxForbiddenException("Delegator does not belong to any organisation")
                  );
                }

                // Validate delegator’s access
                return validateEntityId(delegatorId, derivedOrgId)
                  .compose(hasAccess -> {
                    if (!hasAccess) {
                      return Future.failedFuture(
                        new DxForbiddenException("Delegator does not have access to this organisation")
                      );
                    }
                    return Future.succeededFuture();
                  });
              });
          }

          // Case 2B: orgId is provided → validate delegator for that org
          return validateEntityId(delegatorId, orgId)
            .compose(hasAccess -> {
              if (!hasAccess) {
                return Future.failedFuture(
                  new DxForbiddenException("Delegator does not have access to this organisation")
                );
              }
              return Future.succeededFuture();
            });
        }

        // Case 3: neither admin nor delegate
        return Future.failedFuture(
          new DxForbiddenException("User is neither admin nor delegate")
        );
      });
  }




  public void updateOrganisationById(RoutingContext ctx) {

    // updates org
    // delegate requirement: scope - org_management and delegator is cos_admin
    // check if request param and delegated org id is same

    User user = ctx.user();
    UUID userId = UUID.fromString(user.subject());
    UUID orgId = RequestHelper.getPathParamAsUUID(ctx, "id");

    userService.getUserInfoByID(userId)
      .compose(dxuser -> {

        UUID delegatorId = null;

        // If user is a delegate (but not cos_admin)
        if (dxuser.roles().contains("delegate") && !dxuser.roles().contains("cos_admin")) {

          JsonObject scopesObj = dxuser.scopes();
          JsonArray delegationScopes = scopesObj.getJsonArray("delegation_scope");

          if (!delegationScopes.contains("org_management")) {
            return Future.failedFuture(new DxForbiddenException(
              "This user doesn't have the scope to do this !"));
          }
          delegatorId = UUID.fromString(dxuser.did());

          return validateEntityId(delegatorId, orgId)
            .compose(hasAccess -> {
              if (!hasAccess) {
                return Future.failedFuture(
                  new DxForbiddenException("This user doesn't have delegation access to the organisation")
                );
              }

              return Future.succeededFuture().mapEmpty();
            });
        }

          UpdateOrgDTO updateOrgDTO = RequestHelper.parseBody(ctx, UpdateOrgDTO::fromJson);
          return organizationService.updateOrganizationById(orgId, updateOrgDTO);

      })
      .onSuccess(updatedOrg ->{
        AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
          RoutingContextHelper.getRequestPath(ctx), "PUT", "Update Organization By ID");
        RoutingContextHelper.setAuditingLog(ctx, auditLog);
        ResponseBuilder.sendSuccess(ctx, updatedOrg,urnGenerator);
      })
      .onFailure(err -> {
        LOGGER.error("Failed to Update Organization id: {}, message: {}", orgId, err.getMessage(), err);
        ctx.fail(err);
      });
  }

  public void deleteOrganisationById(RoutingContext ctx) {

    // deletes org
    // delegate requirement: scope - org_management and delegator is cos_admin/org_admin
    // delegation table has the org_id
    // check if request param and delegated org id is same

    User user = ctx.user();
    UUID userId = UUID.fromString(user.subject());
    UUID orgId = RequestHelper.getPathParamAsUUID(ctx, "id");

    userService.getUserInfoByID(userId)
      .compose(dxuser -> {
        UUID delegatorId = null;

        // If user is a delegate (but not cos_admin)
        if (dxuser.roles().contains("delegate") && !dxuser.roles().contains("cos_admin") && !dxuser.roles().contains("org_admin")) {

          JsonObject scopesObj = dxuser.scopes();
          JsonArray delegationScopes = scopesObj.getJsonArray("delegation_scope");

          if (!delegationScopes.contains("org_management")) {
            return Future.failedFuture(new DxForbiddenException(
              "This user doesn't have the scope to do this !"
            ));
          }
          delegatorId = UUID.fromString(dxuser.did());

//          ctx.queryParams().set("requestedBy", delegatorId.toString());
//          LOGGER.debug("Injected 'requestedBy' into query params: {}", delegatorId);

          return validateEntityId(delegatorId, orgId)
            .compose(hasAccess -> {
              if (!hasAccess) {
                return Future.failedFuture(
                  new DxForbiddenException("This user doesn't have delegation access to the organisation"));
              }
              return Future.succeededFuture().mapEmpty();
            });
        }
        return organizationService.deleteOrganization(orgId);
      })
      .onSuccess(res -> {
        AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
          RoutingContextHelper.getRequestPath(ctx), "DELETE", "Delete Organization By ID");
        RoutingContextHelper.setAuditingLog(ctx, auditLog);
        ResponseBuilder.sendSuccess(ctx, "Organisation deleted Successfully!",urnGenerator);
      })
      .onFailure(ctx::fail);
  }



  public void listAllOrganisations(RoutingContext ctx) {
    PaginatedRequest request = PaginationRequestBuilder.from(ctx)
      .allowedFiltersDbMap(ALLOWED_FILTER_MAP_FOR_ORG)
      .apiToDbMap(API_TO_DB_ORG)
      .allowedTimeFields(Set.of(CREATED_AT))
      .defaultTimeField(CREATED_AT)
      .defaultSort(CREATED_AT, DEFAULT_SORTING_ORDER)
      .allowedSortFields(API_TO_DB_ORG.keySet())
      .build();


    organizationService.getOrganizations(request)
      .onSuccess(orgs -> {
        AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
          RoutingContextHelper.getRequestPath(ctx), "GET", "List All Organisations");
        RoutingContextHelper.setAuditingLog(ctx, auditLog);
        ResponseBuilder.sendSuccess(ctx, orgs.data().stream()
          .map(Organization::toFilteredJson).collect(Collectors.toList()), orgs.paginationInfo(),urnGenerator);

      })
      .onFailure(ctx::fail);

  }

  public void approveJoinOrganisationRequests(RoutingContext ctx) {

    JsonObject orgRequestJson = ctx.body().asJsonObject();
    User user = ctx.user();
    UUID userId = UUID.fromString(user.subject());
    UUID reqId = RequestHelper.getPathParamAsUUID(ctx, "req_id");

    Status status = Status.fromString(orgRequestJson.getString("status"));

    userService.getUserInfoByID(userId)
      .compose(dxuser -> {

        // Case 1 — Delegate (not org_admin)
        if (dxuser.roles().contains("delegate") && !dxuser.roles().contains("org_admin")) {

          JsonArray delegationScopes = dxuser.scopes().getJsonArray("delegation_scope");

          if (!delegationScopes.contains("org_management")) {
            return Future.failedFuture(
              new DxForbiddenException("This user doesn't have the scope to do this !")
            );
          }

          UUID delegatorId = UUID.fromString(dxuser.did());

          // Get orgId from the join request (org of the request being approved)
          return organizationService.getOrganizationJoinRequestById(reqId)
            .compose(joinReq -> {
              if (joinReq == null) {
                return Future.failedFuture(new DxNotFoundException("Request Not Found"));
              }

              UUID orgIdFromRequest = joinReq.organizationId();

              // Delegate → verify delegator is admin of this organisation
              return validateEntityId(delegatorId, orgIdFromRequest)
                .compose(hasAccess -> {
                  if (!hasAccess) {
                    return Future.failedFuture(
                      new DxForbiddenException("This user doesn't have delegation access to the organisation")
                    );
                  }

                  return organizationService.updateOrganizationJoinRequestStatus(reqId, status);
                });
            });
        }

        // Case 2 — org_admin
        return organizationService.updateOrganizationJoinRequestStatus(reqId, status);
      })
      .onSuccess(approved -> {
        if (approved) {
          AuditLog auditLog = AuditingHelper.createAuditLog(
            ctx.user(),
            RoutingContextHelper.getRequestPath(ctx),
            "PUT",
            "Approved Join Request"
          );
          RoutingContextHelper.setAuditingLog(ctx, auditLog);

          ResponseBuilder.sendSuccess(ctx, "Updated Organisation Join Request", urnGenerator);

          // Fire and forget email
          emailComposer.sendUserEmailForOrgJoinRequestApproval(reqId, status);
        } else {
          ctx.fail(new DxNotFoundException("Request Not Found"));
        }
      })
      .onFailure(ctx::fail);
  }

  public void getJoinOrganisationRequests(RoutingContext ctx) {

    // gets org_join_req
    // delegate requirement: scope - org_management and delegator is org_admin
    // delegation table has the org_id

    User user = ctx.user();
    UUID userId = UUID.fromString(user.subject());
    UUID orgId = RequestHelper.getPathParamAsUUID(ctx, "id");

    userService.getUserInfoByID(userId)
      .compose(dxuser -> {

        UUID delegatorId = null;

        // If user is a delegate (but not cos_admin)
        if (dxuser.roles().contains("delegate") && !dxuser.roles().contains("org_admin")) {

          JsonObject scopesObj = dxuser.scopes();
          JsonArray delegationScopes = scopesObj.getJsonArray("delegation_scope");

          if (!delegationScopes.contains("org_management")) {
            return Future.failedFuture(new DxForbiddenException(
              "This user doesn't have the scope to do this !"
            ));
          }

          delegatorId = UUID.fromString(dxuser.did());

          return validateEntityId(delegatorId, orgId)
            .compose(hasAccess -> {
              if (!hasAccess) {
                return Future.failedFuture(
                  new DxForbiddenException("This user doesn't have delegation access to the organisation"));
              }

              ctx.queryParams().set("organizationId", orgId.toString());

              PaginatedRequest request = PaginationRequestBuilder.from(ctx)
                .allowedFiltersDbMap(ALLOWED_FILTER_MAP_FOR_ORG_JOIN_REQUEST)
                .apiToDbMap(API_TO_DB_ORG_JOIN_REQUEST)
                .additionalFilters(Map.of(ORGANIZATION_ID, orgId.toString()))
                .allowedTimeFields(Set.of(REQUESTED_AT))
                .defaultTimeField(REQUESTED_AT)
                .defaultSort(REQUESTED_AT, DEFAULT_SORTING_ORDER)
                .allowedSortFields(API_TO_DB_ORG_JOIN_REQUEST.keySet())
                .build();

              LOGGER.info("Delegate: Pagination request info : {}", request);

              return organizationService.getOrganizationPendingJoinRequests(request);
            });
        }

        // Case 2 — org_admin / cos_admin / others
        PaginatedRequest request = PaginationRequestBuilder.from(ctx)
          .allowedFiltersDbMap(ALLOWED_FILTER_MAP_FOR_ORG_JOIN_REQUEST)
          .apiToDbMap(API_TO_DB_ORG_JOIN_REQUEST)
          .additionalFilters(Map.of(ORGANIZATION_ID, orgId.toString()))
          .allowedTimeFields(Set.of(REQUESTED_AT))
          .defaultTimeField(REQUESTED_AT)
          .defaultSort(REQUESTED_AT, DEFAULT_SORTING_ORDER)
          .allowedSortFields(API_TO_DB_ORG_JOIN_REQUEST.keySet())
          .build();

        LOGGER.info("Admin: Pagination request info : {}", request);

        return organizationService.getOrganizationPendingJoinRequests(request);
      })
      .compose(result ->
        userService.enrichWithUserRoles(
          result.data(),
          OrganizationJoinRequest::userId,
          OrganizationJoinRequest::toJson
        ).map(enrichedList -> Map.entry(enrichedList, result.paginationInfo()))
      )
      .onSuccess(entry -> {
        AuditLog auditLog = AuditingHelper.createAuditLog(
          ctx.user(), RoutingContextHelper.getRequestPath(ctx),
          "GET", "Get Pending Join Requests"
        );
        RoutingContextHelper.setAuditingLog(ctx, auditLog);

        ResponseBuilder.sendSuccess(ctx, entry.getKey(), entry.getValue(), urnGenerator);
      })
      .onFailure(ctx::fail);
  }

  public void joinOrganisationRequest(RoutingContext ctx) {

    UUID orgId = RequestHelper.getPathParamAsUUID(ctx, "id");
    JsonObject OrgRequestJson = ctx.body().asJsonObject();
    OrganizationJoinRequest organizationJoinRequest;
    User user = ctx.user();
    OrgRequestJson.put("user_id", user.subject());

    String userName = user.principal().getString("name");
    OrgRequestJson.put("user_name", userName);
    OrgRequestJson.put("organization_id", orgId.toString());

    System.out.println("OrgRequestJson: " + OrgRequestJson.encodePrettily());

    organizationJoinRequest = OrganizationJoinRequest.fromJson(OrgRequestJson);


    organizationService.getOrganizationJoinRequestsByUser(UUID.fromString(user.subject()))
      .compose(joinRequests -> {
        for (OrganizationJoinRequest request : joinRequests) {

          if (request.organizationId().equals(orgId)) {
            return Future.failedFuture(new DxConflictException("User already has a pending/ granted join request for this organization"));
          }
        }
        return organizationService.joinOrganizationRequest(organizationJoinRequest)
          .onSuccess(createdRequest -> {
            AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
              RoutingContextHelper.getRequestPath(ctx), "POST", "Create Join Organization Request");
            RoutingContextHelper.setAuditingLog(ctx, auditLog);
            ResponseBuilder.sendSuccess(ctx, "Created Join request",urnGenerator);
            Future<Void> future = emailComposer.sendEmailForJoiningOrg(organizationJoinRequest,user);

          })
          .onFailure(ctx::fail);
      })
      .onFailure(ctx::fail);

  }

  public void approveOrganisationRequest(RoutingContext ctx) {

    // approve org_create_req
    // delegate requirement: scope - org_management and delegator is cos_admin

    JsonObject OrgRequestJson = ctx.body().asJsonObject();

    UUID requestId = UUID.fromString(OrgRequestJson.getString("req_id"));
    Status status = Status.fromString(OrgRequestJson.getString("status"));

    JsonObject responseObject = OrgRequestJson.copy();
    responseObject.remove("status");

    User user = ctx.user();
    UUID userId = UUID.fromString(user.subject());

    userService.getUserInfoByID(userId)
      .compose(dxuser -> {
          UUID delegatorId = null;

          // If user is a delegate (but not cos_admin)
          if (dxuser.roles().contains("delegate") && !dxuser.roles().contains("cos_admin")) {

            JsonObject scopesObj = dxuser.scopes();
            JsonArray delegationScopes = scopesObj.getJsonArray("delegation_scope");

            if (!delegationScopes.contains("org_management")) {
              return Future.failedFuture(new DxForbiddenException(
                "This user doesn't have the scope to do this !"
              ));
            }
          }

          return Future.succeededFuture().mapEmpty();
        });

    organizationService.updateOrganizationCreateRequestStatus(requestId, status)
      .onSuccess(updated -> {
        AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
          RoutingContextHelper.getRequestPath(ctx), "PUT", "Approve Create Organisation Request");
        RoutingContextHelper.setAuditingLog(ctx, auditLog);
        ResponseBuilder.sendSuccess(ctx, "Updated Sucessfully",urnGenerator);
        Future<Void> future = emailComposer.sendUserEmailForOrgCreateRequestApproval(requestId,status);

      })
      .onFailure(ctx::fail);
  }



  public void getOrganisationRequest(RoutingContext ctx) {

    // get org create requests
    // delegate requirement: scope - org_management and delegator is cos_admin

    User authUser = ctx.user();
    UUID requesterId = UUID.fromString(authUser.subject());

    userService.getUserInfoByID(requesterId)
      .compose(dxuser -> {

        // If requester is not a delegate, OR is cos_admin → no filtering required
        if (!dxuser.roles().contains("delegate") || dxuser.roles().contains("cos_admin")) {
          return Future.succeededFuture();
        }

        // Delegate but not cos_admin → validate scope first
        if (!dxuser.scopes().getJsonArray("delegation_scope").contains("org_management")) {
          return Future.failedFuture(
            new DxForbiddenException("This user doesn't have the scope to do this!")
          );
        }

        // Inject delegator-based filter
        UUID delegatorId = UUID.fromString(dxuser.did());
        ctx.queryParams().set("requestedBy", delegatorId.toString());
        LOGGER.debug("Injected delegated filter: requestedBy={}", delegatorId);

        return Future.succeededFuture();
      })
      .compose(v -> {

        PaginatedRequest request = PaginationRequestBuilder.from(ctx)
          .allowedFiltersDbMap(ALLOWED_FILTER_MAP_FOR_ORG_CREATE_REQUEST)
          .apiToDbMap(API_TO_DB_ORG_CREATE_REQUEST)
          .allowedTimeFields(Set.of(CREATED_AT))
          .defaultTimeField(CREATED_AT)
          .defaultSort(CREATED_AT, DEFAULT_SORTING_ORDER)
          .allowedSortFields(API_TO_DB_ORG_CREATE_REQUEST.keySet())
          .build();

        return organizationService.getAllOrganizationCreateRequests(request);
      })
      .onSuccess(res -> {
        AuditLog auditLog = AuditingHelper.createAuditLog(
          ctx.user(),
          RoutingContextHelper.getRequestPath(ctx),
          "GET",
          "Get All Organisation Requests"
        );
        RoutingContextHelper.setAuditingLog(ctx, auditLog);
        ResponseBuilder.sendSuccess(ctx, res.data(), res.paginationInfo(), urnGenerator);
      })
      .onFailure(ctx::fail);
  }

  public void createOrganisationRequest(RoutingContext ctx) {
    JsonObject OrgRequestJson = ctx.body().asJsonObject();

    User user = ctx.user();

    OrgRequestJson.put("requested_by", user.subject());
    OrgRequestJson.put("user_name", user.principal().getString("name"));
    String orgName = OrgRequestJson.getString("name");

    OrganizationCreateRequest organizationCreateRequest = OrganizationCreateRequest.fromJson(OrgRequestJson);

    organizationService.getOrganizationCreateRequestsByUserId(UUID.fromString(user.subject()))
      .compose(createRequests -> {
        for (OrganizationCreateRequest request : createRequests) {
          if (request.requestedBy().equals(UUID.fromString(user.subject()))) {
            return Future.failedFuture(new DxConflictException("Organisation create request already granted/ pending for this user"));
          }
        }
        return organizationService.getAllPendingGrantedOrganizationCreateRequests()
          .compose(requests -> {
            for (OrganizationCreateRequest request : requests) {
              if (request.name().equalsIgnoreCase(orgName)) {
                return Future.failedFuture(new DxConflictException("Organisation name already exists/ under review"));
              }
            }
            return organizationService.getAllPendingGrantedOrganizationCreateRequests()
              .compose(pendingRequests -> {
                for (OrganizationCreateRequest request : requests) {
                  if (request.managerEmail().equalsIgnoreCase(organizationCreateRequest.managerEmail())) {
                    return Future.failedFuture(new DxConflictException("Manager email is already in use for another organisation request"));
                  }
                }
                return organizationService.createOrganizationRequest(organizationCreateRequest);
              }).onFailure(ctx::fail);
          }).onFailure(ctx::fail);

      })
      .onSuccess(requests -> {
        AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
          RoutingContextHelper.getRequestPath(ctx), "POST", "Create Organisation Request");
        RoutingContextHelper.setAuditingLog(ctx, auditLog);
        ResponseBuilder.sendSuccess(ctx, requests,urnGenerator);
        emailComposer.sendEmailForCreatingOrg(organizationCreateRequest, user);
      })
      .onFailure(ctx::fail);
  }

  public void deleteOrganisationUserById(RoutingContext ctx) {


    // deletes org_user
    // delegate requirement: scope - org_management and delegator is org_admin or cos_admin
    // delegation table has the org_id
    // get actual org admin id from the delegation table

    UUID orgId = RequestHelper.getPathParamAsUUID(ctx, "id");
    UUID userId = RequestHelper.getPathParamAsUUID(ctx, "user_id");

    if (orgId == null || userId == null) {
      ctx.fail(new DxNotFoundException("Organization ID or User ID is missing"));
      return;
    }

    UUID orgAdminId = UUID.fromString(ctx.user().subject());

    userService.getUserInfoByID(userId)
      .compose(user -> {
        if (user == null) {
          return Future.failedFuture(new DxNotFoundException("User not found"));
        }

        if (user.roles().contains(KeycloakConstants.ORG_ADMIN_ROLE)) {
          return Future.failedFuture(new DxBadRequestException("Cannot delete admin user"));
        }

        Future<Boolean> deletionFuture;
        if (user.roles().contains("provider")) {
          deletionFuture = organizationService.deleteProviderUser(userId, orgAdminId, orgId);
        } else {
          deletionFuture = organizationService.deleteOrganizationUser(orgId, userId);
        }

        return deletionFuture.compose(deleted -> {
          if (!deleted) {
            return Future.failedFuture(new DxNotFoundException("User not found in organization"));
          }
          return organizationService.deleteOrganizationJoinRequest(orgId, userId)
            .recover(err -> {
              LOGGER.warn("Failed to delete join request: {}", err.getMessage());
              return Future.succeededFuture();
            })
            .compose(v -> organizationService.deleteProviderRoleRequest(orgId, userId)
              .recover(err -> {
                LOGGER.warn("Failed to delete provider role request: {}", err.getMessage());
                return Future.succeededFuture();
              })
            )
            .onSuccess(v -> {
              LOGGER.info("User {} deleted completely from Organization {}", userId, orgId);
              AuditLog auditLog = AuditingHelper.createAuditLog(
                ctx.user(), RoutingContextHelper.getRequestPath(ctx), "DELETE", "Deleted User with Cleanup"
              );
              RoutingContextHelper.setAuditingLog(ctx, auditLog);
              ResponseBuilder.sendSuccess(ctx, "User deleted successfully from DB and Keycloak",urnGenerator);
            })
            .onFailure(ctx::fail);
        });
      });
  }


  public void getOrganisationUserInfo(RoutingContext ctx) {

    // gets org_user info
    // delegate requirement: scope - org_management and delegator is org_admin or cos_admin
    // delegation table has the org_id

    UUID orgId = RequestHelper.getPathParamAsUUID(ctx, "id");
    UUID targetUserId = RequestHelper.getPathParamAsUUID(ctx, "user_id");

    UUID requesterId = UUID.fromString(ctx.user().subject());

    validateDelegatedAccess(requesterId, orgId, DxScope.ORG_MANAGEMENT)
      .compose(v -> verifyUserBelongsToOrg(targetUserId, orgId))
      .compose(v -> userService.getUserInfoByID(targetUserId))
      .onSuccess(userInfo -> {
        AuditLog auditLog = AuditingHelper.createAuditLog(
          ctx.user(),
          RoutingContextHelper.getRequestPath(ctx),
          "GET",
          "Get User Info By ID"
        );
        RoutingContextHelper.setAuditingLog(ctx, auditLog);
        ResponseBuilder.sendSuccess(ctx, userInfo, urnGenerator);
      })
      .onFailure(ctx::fail);
  }


  // gets org_user
  // delegate requirement: scope - org_management and delegator is org_admin
  // delegation table has the org_id
  public void getOrganisationUsers(RoutingContext ctx) {

    UUID orgId = RequestHelper.getPathParamAsUUID(ctx, "id");
    UUID requesterId = UUID.fromString(ctx.user().subject());

    // Validate delegated access before doing anything else
    validateDelegatedAccess(requesterId, orgId, DxScope.ORG_MANAGEMENT)
      .compose(v -> {

        PaginatedRequest request = PaginationRequestBuilder.from(ctx)
          .allowedFiltersDbMap(ALLOWED_FILTER_MAP_FOR_ORG_USERS)
          .apiToDbMap(API_TO_DB_ORG_USERS)
          .additionalFilters(Map.of(ORGANIZATION_ID, orgId.toString()))
          .allowedTimeFields(Set.of(CREATED_AT))
          .defaultTimeField(CREATED_AT)
          .defaultSort(CREATED_AT, DEFAULT_SORTING_ORDER)
          .allowedSortFields(API_TO_DB_ORG_USERS.keySet())
          .build();

        AuditLog auditLog = AuditingHelper.createAuditLog(
          ctx.user(),
          RoutingContextHelper.getRequestPath(ctx),
          "GET",
          "Get Organisation Users by OrgID"
        );

        return organizationService.getOrganizationUsers(request)
          .compose(res ->
            userService.enrichWithUserRoles(
              res.data(),
              OrganizationUser::userId,
              OrganizationUser::toJson
            ).map(enriched -> {

              RoutingContextHelper.setAuditingLog(ctx, auditLog);
              return Map.entry(enriched, res.paginationInfo());
            })
          );
      })
      .onSuccess(entry ->
        ResponseBuilder.sendSuccess(ctx, entry.getKey(), entry.getValue(), urnGenerator)
      )
      .onFailure(ctx::fail);
  }


  public void updateOrganisationUserRole(RoutingContext ctx) {

    // updates org_user role
    // delegate requirement: scope - org_management and delegator is org_admin or cos_admin
    // delegation table has org_id

    JsonObject OrgRequestJson = ctx.body().asJsonObject();

    Role role;
    role = Role.fromString(OrgRequestJson.getString("role"));

    UUID  orgId = RequestHelper.getPathParamAsUUID(ctx, "id");
    UUID userId = RequestHelper.getPathParamAsUUID(ctx, "user_id");

    organizationService.updateUserRole(orgId, userId, role)
      .onSuccess(updated -> {
        if (updated) {
          AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
            RoutingContextHelper.getRequestPath(ctx), "PUT", "Update Organisation User Role");
          RoutingContextHelper.setAuditingLog(ctx, auditLog);
          ResponseBuilder.sendSuccess(ctx,"Updated Organisation User Role",urnGenerator);

        } else {
          ctx.fail(new DxNotFoundException( "Organisation User Not Found"));
        }
      })
      .onFailure(ctx::fail);;

  }

  public void createProviderRequest(RoutingContext ctx) {

    User user = ctx.user();
    LOGGER.debug("User: {}", user);
    if (user == null || user.subject() == null || user.principal() == null) {
      ctx.fail(new DxForbiddenException("User not found"));
      return;
    }

    String userId = user.subject();
    String orgID = user.principal().getString("organisation_id");

    if (userId == null || userId.isEmpty()) {
      ctx.fail(new DxForbiddenException("User not found"));
      return;
    }

    if (orgID == null || orgID.isEmpty()) {
      ctx.fail(new DxForbiddenException("User is not part any organisation"));
      return;
    }

    JsonObject req = new JsonObject().
      put("user_id", user.subject()).
      put("organization_id", orgID);

    ProviderRoleRequest providerRoleRequest = ProviderRoleRequest.fromJson(req);

    organizationService.createProviderRequest(providerRoleRequest)
      .onSuccess(requests -> {
        AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
          RoutingContextHelper.getRequestPath(ctx), "POST", "Create Provider Role Request");
        RoutingContextHelper.setAuditingLog(ctx, auditLog);
        ResponseBuilder.sendSuccess(ctx, "Created Request",urnGenerator);
        Future<Void> future = emailComposer.sendEmailForProviderRole(providerRoleRequest,user);

      })
      .onFailure(ctx::fail);
  }

  public void updateProviderRequest(RoutingContext ctx) {

    // updates provider requests
    // delegate requirement: scope - provider_management and delegator is org_admin or cos_admin
    // optional : delegation table has provider_req for that user/ the org id of the delegation and provider req org id match

    JsonObject OrgRequestJson = ctx.body().asJsonObject();
    UUID reqId = RequestHelper.getPathParamAsUUID(ctx, "id");
    Status status = Status.fromString(OrgRequestJson.getString("status"));


    organizationService.updateProviderRequestStatus(reqId,status)
      .onSuccess(requests -> {
        AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
          RoutingContextHelper.getRequestPath(ctx), "PUT", "Update Provider Role Request");
        RoutingContextHelper.setAuditingLog(ctx, auditLog);
        ResponseBuilder.sendSuccess(ctx, "Provider role updated",urnGenerator);
        Future<Void> future = emailComposer.sendUserEmailForProviderRoleApproval(reqId,status);

      })
      .onFailure(ctx::fail);
  }

  public void getProviderRequest(RoutingContext ctx) {

    User user = ctx.user();
    if (user == null || user.subject() == null) {
      ctx.fail(new DxForbiddenException("User not found"));
      return;
    }

    UUID requesterId = UUID.fromString(user.subject());

    validateDelegatedAccess(requesterId, null, DxScope.ORG_MANAGEMENT)
      .compose(v -> keycloakUserService.getUserById(requesterId))
      .compose(dxUser -> {

        // Case 1: requester already has an organization
        if (dxUser.organisationId() != null && !dxUser.organisationId().isEmpty()) {
          return Future.succeededFuture(UUID.fromString(dxUser.organisationId()));
        }

        // Case 2: requester is a delegate → get delegator’s organisation from Keycloak
        UUID delegatorId = UUID.fromString(dxUser.did());

        return keycloakUserService.getUserById(delegatorId).compose(delegatorUser -> {
          if (delegatorUser.organisationId() == null || delegatorUser.organisationId().isEmpty()) {
            return Future.failedFuture(
              new DxForbiddenException("Delegator does not belong to any organisation")
            );
          }

          UUID orgId = UUID.fromString(delegatorUser.organisationId());
          return Future.succeededFuture(orgId);
        });
      })
      .compose(orgId -> {

        ctx.queryParams().set("organization_id", orgId.toString());

        PaginatedRequest request = PaginationRequestBuilder.from(ctx)
          .allowedFiltersDbMap(ALLOWED_FILTER_MAP_FOR_PROVIDER_ROLE_REQUEST)
          .apiToDbMap(API_TO_DB_PROVIDER_ROLE_REQUEST)
          .additionalFilters(Map.of(ORGANIZATION_ID, orgId.toString()))
          .allowedTimeFields(Set.of(CREATED_AT))
          .defaultTimeField(CREATED_AT)
          .defaultSort(CREATED_AT, DEFAULT_SORTING_ORDER)
          .allowedSortFields(API_TO_DB_PROVIDER_ROLE_REQUEST.keySet())
          .build();

        return organizationService.getAllPendingProviderRoleRequests(request);
      })
      .compose(requests ->
        userService.enrichWithUserRoles(
          requests.data(),
          ProviderRoleRequest::userId,
          ProviderRoleRequest::toJson
        ).map(enriched -> Map.entry(enriched, requests.paginationInfo()))
      )
      .onSuccess(entry -> {

        AuditLog auditLog = AuditingHelper.createAuditLog(
          ctx.user(),
          RoutingContextHelper.getRequestPath(ctx),
          "GET",
          "Get Provider Role Requests"
        );

        RoutingContextHelper.setAuditingLog(ctx, auditLog);

        ResponseBuilder.sendSuccess(
          ctx,
          entry.getKey(),
          entry.getValue(),
          urnGenerator
        );
      })
      .onFailure(ctx::fail);
  }



  public void createProviderRole(RoutingContext ctx) {
    JsonObject providerRequestJson = ctx.body().asJsonObject();

    ProviderRoleRequest providerRoleRequest = ProviderRoleRequest.fromJson(providerRequestJson);

    organizationService.createProviderRole(providerRoleRequest)
      .onSuccess(org -> ResponseBuilder.sendSuccess(ctx, "Provider role granted successfully",urnGenerator))
      .onFailure(err -> {
        if (err instanceof DxForbiddenException) {
          ctx.fail(new DxForbiddenException("User is not part of any organisation or does not have permission to grant provider role"));
        } else if (err instanceof DxNotFoundException) {
          ctx.fail(new DxNotFoundException("User not found or does not have a pending provider role request"));
        } else {
          ctx.fail(err);
        }
      });

  }

  public void getOrganizationById(RoutingContext ctx) {
    UUID orgId = RequestHelper.getPathParamAsUUID(ctx, "id");

    organizationService.getOrganizationById(orgId)
      .onSuccess(org -> {
        AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
          RoutingContextHelper.getRequestPath(ctx), "GET", "Get Organization By ID");
        RoutingContextHelper.setAuditingLog(ctx, auditLog);
        ResponseBuilder.sendSuccess(ctx, org.toJson(),urnGenerator);
      })
      .onFailure(err -> {
        if (err instanceof DxNotFoundException) {
          ctx.fail(new DxNotFoundException("Organization not found with id: " + orgId));
        } else {
          ctx.fail(err);
        }
      });
  }

  public void getOrganizationCreateReport(RoutingContext ctx) {
    HttpServerResponse response = ctx.response();
    response
      .putHeader("Access-Control-Allow-Origin", "*")
      .putHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
      .putHeader("Access-Control-Allow-Methods", "GET, POST,PUT, DELETE, OPTIONS")
      .putHeader("Content-Type", "text/csv")
      .putHeader("Content-Disposition", "attachment; filename=\"org_create_request_report.csv\"")
      .setChunked(true);

    PaginatedRequest request = PaginationRequestBuilder.from(ctx)
      .allowedFiltersDbMap(ALLOWED_FILTER_MAP_FOR_ORG_CREATE_REQUEST)
      .apiToDbMap(API_TO_DB_ORG_CREATE_REQUEST)
      .allowedTimeFields(Set.of(CREATED_AT))
      .defaultTimeField(CREATED_AT)
      .defaultSort(CREATED_AT, DEFAULT_SORTING_ORDER)
      .allowedSortFields(API_TO_DB_ORG_CREATE_REQUEST.keySet())
      .build();

    organizationCreateReportService
      .streamAdminCsvBatchedCreateRequest(request)
      .onSuccess(
        csvStream -> {
          if (csvStream == null) {
            response.end();
            return;
          }
          csvStream
            .exceptionHandler(
              err -> {
                LOGGER.error("Failed to stream CSV", err);
                ctx.fail(err);
              })
            .handler(buffer -> response.write(buffer))
            .endHandler(v -> response.end());
        })
      .onFailure(
        err -> {
          LOGGER.error("Failed to stream CSV", err);
          ctx.fail(err);
        });
  }

  public void getOrganizationJoinReport(RoutingContext ctx) {
    HttpServerResponse response = ctx.response();
    UUID orgId = RequestHelper.getPathParamAsUUID(ctx, "id");

    response
      .putHeader("Access-Control-Allow-Origin", "*")
      .putHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
      .putHeader("Access-Control-Allow-Methods", "GET, POST,PUT, DELETE, OPTIONS")
      .putHeader("Content-Type", "text/csv")
      .putHeader("Content-Disposition", "attachment; filename=\"org_create_request_report.csv\"")
      .setChunked(true);

    PaginatedRequest request = PaginationRequestBuilder.from(ctx)
      .allowedFiltersDbMap(ALLOWED_FILTER_MAP_FOR_ORG_JOIN_REQUEST)
      .apiToDbMap(API_TO_DB_ORG_JOIN_REQUEST)
      .additionalFilters(Map.of(ORGANIZATION_ID, orgId.toString()))
      .allowedTimeFields(Set.of("requested_at"))
      .defaultTimeField("requested_at")
      .defaultSort("requested_at", DEFAULT_SORTING_ORDER)
      .allowedSortFields(API_TO_DB_ORG_JOIN_REQUEST.keySet())
      .build();

    organizationCreateReportService
      .streamAdminCsvBatchedJoinRequest(request)
      .onSuccess(
        csvStream -> {
          if (csvStream == null) {
            response.end();
            return;
          }
          csvStream
            .exceptionHandler(
              err -> {
                LOGGER.error("Failed to stream CSV", err);
                ctx.fail(err);
              })
            .handler(buffer -> response.write(buffer))
            .endHandler(v -> response.end());
        })
      .onFailure(
        err -> {
          LOGGER.error("Failed to stream CSV", err);
          ctx.fail(err);
        });
  }

  public void getOrganizationReport(RoutingContext ctx) {
    HttpServerResponse response = ctx.response();
    response
      .putHeader("Access-Control-Allow-Origin", "*")
      .putHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
      .putHeader("Access-Control-Allow-Methods", "GET, POST,PUT, DELETE, OPTIONS")
      .putHeader("Content-Type", "text/csv")
      .putHeader("Content-Disposition", "attachment; filename=\"org_create_request_report.csv\"")
      .setChunked(true);

    PaginatedRequest request = PaginationRequestBuilder.from(ctx)
      .allowedFiltersDbMap(ALLOWED_FILTER_MAP_FOR_ORG)
      .apiToDbMap(API_TO_DB_ORG_USERS)
      .allowedTimeFields(Set.of(CREATED_AT))
      .defaultTimeField(CREATED_AT)
      .defaultSort(CREATED_AT, DEFAULT_SORTING_ORDER)
      .allowedSortFields(API_TO_DB_ORG_USERS.keySet())
      .build();

    organizationCreateReportService
      .streamAdminCsvBatchedOrganization(request)
      .onSuccess(
        csvStream -> {
          if (csvStream == null) {
            response.end();
            return;
          }
          csvStream
            .exceptionHandler(
              err -> {
                LOGGER.error("Failed to stream CSV", err);
                ctx.fail(err);
              })
            .handler(buffer -> response.write(buffer))
            .endHandler(v -> response.end());
        })
      .onFailure(
        err -> {
          LOGGER.error("Failed to stream CSV", err);
          ctx.fail(err);
        });
  }

  public void getProviderRequestReport(RoutingContext ctx) {
    HttpServerResponse response = ctx.response();
    response
      .putHeader("Access-Control-Allow-Origin", "*")
      .putHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
      .putHeader("Access-Control-Allow-Methods", "GET, POST,PUT, DELETE, OPTIONS")
      .putHeader("Content-Type", "text/csv")
      .putHeader("Content-Disposition", "attachment; filename=\"org_create_request_report.csv\"")
      .setChunked(true);

    PaginatedRequest request = PaginationRequestBuilder.from(ctx)
      .allowedFiltersDbMap(ALLOWED_FILTER_MAP_FOR_PROVIDER_ROLE_REQUEST)
      .apiToDbMap(API_TO_DB_PROVIDER_ROLE_REQUEST)
      .allowedTimeFields(Set.of(CREATED_AT))
      .defaultTimeField(CREATED_AT)
      .defaultSort(CREATED_AT, DEFAULT_SORTING_ORDER)
      .allowedSortFields(API_TO_DB_PROVIDER_ROLE_REQUEST.keySet())
      .build();

    organizationCreateReportService
      .streamAdminCsvBatchedProviderRequest(request)
      .onSuccess(
        csvStream -> {
          if (csvStream == null) {
            response.end();
            return;
          }
          csvStream
            .exceptionHandler(
              err -> {
                LOGGER.error("Failed to stream CSV", err);
                ctx.fail(err);
              })
            .handler(buffer -> response.write(buffer))
            .endHandler(v -> response.end());
        })
      .onFailure(
        err -> {
          LOGGER.error("Failed to stream CSV", err);
          ctx.fail(err);
        });
  }

  public void getComputeRoleReport(RoutingContext ctx) {
    HttpServerResponse response = ctx.response();
    response
      .putHeader("Access-Control-Allow-Origin", "*")
      .putHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
      .putHeader("Access-Control-Allow-Methods", "GET, POST,PUT, DELETE, OPTIONS")
      .putHeader("Content-Type", "text/csv")
      .putHeader("Content-Disposition", "attachment; filename=\"org_create_request_report.csv\"")
      .setChunked(true);

    PaginatedRequest request = PaginationRequestBuilder.from(ctx)
      .allowedFiltersDbMap(ALLOWED_FILTER_MAP_FOR_COMPUTE_ROLE)
      .apiToDbMap(API_TO_DB_COMPUTE_ROLE_REQUEST)
      .allowedTimeFields(Set.of(CREATED_AT))
      .defaultTimeField(CREATED_AT)
      .defaultSort(CREATED_AT, DEFAULT_SORTING_ORDER)
      .allowedSortFields(API_TO_DB_COMPUTE_ROLE_REQUEST.keySet())
      .build();

    organizationCreateReportService
      .streamAdminCsvBatchedComputeRequest(request)
      .onSuccess(
        csvStream -> {
          if (csvStream == null) {
            response.end();
            return;
          }
          csvStream
            .exceptionHandler(
              err -> {
                LOGGER.error("Failed to stream CSV", err);
                ctx.fail(err);
              })
            .handler(buffer -> response.write(buffer))
            .endHandler(v -> response.end());
        })
      .onFailure(
        err -> {
          LOGGER.error("Failed to stream CSV", err);
          ctx.fail(err);
        });
  }

  public void getCreditRequestReport(RoutingContext ctx) {
    HttpServerResponse response = ctx.response();
    response
      .putHeader("Access-Control-Allow-Origin", "*")
      .putHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
      .putHeader("Access-Control-Allow-Methods", "GET, POST,PUT, DELETE, OPTIONS")
      .putHeader("Content-Type", "text/csv")
      .putHeader("Content-Disposition", "attachment; filename=\"credit_request_report.csv\"")
      .setChunked(true);

    PaginatedRequest request = PaginationRequestBuilder.from(ctx)
      .allowedFiltersDbMap(ALLOWED_FILTER_MAP_FOR_CREDIT_REQUEST)
      .apiToDbMap(API_TO_DB_CREDIT_REQUEST)
      .allowedTimeFields(Set.of(REQUESTED_AT))
      .defaultTimeField(REQUESTED_AT)
      .defaultSort(REQUESTED_AT, DEFAULT_SORTING_ORDER)
      .allowedSortFields(API_TO_DB_CREDIT_REQUEST.keySet())
      .build();

    organizationCreateReportService
      .streamAdminCsvBatchedCredit(request)
      .onSuccess(
        csvStream -> {
          if (csvStream == null) {
            response.end();
            return;
          }
          csvStream
            .exceptionHandler(
              err -> {
                LOGGER.error("Failed to stream CSV", err);
                ctx.fail(err);
              })
            .handler(buffer -> response.write(buffer))
            .endHandler(v -> response.end());
        })
      .onFailure(
        err -> {
          LOGGER.error("Failed to stream CSV", err);
          ctx.fail(err);
        });
  }


  public void getUserOrganisationRequest(RoutingContext ctx) {
    User user = ctx.user();
    UUID userId = UUID.fromString(user.subject());

    AuditLog auditLog = AuditingHelper.createAuditLog(
      ctx.user(),
      RoutingContextHelper.getRequestPath(ctx),
      "GET",
      "Get User Organization Requests"
    );

    organizationService.getOrganizationCreateRequestsByUserId(userId)
      .compose(requests -> {
        List<JsonObject> result = requests.stream()
          .map(OrganizationCreateRequest::toJson)
          .collect(Collectors.toList());
        return Future.succeededFuture(result);
      })
      .onSuccess(result -> {
        RoutingContextHelper.setAuditingLog(ctx, auditLog);
        ResponseBuilder.sendSuccess(ctx, result, urnGenerator);
      })
      .onFailure(err -> {
        LOGGER.error("Failed to fetch organization requests for user {}: {}", userId, err.getMessage());
        ctx.fail(err);
      });
  }

  public void deleteOrganizationCreateRequest(RoutingContext ctx) {
    UUID requestId = UUID.fromString(ctx.pathParam("id"));
    User user = ctx.user();
    UUID userId = UUID.fromString(user.subject());


    organizationService.getOrganizationCreateRequestById(requestId)
      .compose(request -> {
        if (request == null) {
          ctx.fail(new DxBadRequestException("Organization request not found"));
          return Future.failedFuture(new DxBadRequestException("Organization request not found"));
        }

        if (!request.status().equals(Status.PENDING.getStatus())) {
          ctx.fail(new DxBadRequestException("Only pending requests can be deleted"));
          return Future.failedFuture(new DxBadRequestException("Only pending requests can be deleted"));
        }

        if (!request.requestedBy().equals(userId)) {
          ctx.fail(new DxForbiddenException("User is not authorized to delete this request"));
          return Future.failedFuture(new DxForbiddenException("User is not authorized to delete this request"));
        }

        return organizationService.deleteOrganizationRequestById(requestId)
          .compose(deleted -> {
            if (!deleted) {
              return Future.failedFuture(new DxNotFoundException(
                "Failed to delete organization request with ID: " + requestId));
            }
            AuditLog auditLog = AuditingHelper.createAuditLog(
              ctx.user(),
              RoutingContextHelper.getRequestPath(ctx),
              "DELETE",
              "Deleted Organization Request"
            );
            RoutingContextHelper.setAuditingLog(ctx, auditLog);
            ResponseBuilder.sendSuccess(ctx, "Organization request deleted successfully", urnGenerator);
            return Future.succeededFuture(true);
          });
      })
      .onFailure(ctx::fail);
  }


  public void getUserJoinOrganisationRequests(RoutingContext ctx) {
    User user = ctx.user();
    UUID userId = UUID.fromString(user.subject());

    AuditLog auditLog = AuditingHelper.createAuditLog(
      ctx.user(),
      RoutingContextHelper.getRequestPath(ctx),
      "GET",
      "Get User Join Organisation Requests"
    );

    organizationService.getOrganizationJoinRequestsByUser(userId)
      .compose(requests -> {
        List<JsonObject> result = requests.stream()
          .map(OrganizationJoinRequest::toJson)
          .collect(Collectors.toList());
        return Future.succeededFuture(result);
      })
      .onSuccess(result -> {
        RoutingContextHelper.setAuditingLog(ctx, auditLog);
        ResponseBuilder.sendSuccess(ctx, result, urnGenerator);
      })
      .onFailure(err -> {
        LOGGER.error("Failed to fetch join organisation requests for user {}: {}", userId, err.getMessage());
        ctx.fail(err);
      });
  }

  public void deleteUserJoinOrganisationRequests(RoutingContext ctx) {
    UUID requestId = UUID.fromString(ctx.pathParam("id"));
    User user = ctx.user();
    UUID userId = UUID.fromString(user.subject());

    organizationService.getOrganizationJoinRequestById(requestId)
      .compose(request -> {
        if (request == null) {
          ctx.fail(new DxBadRequestException("Join organisation request not found"));
          return Future.failedFuture(new DxBadRequestException("Join organisation request not found"));
        }

        if (!request.status().equals(Status.PENDING.getStatus())) {
          ctx.fail(new DxBadRequestException("Only pending requests can be deleted"));
          return Future.failedFuture(new DxBadRequestException("Only pending requests can be deleted"));
        }

        if (!request.userId().equals(userId)) {
          ctx.fail(new DxForbiddenException("User is not authorized to delete this request"));
          return Future.failedFuture(new DxForbiddenException("User is not authorized to delete this request"));
        }

        return organizationService.deleteOrganizationJoinRequestById(requestId)
          .compose(deleted -> {
            if (!deleted) {
              return Future.failedFuture(new DxNotFoundException(
                "Failed to delete join organisation request with ID: " + requestId));
            }
            AuditLog auditLog = AuditingHelper.createAuditLog(
              ctx.user(),
              RoutingContextHelper.getRequestPath(ctx),
              "DELETE",
              "Deleted Join Organisation Request"
            );
            RoutingContextHelper.setAuditingLog(ctx, auditLog);
            ResponseBuilder.sendSuccess(ctx, "Join organisation request deleted successfully", urnGenerator);
            return Future.succeededFuture(true);
          });
      })
      .onFailure(ctx::fail);
  }

  public void getProviderRoleRequest(RoutingContext ctx) {
    AuditLog auditLog = AuditingHelper.createAuditLog(
      ctx.user(),
      RoutingContextHelper.getRequestPath(ctx),
      "GET",
      "Get Provider Role Request by User"
    );

    UUID userId = UUID.fromString(ctx.user().subject());

    organizationService.getProviderRoleRequestByUserId(userId) // Future<ProviderRoleRequest>
      .compose(request -> {
        if (request == null) {
          return Future.failedFuture(
            new DxNotFoundException("No provider role request found for userId: " + userId)
          );
        }

        return userService.enrichWithUserRoles(
          List.of(request),
          ProviderRoleRequest::userId,
          ProviderRoleRequest::toJson
        ).map(list -> list.isEmpty() ? null : list.get(0));
      })
      .onSuccess(enriched -> {
        RoutingContextHelper.setAuditingLog(ctx, auditLog);

        if (enriched == null) {
          ResponseBuilder.sendSuccess(ctx, new JsonObject(), this.urnGenerator);
        } else {
          ResponseBuilder.sendSuccess(ctx, enriched, this.urnGenerator);
        }
      })
      .onFailure(err -> {
        LOGGER.error("Failed to fetch provider role request for user {}: {}", userId, err.getMessage());
        ctx.fail(err);
      });
  }

  public void deleteUserProviderRoleRequest(RoutingContext ctx) {
    UUID userId = UUID.fromString(ctx.user().subject());

    organizationService.getProviderRoleRequestByUserId(userId)
      .compose(request -> {
        if (request == null) {
          return Future.failedFuture(
            new DxNotFoundException("No provider role request found for userId: " + userId)
          );
        }

        if (!Status.PENDING.getStatus().equalsIgnoreCase(request.status())) {
          return Future.failedFuture(
            new DxBadRequestException("Only pending provider role requests can be deleted")
          );
        }

        return organizationService.deleteProviderRoleRequestById(request.id());
      })
      .onSuccess(deleted -> {
        if (deleted) {
          AuditLog auditLog = AuditingHelper.createAuditLog(
            ctx.user(),
            RoutingContextHelper.getRequestPath(ctx),
            "DELETE",
            "Deleted Provider Role Request"
          );
          RoutingContextHelper.setAuditingLog(ctx, auditLog);

          ResponseBuilder.sendSuccess(
            ctx,
            "Provider Role Request deleted successfully",
            this.urnGenerator
          );
        } else {
          ctx.fail(new DxNotFoundException("Failed to delete provider role request"));
        }
      })
      .onFailure(err -> {
        LOGGER.error("Failed to delete provider role request for user {}: {}", userId, err.getMessage());
        ctx.fail(err);
      });
  }

}
