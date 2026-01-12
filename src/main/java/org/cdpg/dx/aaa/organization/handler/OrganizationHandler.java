package org.cdpg.dx.aaa.organization.handler;

import static org.cdpg.dx.aaa.organization.config.Constants.*;
import static org.cdpg.dx.database.postgres.util.Constants.DEFAULT_SORTING_ORDER;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.audit.util.AuditingHelper;
import org.cdpg.dx.aaa.delegation.service.DelegationService;
import org.cdpg.dx.aaa.email.util.EmailComposer;
import org.cdpg.dx.aaa.organization.audit.OrganizationAuditHelper;
import org.cdpg.dx.aaa.organization.models.*;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.aaa.user.service.UserService;
import org.cdpg.dx.auditing.model.ActivityAuditLogBuilder;
import org.cdpg.dx.auditing.model.AuditLog;
import org.cdpg.dx.auth.authentication.util.AccessValidator;
import org.cdpg.dx.auth.authorization.model.DxRole;
import org.cdpg.dx.auth.authorization.model.DxScope;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.exception.DxConflictException;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.request.PaginationRequestBuilder;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.common.util.RequestHelper;
import org.cdpg.dx.common.util.RoutingContextHelper;
import org.cdpg.dx.keycloak.config.KeycloakConstants;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

public class OrganizationHandler {

  private static final Logger LOGGER = LogManager.getLogger(OrganizationHandler.class);
  private final OrganizationService organizationService;
  private final UserService userService;
  private final EmailComposer emailComposer;
  private final KeycloakUserService keycloakUserService;
  private final URNGenerator urnGenerator;
  private final DelegationService delegationService;

  public OrganizationHandler(
      OrganizationService organizationService,
      UserService userService,
      EmailComposer emailComposer,
      KeycloakUserService keycloakUserService,
      URNGenerator urnGenerator,
      DelegationService delegationService) {
    this.organizationService = organizationService;
    this.userService = userService;
    this.emailComposer = emailComposer;
    this.keycloakUserService = keycloakUserService;
    this.urnGenerator = urnGenerator;
    this.delegationService = delegationService;
  }

  public Future<Boolean> validateEntityId(UUID delegatorId, UUID orgId) {

    return delegationService
        .getDelegationScopeByEntityId(orgId)
        .compose(
            scopeConstraints -> {
              boolean exists =
                  scopeConstraints.stream()
                      .anyMatch(
                          scopeConstraint ->
                              "org_management".equalsIgnoreCase(scopeConstraint.scope())
                                  && orgId.equals(scopeConstraint.entityId())
                                  && LocalDateTime.now().isBefore(scopeConstraint.expiryAt()));

              if (!exists) {
                return Future.failedFuture(
                    new DxForbiddenException(
                        "Delegation not found or expired for org_management scope"));
              }

              return Future.succeededFuture(true);
            });
  }


  public Future<Void> verifyUserBelongsToOrg(UUID userId, UUID orgId) {

    return organizationService
        .getOrganisationUserByUserId(userId)
        .compose(
            orgUser -> {
              if (orgUser == null) {
                return Future.failedFuture(
                    new DxNotFoundException("User does not belong to any organisation"));
              }

              if (!orgUser.organizationId().equals(orgId)) {
                return Future.failedFuture(
                    new DxForbiddenException("User does not belong to this organisation"));
              }

              return Future.succeededFuture();
            });
  }


  public void updateOrganisationById(RoutingContext ctx) {

    // updates org
    // delegate requirement: scope - org_management and delegator is cos_admin
    // check if request param and delegated org id is same

    User user = ctx.user();
    JsonObject userJson = user.principal();

    AccessValidator.validate(
        userJson,
        List.of( // primary roles (no scope check)
            DxRole.COS_ADMIN.getRole()),
        List.of(DxScope.USER_MANAGEMENT.getScope(), DxScope.COS_ADMIN_ACCESS.getScope()));

    UUID orgId = RequestHelper.getPathParamAsUUID(ctx, "id");
    UpdateOrgDTO updateOrgDTO = RequestHelper.parseBody(ctx, UpdateOrgDTO::fromJson);

    organizationService
        .updateOrganizationById(orgId, updateOrgDTO)
        .onSuccess(
            updatedOrg -> {
              ActivityAuditLogBuilder auditLog =
                  OrganizationAuditHelper.buildOrganizationUpdateAudit(
                      ctx, updatedOrg.id(), updatedOrg.orgName(), updateOrgDTO.toJson());
              RoutingContextHelper.setAuditingLogNew(ctx, auditLog);
              ResponseBuilder.sendSuccess(ctx, updatedOrg, urnGenerator);
            })
        .onFailure(
            err -> {
              LOGGER.error(
                  "Failed to Update Organization id: {}, message: {}",
                  orgId,
                  err.getMessage(),
                  err);
              ctx.fail(err);
            });
  }

  public void deleteOrganisationById(RoutingContext ctx) {

    UUID orgId = RequestHelper.getPathParamAsUUID(ctx, "id");
    organizationService
        .deleteOrganization(orgId)
        .onSuccess(
            updatedOrg -> {
              ActivityAuditLogBuilder auditLog =
                  OrganizationAuditHelper.buildOrganizationDeleteAudit(ctx, orgId,  null);
              RoutingContextHelper.setAuditingLogNew(ctx, auditLog);
              ResponseBuilder.sendSuccess(ctx, updatedOrg, urnGenerator);
              ResponseBuilder.sendSuccess(ctx, "Organisation deleted Successfully!", urnGenerator);
            })
        .onFailure(ctx::fail);
  }

  public void listAllOrganisations(RoutingContext ctx) {
    PaginatedRequest request =
        PaginationRequestBuilder.from(ctx)
            .allowedFiltersDbMap(ALLOWED_FILTER_MAP_FOR_ORG)
            .apiToDbMap(API_TO_DB_ORG)
            .allowedTimeFields(Set.of(CREATED_AT))
            .defaultTimeField(CREATED_AT)
            .defaultSort(CREATED_AT, DEFAULT_SORTING_ORDER)
            .allowedSortFields(API_TO_DB_ORG.keySet())
            .build();

    organizationService
        .getOrganizations(request)
        .onSuccess(
            orgs -> {
              AuditLog auditLog =
                  AuditingHelper.createAuditLog(
                      ctx.user(),
                      RoutingContextHelper.getRequestPath(ctx),
                      "GET",
                      "List All Organisations");
              RoutingContextHelper.setAuditingLog(ctx, auditLog);
              ResponseBuilder.sendSuccess(
                  ctx,
                  orgs.data().stream()
                      .map(Organization::toFilteredJson)
                      .collect(Collectors.toList()),
                  orgs.paginationInfo(),
                  urnGenerator);
            })
        .onFailure(ctx::fail);
  }

  public void approveJoinOrganisationRequests(RoutingContext ctx) {

    User user = ctx.user();
    JsonObject userJson = user.principal();

    AccessValidator.validate(
        userJson,
        List.of( // primary roles (no scope check)
            DxRole.ORG_ADMIN.getRole()),
        List.of(DxScope.USER_MANAGEMENT.getScope()));

    JsonObject OrgRequestJson = ctx.body().asJsonObject();

    UUID requestId = RequestHelper.getPathParamAsUUID(ctx, "req_id");

    Status status = Status.fromString(OrgRequestJson.getString("status"));

    organizationService
        .updateOrganizationJoinRequestStatus(requestId, status)
        .onSuccess(
            approved -> {
              if (approved) {

                AuditLog auditLog =
                    AuditingHelper.createAuditLog(
                        ctx.user(),
                        RoutingContextHelper.getRequestPath(ctx),
                        "PUT",
                        "Approved Join Request");
                RoutingContextHelper.setAuditingLog(ctx, auditLog);
                ResponseBuilder.sendSuccess(ctx, "Updated Organisation Join Request", urnGenerator);
                Future<Void> future =
                    emailComposer.sendUserEmailForOrgJoinRequestApproval(requestId, status);

              } else {
                ctx.fail(new DxNotFoundException("Request Not Found"));
              }
            })
        .onFailure(ctx::fail);
  }

  public void getJoinOrganisationRequests(RoutingContext ctx) {

    UUID orgId = RequestHelper.getPathParamAsUUID(ctx, "id");
    User user = ctx.user();
    JsonObject userJson = user.principal();

    AccessValidator.validate(
        userJson,
        List.of( // primary roles (no scope check)
            DxRole.ORG_ADMIN.getRole()),
        List.of(DxScope.USER_MANAGEMENT.getScope()));

    PaginatedRequest request =
        PaginationRequestBuilder.from(ctx)
            .allowedFiltersDbMap(ALLOWED_FILTER_MAP_FOR_ORG_JOIN_REQUEST)
            .apiToDbMap(API_TO_DB_ORG_JOIN_REQUEST)
            .additionalFilters(Map.of(ORGANIZATION_ID, orgId.toString()))
            .allowedTimeFields(Set.of(REQUESTED_AT))
            .defaultTimeField(REQUESTED_AT)
            .defaultSort(REQUESTED_AT, DEFAULT_SORTING_ORDER)
            .allowedSortFields(API_TO_DB_ORG_JOIN_REQUEST.keySet())
            .build();

    organizationService
        .getOrganizationPendingJoinRequests(request)
        .compose(
            result ->
                userService
                    .enrichWithUserRoles(
                        result.data(),
                        OrganizationJoinRequest::userId,
                        OrganizationJoinRequest::toJson)
                    .map(enrichedList -> Map.entry(enrichedList, result.paginationInfo())))
        .onSuccess(
            entry -> {
              AuditLog auditLog =
                  AuditingHelper.createAuditLog(
                      ctx.user(),
                      RoutingContextHelper.getRequestPath(ctx),
                      "GET",
                      "Get Pending Join Requests");
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
    JsonObject jsonBody = organizationJoinRequest.toJson();
    String officialEmailStr = jsonBody.getString("official_email");

    organizationService
        .getAllOrganizationJoinRequests()
        .compose(
            joinRequests -> {
              for (OrganizationJoinRequest request : joinRequests) {

                //          if (request.organizationId().equals(orgId.toString())
                //            && request.userId().equals(user.subject()))
                if (request.organizationId().equals(orgId)
                    && request.userId().equals(UUID.fromString(user.subject()))) {

                  return Future.failedFuture(
                      new DxConflictException(
                          "User already has a pending/ granted join request for this organization"));
                }

                if (!request.status().equals(Status.REJECTED)
                    && request.officialEmail().equals(officialEmailStr)) {
                  return Future.failedFuture(
                      new DxConflictException("This email has been used already!"));
                }
              }

              return organizationService
                  .joinOrganizationRequest(organizationJoinRequest)
                  .onSuccess(
                      createdRequest -> {
                        ActivityAuditLogBuilder auditLog =
                            OrganizationAuditHelper.buildJoinOrgRequestAudit(
                                ctx,
                                createdRequest.id(),
                                createdRequest.organizationId(),
                                "member");
                        RoutingContextHelper.setAuditingLogNew(ctx, auditLog);

                        ResponseBuilder.sendSuccess(ctx, "Created Join request", urnGenerator);
                        Future<Void> future =
                            emailComposer.sendEmailForJoiningOrg(organizationJoinRequest, user);
                      })
                  .onFailure(ctx::fail);
            })
        .onFailure(ctx::fail);
  }

  public void updateOrganisationRequest(RoutingContext ctx) {

    JsonObject OrgRequestJson = ctx.body().asJsonObject();
    User user = ctx.user();
    JsonObject userJson = user.principal();

    AccessValidator.validate(
        userJson,
        List.of( // primary roles (no scope check)
            DxRole.COS_ADMIN.getRole()),
        List.of(DxScope.COS_ADMIN_ACCESS.getScope()));

    UUID requestId = UUID.fromString(OrgRequestJson.getString("req_id"));
    Status status = Status.fromString(OrgRequestJson.getString("status"));

    JsonObject responseObject = OrgRequestJson.copy();
    responseObject.remove("status");

    organizationService
        .updateOrganizationCreateRequestStatus(requestId, status)
        .onSuccess(
            updated -> {
              ActivityAuditLogBuilder auditLog =
                  OrganizationAuditHelper.buildOrgCreateOrgUpdateAudit(
                      ctx, requestId, status.getStatus());
              RoutingContextHelper.setAuditingLogNew(ctx, auditLog);

              ResponseBuilder.sendSuccess(ctx, "Updated Sucessfully", urnGenerator);
              Future<Void> future =
                  emailComposer.sendUserEmailForOrgCreateRequestApproval(requestId, status);
            })
        .onFailure(ctx::fail);
  }

  public void getAllOrganisationRequest(RoutingContext ctx) {

    User user = ctx.user();
    JsonObject userJson = user.principal();

    AccessValidator.validate(
        userJson,
        List.of( // primary roles (no scope check)
            DxRole.COS_ADMIN.getRole()),
        List.of(DxScope.COS_ADMIN_ACCESS.getScope()));

    PaginatedRequest request =
        PaginationRequestBuilder.from(ctx)
            .allowedFiltersDbMap(ALLOWED_FILTER_MAP_FOR_ORG_CREATE_REQUEST)
            .apiToDbMap(API_TO_DB_ORG_CREATE_REQUEST)
            .allowedTimeFields(Set.of(CREATED_AT))
            .defaultTimeField(CREATED_AT)
            .defaultSort(CREATED_AT, DEFAULT_SORTING_ORDER)
            .allowedSortFields(API_TO_DB_ORG_CREATE_REQUEST.keySet())
            .build();

    organizationService
        .getAllOrganizationCreateRequests(request)
        .onSuccess(
            res -> {
              AuditLog auditLog =
                  AuditingHelper.createAuditLog(
                      ctx.user(),
                      RoutingContextHelper.getRequestPath(ctx),
                      "GET",
                      "Get All Organisation Requests");

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

    OrganizationCreateRequest organizationCreateRequest =
        OrganizationCreateRequest.fromJson(OrgRequestJson);

    organizationService
        .getOrganizationCreateRequestsByUserId(UUID.fromString(user.subject()))
        .compose(
            createRequests -> {
              for (OrganizationCreateRequest request : createRequests) {
                if (request.requestedBy().equals(UUID.fromString(user.subject()))) {
                  return Future.failedFuture(
                      new DxConflictException(
                          "Organisation create request already granted/ pending for this user"));
                }
              }
              return organizationService
                  .getAllPendingGrantedOrganizationCreateRequests()
                  .compose(
                      requests -> {
                        for (OrganizationCreateRequest request : requests) {
                          if (request.name().equalsIgnoreCase(orgName)) {
                            return Future.failedFuture(
                                new DxConflictException(
                                    "Organisation name already exists/ under review"));
                          }
                        }
                        return organizationService
                            .getAllPendingGrantedOrganizationCreateRequests()
                            .compose(
                                pendingRequests -> {
                                  for (OrganizationCreateRequest request : requests) {
                                    if (request
                                        .managerEmail()
                                        .equalsIgnoreCase(
                                            organizationCreateRequest.managerEmail())) {
                                      return Future.failedFuture(
                                          new DxConflictException(
                                              "Manager email is already in use for another organisation request"));
                                    }
                                  }
                                  return organizationService.createOrganizationRequest(
                                      organizationCreateRequest);
                                })
                            .onFailure(ctx::fail);
                      })
                  .onFailure(ctx::fail);
            })
        .onSuccess(
            requests -> {
              ActivityAuditLogBuilder auditLog =
                  OrganizationAuditHelper.buildOrgCreateRequestAudit(
                      ctx, requests.id(), requests.name());
              RoutingContextHelper.setAuditingLogNew(ctx, auditLog);

              ResponseBuilder.sendSuccess(ctx, requests, urnGenerator);
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

    userService
        .getUserInfoByID(userId)
        .compose(
            user -> {
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

              return deletionFuture.compose(
                  deleted -> {
                    if (!deleted) {
                      return Future.failedFuture(
                          new DxNotFoundException("User not found in organization"));
                    }
                    return organizationService
                        .deleteOrganizationJoinRequest(orgId, userId)
                        .recover(
                            err -> {
                              LOGGER.warn("Failed to delete join request: {}", err.getMessage());
                              return Future.succeededFuture();
                            })
                        .compose(
                            v ->
                                organizationService
                                    .deleteProviderRoleRequest(orgId, userId)
                                    .recover(
                                        err -> {
                                          LOGGER.warn(
                                              "Failed to delete provider role request: {}",
                                              err.getMessage());
                                          return Future.succeededFuture();
                                        }))
                        .onSuccess(
                            v -> {
                              LOGGER.info(
                                  "User {} deleted completely from Organization {}", userId, orgId);
                              AuditLog auditLog =
                                  AuditingHelper.createAuditLog(
                                      ctx.user(),
                                      RoutingContextHelper.getRequestPath(ctx),
                                      "DELETE",
                                      "Deleted User with Cleanup");
                              RoutingContextHelper.setAuditingLog(ctx, auditLog);
                              ResponseBuilder.sendSuccess(
                                  ctx,
                                  "User deleted successfully from DB and Keycloak",
                                  urnGenerator);
                            })
                        .onFailure(ctx::fail);
                  });
            });
  }



  public void getOrganisationUserInfo(RoutingContext ctx) {
    // gets org_user info
    // delegate requirement: scope - org_management and delegator is org_admin or cos_admin
    // delegation table has the org_id

    User user = ctx.user();
    JsonObject userJson = user.principal();

    AccessValidator.validate(
        userJson,
        List.of( // primary roles (no scope check)
            DxRole.ORG_ADMIN.getRole()),
        List.of(DxScope.USER_MANAGEMENT.getScope()));

    UUID orgId = RequestHelper.getPathParamAsUUID(ctx, "id");
    UUID userId = RequestHelper.getPathParamAsUUID(ctx, "user_id");

    // TODO check this belogns to the org

    userService
        .getUserInfoByID(userId)
        .onSuccess(
            users -> {
              AuditLog auditLog =
                  AuditingHelper.createAuditLog(
                      ctx.user(),
                      RoutingContextHelper.getRequestPath(ctx),
                      "GET",
                      "Get User Info By ID");
              RoutingContextHelper.setAuditingLog(ctx, auditLog);
              ResponseBuilder.sendSuccess(ctx, users, urnGenerator);
            })
        .onFailure(ctx::fail);
  }

  public void getOrganisationUsers(RoutingContext ctx) {
    // gets org_user
    // delegate requirement: scope - org_management and delegator is org_admin
    // delegation table has the org_id

    User user = ctx.user();
    JsonObject userJson = user.principal();

    AccessValidator.validate(
        userJson,
        List.of( // primary roles (no scope check)
            DxRole.ORG_ADMIN.getRole()),
        List.of(DxScope.USER_MANAGEMENT.getScope()));

    UUID orgId = RequestHelper.getPathParamAsUUID(ctx, "id");

    PaginatedRequest request =
        PaginationRequestBuilder.from(ctx)
            .allowedFiltersDbMap(ALLOWED_FILTER_MAP_FOR_ORG_USERS)
            .apiToDbMap(API_TO_DB_ORG_USERS)
            .additionalFilters(Map.of(ORGANIZATION_ID, orgId.toString()))
            .allowedTimeFields(Set.of(CREATED_AT))
            .defaultTimeField(CREATED_AT)
            .defaultSort(CREATED_AT, DEFAULT_SORTING_ORDER)
            .allowedSortFields(API_TO_DB_ORG_USERS.keySet())
            .build();

    AuditLog auditLog =
        AuditingHelper.createAuditLog(
            ctx.user(),
            RoutingContextHelper.getRequestPath(ctx),
            "GET",
            "Get Organisation Users by OrgID");

    organizationService
        .getOrganizationUsers(request)
        .compose(
            res ->
                userService
                    .enrichWithUserRoles(
                        res.data(), OrganizationUser::userId, OrganizationUser::toJson)
                    .map(enriched -> Map.entry(enriched, res.paginationInfo())))
        .onSuccess(
            entry -> {
              RoutingContextHelper.setAuditingLog(ctx, auditLog);
              ResponseBuilder.sendSuccess(ctx, entry.getKey(), entry.getValue(), urnGenerator);
            })
        .onFailure(ctx::fail);
  }


  public void updateOrganisationUserRole(RoutingContext ctx) {

    // updates org_user role
    // delegate requirement: scope - org_management and delegator is org_admin or cos_admin
    // delegation table has org_id

    JsonObject OrgRequestJson = ctx.body().asJsonObject();

    Role role;
    role = Role.fromString(OrgRequestJson.getString("role"));

    UUID orgId = RequestHelper.getPathParamAsUUID(ctx, "id");
    UUID userId = RequestHelper.getPathParamAsUUID(ctx, "user_id");

    organizationService
        .updateUserRole(orgId, userId, role)
        .onSuccess(
            updated -> {
              if (updated) {
                /* AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
                  RoutingContextHelper.getRequestPath(ctx), "PUT", "Update Organisation User Role");
                RoutingContextHelper.setAuditingLog(ctx, auditLog);*/
                ResponseBuilder.sendSuccess(ctx, "Updated Organisation User Role", urnGenerator);

              } else {
                ctx.fail(new DxNotFoundException("Organisation User Not Found"));
              }
            })
        .onFailure(ctx::fail);
    ;
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

    JsonObject req = new JsonObject().put("user_id", user.subject()).put("organization_id", orgID);

    ProviderRoleRequest providerRoleRequest = ProviderRoleRequest.fromJson(req);

    organizationService
        .createProviderRequest(providerRoleRequest)
        .onSuccess(
            requests -> {
              /*   AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
                RoutingContextHelper.getRequestPath(ctx), "POST", "Create Provider Role Request");
              RoutingContextHelper.setAuditingLog(ctx, auditLog);*/
              ResponseBuilder.sendSuccess(ctx, "Created Request", urnGenerator);
              Future<Void> future =
                  emailComposer.sendEmailForProviderRole(providerRoleRequest, user);
            })
        .onFailure(ctx::fail);
  }

  public void updateProviderRequest(RoutingContext ctx) {

    User user = ctx.user();
    JsonObject userJson = user.principal();

    AccessValidator.validate(
        userJson,
        List.of( // primary roles (no scope check)
            DxRole.ORG_ADMIN.getRole()),
        List.of(DxScope.USER_MANAGEMENT.getScope()));

    JsonObject OrgRequestJson = ctx.body().asJsonObject();
    UUID reqId = RequestHelper.getPathParamAsUUID(ctx, "id");
    Status status = Status.fromString(OrgRequestJson.getString("status"));

    organizationService
        .updateProviderRequestStatus(reqId, status)
        .onSuccess(
            requests -> {
              /*AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
                        RoutingContextHelper.getRequestPath(ctx), "PUT", "Update Provider Role Request");
                      RoutingContextHelper.setAuditingLog(ctx, auditLog);
              */ ResponseBuilder.sendSuccess(
                  ctx, "Provider role updated", urnGenerator);
              Future<Void> future =
                  emailComposer.sendUserEmailForProviderRoleApproval(reqId, status);
            })
        .onFailure(ctx::fail);
  }

  public void getProviderRequest(RoutingContext ctx) {


    User user = ctx.user();
    if (user == null || user.subject() == null || user.principal() == null) {
      ctx.fail(new DxForbiddenException("User not authenticated"));
      return;
    }

    JsonObject userJson = user.principal();
    UUID userId = UUID.fromString(user.subject());

    String orgIdStr = ctx.pathParam("id");
    if (orgIdStr == null || orgIdStr.isBlank()) {
      ctx.fail(new DxForbiddenException("Organization ID is required"));
      return;
    }

    UUID requestedOrgId = UUID.fromString(orgIdStr);

    UUID delegatorId = null;
    if (userJson.containsKey("did")) {
      delegatorId = UUID.fromString(userJson.getString("did"));
    }

    LOGGER.info("Fetching provider requests for org {} by user {}", requestedOrgId, userId);

    /*
     * Step 1: Resolve effective owner
     */
    resolveEffectiveOwner(userId, delegatorId, requestedOrgId, userJson)
      .compose(effectiveOwnerId -> {

        /*
         * Step 2: Build paginated request FOR REQUESTED ORG ONLY
         */

        PaginatedRequest request = PaginationRequestBuilder.from(ctx)
          .allowedFiltersDbMap(ALLOWED_FILTER_MAP_FOR_PROVIDER_ROLE_REQUEST)
          .apiToDbMap(API_TO_DB_PROVIDER_ROLE_REQUEST)
          .additionalFilters(Map.of(ORGANIZATION_ID, requestedOrgId.toString()))
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
        ).map(enriched ->
          Map.entry(enriched, requests.paginationInfo())
        )
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


  private Future<UUID> resolveEffectiveOwner(
    UUID userId,
    UUID delegatorId,
    UUID requestedOrgId,
    JsonObject userJson
  ) {

    // Case 1: User is direct org admin
    return userService.getUserInfoByID(userId)
      .compose(orgUser -> {
        if (orgUser != null && requestedOrgId.equals(orgUser.organisationId())) {
          LOGGER.info("User {} is direct owner of org {}", userId, requestedOrgId);
          return Future.succeededFuture(userId);
        }

        // Case 2: Delegated access
        if (delegatorId == null) {
          return Future.failedFuture(
            new DxForbiddenException("User is neither owner nor delegate")
          );
        }

        // Scope check for delegated access
        AccessValidator.validate(
          userJson,
          List.of(DxRole.ORG_ADMIN.getRole()),
          List.of(DxScope.USER_MANAGEMENT.getScope())
        );

        return userService.getUserInfoByID(delegatorId)
          .compose(delegatorOrgUser -> {

            // ✅ Case 2a: Delegator is COS admin (global authority)
            if (delegatorOrgUser != null
              && delegatorOrgUser.roles().contains(DxRole.COS_ADMIN.toString())) {

              LOGGER.info(
                "User {} acting as delegate for COS admin {} on org {}",
                userId, delegatorId, requestedOrgId
              );

              return Future.succeededFuture(delegatorId);
            }

            // ✅ Case 2b: Delegator owns requested org
            if (delegatorOrgUser != null
              && requestedOrgId.equals(delegatorOrgUser.organisationId())) {

              LOGGER.info(
                "User {} acting as delegate for org owner {} on org {}",
                userId, delegatorId, requestedOrgId
              );

              return Future.succeededFuture(delegatorId);
            }

            // ❌ Not allowed
            return Future.failedFuture(
              new DxForbiddenException(
                "Delegator does not have authority over requested organization"
              )
            );
          });
      });
  }






  public void createProviderRole(RoutingContext ctx) {
    JsonObject providerRequestJson = ctx.body().asJsonObject();

    ProviderRoleRequest providerRoleRequest = ProviderRoleRequest.fromJson(providerRequestJson);

    organizationService
        .createProviderRole(providerRoleRequest)
        .onSuccess(
            org ->
                ResponseBuilder.sendSuccess(
                    ctx, "Provider role granted successfully", urnGenerator))
        .onFailure(
            err -> {
              if (err instanceof DxForbiddenException) {
                ctx.fail(
                    new DxForbiddenException(
                        "User is not part of any organisation or does not have permission to grant provider role"));
              } else if (err instanceof DxNotFoundException) {
                ctx.fail(
                    new DxNotFoundException(
                        "User not found or does not have a pending provider role request"));
              } else {
                ctx.fail(err);
              }
            });
  }

  public void getOrganizationById(RoutingContext ctx) {
    UUID orgId = RequestHelper.getPathParamAsUUID(ctx, "id");

    organizationService
        .getOrganizationById(orgId)
        .onSuccess(
            org -> {
              /* AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
                RoutingContextHelper.getRequestPath(ctx), "GET", "Get Organization By ID");
              RoutingContextHelper.setAuditingLog(ctx, auditLog);*/
              ResponseBuilder.sendSuccess(ctx, org.toJson(), urnGenerator);
            })
        .onFailure(
            err -> {
              if (err instanceof DxNotFoundException) {
                ctx.fail(new DxNotFoundException("Organization not found with id: " + orgId));
              } else {
                ctx.fail(err);
              }
            });
  }

  public void getUserOrganisationRequest(RoutingContext ctx) {
    User user = ctx.user();
    UUID userId = UUID.fromString(user.subject());

    AuditLog auditLog =
        AuditingHelper.createAuditLog(
            ctx.user(),
            RoutingContextHelper.getRequestPath(ctx),
            "GET",
            "Get User Organization Requests");

    organizationService
        .getOrganizationCreateRequestsByUserId(userId)
        .compose(
            requests -> {
              List<JsonObject> result =
                  requests.stream()
                      .map(OrganizationCreateRequest::toJson)
                      .collect(Collectors.toList());
              return Future.succeededFuture(result);
            })
        .onSuccess(
            result -> {
              RoutingContextHelper.setAuditingLog(ctx, auditLog);
              ResponseBuilder.sendSuccess(ctx, result, urnGenerator);
            })
        .onFailure(
            err -> {
              LOGGER.error(
                  "Failed to fetch organization requests for user {}: {}",
                  userId,
                  err.getMessage());
              ctx.fail(err);
            });
  }

  public void deleteOrganizationCreateRequest(RoutingContext ctx) {
    UUID requestId = UUID.fromString(ctx.pathParam("id"));
    User user = ctx.user();
    UUID userId = UUID.fromString(user.subject());

    organizationService
        .getOrganizationCreateRequestById(requestId)
        .compose(
            request -> {
              if (request == null) {
                ctx.fail(new DxBadRequestException("Organization request not found"));
                return Future.failedFuture(
                    new DxBadRequestException("Organization request not found"));
              }

              if (!request.status().equals(Status.PENDING.getStatus())) {
                ctx.fail(new DxBadRequestException("Only pending requests can be deleted"));
                return Future.failedFuture(
                    new DxBadRequestException("Only pending requests can be deleted"));
              }

              if (!request.requestedBy().equals(userId)) {
                ctx.fail(new DxForbiddenException("User is not authorized to delete this request"));
                return Future.failedFuture(
                    new DxForbiddenException("User is not authorized to delete this request"));
              }

              return organizationService
                  .deleteOrganizationRequestById(requestId)
                  .compose(
                      deleted -> {
                        if (!deleted) {
                          return Future.failedFuture(
                              new DxNotFoundException(
                                  "Failed to delete organization request with ID: " + requestId));
                        }
                        AuditLog auditLog =
                            AuditingHelper.createAuditLog(
                                ctx.user(),
                                RoutingContextHelper.getRequestPath(ctx),
                                "DELETE",
                                "Deleted Organization Request");
                        RoutingContextHelper.setAuditingLog(ctx, auditLog);
                        ResponseBuilder.sendSuccess(
                            ctx, "Organization request deleted successfully", urnGenerator);
                        return Future.succeededFuture(true);
                      });
            })
        .onFailure(ctx::fail);
  }

  public void getUserJoinOrganisationRequests(RoutingContext ctx) {
    User user = ctx.user();
    UUID userId = UUID.fromString(user.subject());

    AuditLog auditLog =
        AuditingHelper.createAuditLog(
            ctx.user(),
            RoutingContextHelper.getRequestPath(ctx),
            "GET",
            "Get User Join Organisation Requests");

    organizationService
        .getOrganizationJoinRequestsByUser(userId)
        .compose(
            requests -> {
              List<JsonObject> result =
                  requests.stream()
                      .map(OrganizationJoinRequest::toJson)
                      .collect(Collectors.toList());
              return Future.succeededFuture(result);
            })
        .onSuccess(
            result -> {
              RoutingContextHelper.setAuditingLog(ctx, auditLog);
              ResponseBuilder.sendSuccess(ctx, result, urnGenerator);
            })
        .onFailure(
            err -> {
              LOGGER.error(
                  "Failed to fetch join organisation requests for user {}: {}",
                  userId,
                  err.getMessage());
              ctx.fail(err);
            });
  }

  public void deleteUserJoinOrganisationRequests(RoutingContext ctx) {
    UUID requestId = UUID.fromString(ctx.pathParam("id"));
    User user = ctx.user();
    UUID userId = UUID.fromString(user.subject());

    organizationService
        .getOrganizationJoinRequestById(requestId)
        .compose(
            request -> {
              if (request == null) {
                ctx.fail(new DxBadRequestException("Join organisation request not found"));
                return Future.failedFuture(
                    new DxBadRequestException("Join organisation request not found"));
              }

              if (!request.status().equals(Status.PENDING.getStatus())) {
                ctx.fail(new DxBadRequestException("Only pending requests can be deleted"));
                return Future.failedFuture(
                    new DxBadRequestException("Only pending requests can be deleted"));
              }

              if (!request.userId().equals(userId)) {
                ctx.fail(new DxForbiddenException("User is not authorized to delete this request"));
                return Future.failedFuture(
                    new DxForbiddenException("User is not authorized to delete this request"));
              }

              return organizationService
                  .deleteOrganizationJoinRequestById(requestId)
                  .compose(
                      deleted -> {
                        if (!deleted) {
                          return Future.failedFuture(
                              new DxNotFoundException(
                                  "Failed to delete join organisation request with ID: "
                                      + requestId));
                        }
                        AuditLog auditLog =
                            AuditingHelper.createAuditLog(
                                ctx.user(),
                                RoutingContextHelper.getRequestPath(ctx),
                                "DELETE",
                                "Deleted Join Organisation Request");
                        RoutingContextHelper.setAuditingLog(ctx, auditLog);
                        ResponseBuilder.sendSuccess(
                            ctx, "Join organisation request deleted successfully", urnGenerator);
                        return Future.succeededFuture(true);
                      });
            })
        .onFailure(ctx::fail);
  }

  public void getProviderRoleRequest(RoutingContext ctx) {
    AuditLog auditLog =
        AuditingHelper.createAuditLog(
            ctx.user(),
            RoutingContextHelper.getRequestPath(ctx),
            "GET",
            "Get Provider Role Request by User");

    UUID userId = UUID.fromString(ctx.user().subject());

    organizationService
        .getProviderRoleRequestByUserId(userId) // Future<ProviderRoleRequest>
        .compose(
            request -> {
              if (request == null) {
                return Future.failedFuture(
                    new DxNotFoundException(
                        "No provider role request found for userId: " + userId));
              }

              return userService
                  .enrichWithUserRoles(
                      List.of(request), ProviderRoleRequest::userId, ProviderRoleRequest::toJson)
                  .map(list -> list.isEmpty() ? null : list.get(0));
            })
        .onSuccess(
            enriched -> {
              // RoutingContextHelper.setAuditingLog(ctx, auditLog);

              if (enriched == null) {
                ResponseBuilder.sendSuccess(ctx, new JsonObject(), this.urnGenerator);
              } else {
                ResponseBuilder.sendSuccess(ctx, enriched, this.urnGenerator);
              }
            })
        .onFailure(
            err -> {
              LOGGER.error(
                  "Failed to fetch provider role request for user {}: {}",
                  userId,
                  err.getMessage());
              ctx.fail(err);
            });
  }

  public void deleteUserProviderRoleRequest(RoutingContext ctx) {
    UUID userId = UUID.fromString(ctx.user().subject());

    organizationService
        .getProviderRoleRequestByUserId(userId)
        .compose(
            request -> {
              if (request == null) {
                return Future.failedFuture(
                    new DxNotFoundException(
                        "No provider role request found for userId: " + userId));
              }

              if (!Status.PENDING.getStatus().equalsIgnoreCase(request.status())) {
                return Future.failedFuture(
                    new DxBadRequestException(
                        "Only pending provider role requests can be deleted"));
              }

              return organizationService.deleteProviderRoleRequestById(request.id());
            })
        .onSuccess(
            deleted -> {
              if (deleted) {
                /* AuditLog auditLog = AuditingHelper.createAuditLog(
                            ctx.user(),
                            RoutingContextHelper.getRequestPath(ctx),
                            "DELETE",
                            "Deleted Provider Role Request"
                          );
                          RoutingContextHelper.setAuditingLog(ctx, auditLog);
                */
                ResponseBuilder.sendSuccess(
                    ctx, "Provider Role Request deleted successfully", this.urnGenerator);
              } else {
                ctx.fail(new DxNotFoundException("Failed to delete provider role request"));
              }
            })
        .onFailure(
            err -> {
              LOGGER.error(
                  "Failed to delete provider role request for user {}: {}",
                  userId,
                  err.getMessage());
              ctx.fail(err);
            });
  }

}
