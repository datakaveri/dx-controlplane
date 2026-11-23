package org.cdpg.dx.aaa.organization.handler;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.email.util.EmailComposer;
import org.cdpg.dx.aaa.organization.audit.OrganizationAuditHelper;
import org.cdpg.dx.aaa.organization.models.DeptAdminScope;
import org.cdpg.dx.aaa.organization.models.OrganisationAuditOperation;
import org.cdpg.dx.aaa.organization.models.OrganizationJoinRequest;
import org.cdpg.dx.aaa.organization.models.Role;
import org.cdpg.dx.aaa.organization.models.Status;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.aaa.organization.util.DepartmentPaths;
import org.cdpg.dx.aaa.user.service.UserService;
import org.cdpg.dx.auditing.v2.model.UserActivityAuditLogBuilder;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.common.util.RoutingContextHelper;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.exception.DxConflictException;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.request.PaginationRequestBuilder;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.common.util.CpRoutingContextHelper;
import org.cdpg.dx.common.util.RequestHelper;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

import static org.cdpg.dx.aaa.common.Constants.ID;
import static org.cdpg.dx.aaa.organization.config.Constants.*;
import static org.cdpg.dx.aaa.organization.config.Constants.API_TO_DB_ORG_JOIN_REQUEST;
import static org.cdpg.dx.aaa.organization.config.Constants.REQUESTED_AT;
import static org.cdpg.dx.database.postgres.util.Constants.DEFAULT_SORTING_ORDER;

public class OrganizationJoinRequestHandler {
  private static final Logger LOGGER = LogManager.getLogger(OrganizationJoinRequestHandler.class);

  private final OrganizationService organizationService;
  private final UserService userService;
  private final EmailComposer emailComposer;
  private final URNGenerator urnGenerator;
  private final KeycloakUserService keycloakUserService;
  private final DeptAdminAccessHandler deptAdminAccess;

  public OrganizationJoinRequestHandler(
      OrganizationService organizationService,
      UserService userService,
      KeycloakUserService keycloakUserService,
      EmailComposer emailComposer,
      URNGenerator urnGenerator,
      DeptAdminAccessHandler deptAdminAccess) {
    this.deptAdminAccess = deptAdminAccess;
    this.organizationService = organizationService;
    this.userService = userService;
    this.emailComposer = emailComposer;
    this.urnGenerator = urnGenerator;
    this.keycloakUserService = keycloakUserService;
  }

  public void joinOrganisationRequest(RoutingContext ctx) {

    UUID orgId = RequestHelper.getPathParamAsUUID(ctx, "id");
    JsonObject orgRequestJson = ctx.body().asJsonObject();

    DxUser dxUser = RoutingContextHelper.fromPrincipal(ctx);
    UUID userId = dxUser.sub();
    User user = ctx.user();

    keycloakUserService.getUserById(userId)
      .compose(keycloakUser -> {

        orgRequestJson.put("user_id", userId.toString());
        orgRequestJson.put("user_name", keycloakUser.name());
        orgRequestJson.put("organization_id", orgId.toString());

        // Public request body uses "departmentId" (department vocabulary);
        // translate it to the internal requested_node_id column key that
        // OrganizationJoinRequest.fromJson expects. Omitted/null means the
        // user did not pick a department (only valid for a flat organization
        // -- OrganizationMembershipServiceImpl rejects it for a hierarchical
        // one).
        String departmentId = orgRequestJson.getString("departmentId");
        if (departmentId != null && !departmentId.isBlank()) {
          orgRequestJson.put(REQUESTED_NODE_ID, departmentId);
        }

        // Role in the selected department: "member" (default) or "dept_admin".
        // Validated by OrganizationJoinRequest.
        orgRequestJson.remove(REQUESTED_ROLE);
        String departmentRole = orgRequestJson.getString("departmentRole");
        if (departmentRole != null) {
          orgRequestJson.put(REQUESTED_ROLE, departmentRole);
        }

        // Optional free-form details the UI collects (e.g. a purpose for an
        // Admin request). Only accepted as "additionalInfo" and must be a JSON
        // object; stored and returned as-is.
        orgRequestJson.remove(ADDITIONAL_INFO);
        Object additionalInfo = orgRequestJson.getValue("additionalInfo");
        if (additionalInfo != null) {
          if (!(additionalInfo instanceof JsonObject)) {
            return Future.failedFuture(
              new DxBadRequestException("additionalInfo must be a JSON object"));
          }
          orgRequestJson.put(ADDITIONAL_INFO, additionalInfo);
        }

        OrganizationJoinRequest organizationJoinRequest =
          OrganizationJoinRequest.fromJson(orgRequestJson);

        String officialEmailStr = organizationJoinRequest.officialEmail();

        return organizationService
          .getAllOrganizationJoinRequests()
          .compose(joinRequests -> {

            for (OrganizationJoinRequest request : joinRequests) {

              // Same user + same org + active request
              if (
                request.organizationId().equals(orgId)
                  && request.userId().equals(userId)
                  && !request.status().equals(Status.WITHDRAWN.getStatus())
              ) {
                return Future.failedFuture(
                  new DxConflictException(
                    "User already has a pending/granted join request for this organization"
                  )
                );
              }

              // Same official email already in use
              if (
                request.officialEmail().equals(officialEmailStr)
                  && !Set.of(
                  Status.REJECTED.getStatus(),
                  Status.WITHDRAWN.getStatus()
                ).contains(request.status())
              ) {
                return Future.failedFuture(
                  new DxConflictException("This email has been used already!")
                );
              }
            }

            return organizationService.joinOrganizationRequest(organizationJoinRequest);
          });
      })
      .onSuccess(createdRequest -> {

        UserActivityAuditLogBuilder auditLogBuilder =
          OrganizationAuditHelper.buildOrganisationAudit(
            ctx, createdRequest.toJson(), OrganisationAuditOperation.REQUEST_ORG_JOIN);
        CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);
        ResponseBuilder.sendSuccess(ctx, "Created Join request", urnGenerator);

        emailComposer.sendEmailForJoiningOrg(createdRequest, user);
      })
      .onFailure(ctx::fail);
  }

  public void getJoinOrganisationRequests(RoutingContext ctx) {

    UUID orgId = RequestHelper.getPathParamAsUUID(ctx, "id");
    String orgIdparam = ctx.pathParam("id");

    DxUser dxUser = RoutingContextHelper.fromPrincipal(ctx);
    if (!orgIdparam.equals(dxUser.organisationId())) {
      ctx.fail(new DxForbiddenException(
        "The org id of the user and the path parameter are not same"));
      return;
    }

    // Optional ?departmentId= narrows to that department and its sub-departments.
    deptAdminAccess
      .departmentNodeIds(ctx, orgId)
      .onSuccess(
        nodeIds -> {
          Map<String, Object> additionalFilters = new HashMap<>();
          additionalFilters.put(ORGANIZATION_ID, orgId.toString());
          if (nodeIds != null) {
            additionalFilters.put(REQUESTED_NODE_ID, nodeIds);
          }
          listJoinRequests(
            ctx,
            DeptAdminAccessHandler.withDepartmentIdParam(
              ALLOWED_FILTER_MAP_FOR_ORG_JOIN_REQUEST, REQUESTED_NODE_ID),
            additionalFilters);
        })
      .onFailure(ctx::fail);
  }

  /**
   * Dept Admin: join requests for the department the caller administers and
   * all of its sub-departments, narrowable by {@code ?departmentScope=}. Only
   * reachable by a dept_admin (the route guard publishes their subtree).
   */
  public void getDepartmentJoinRequests(RoutingContext ctx) {

    UUID orgId = RequestHelper.getPathParamAsUUID(ctx, "id");
    DeptAdminScope deptAdminScope = DeptAdminAccessHandler.scopeOf(ctx);

    if (deptAdminScope == null || !deptAdminScope.organizationId().equals(orgId)) {
      ctx.fail(new DxForbiddenException(
        "The org id of the user and the path parameter are not same"));
      return;
    }

    // Always within the caller's subtree; ?departmentScope= / ?departmentId= narrow it.
    deptAdminAccess
      .departmentNodeIds(ctx, orgId)
      .onSuccess(
        nodeIds ->
          listJoinRequests(
            ctx,
            DeptAdminAccessHandler.withDepartmentParams(
              ALLOWED_FILTER_MAP_FOR_ORG_JOIN_REQUEST, APPROVAL_SCOPE),
            Map.of(
              ORGANIZATION_ID, orgId.toString(),
              APPROVAL_SCOPE, approvableScopes(nodeIds, deptAdminScope.adminOfNodeId()))))
      .onFailure(ctx::fail);
  }

  /**
   * approval_scope values a dept_admin can act on: member requests anywhere in {@code nodeIds},
   * but dept_admin requests only below their own department -- one for their own department
   * needs someone above them, so it is left out of their list.
   */
  static List<String> approvableScopes(List<String> nodeIds, UUID adminOfNodeId) {
    List<String> scopes = new ArrayList<>();
    for (String nodeId : nodeIds) {
      scopes.add(Role.USER.getRoleName() + ":" + nodeId);
      if (!nodeId.equals(adminOfNodeId.toString())) {
        scopes.add(Role.DEPT_ADMIN.getRoleName() + ":" + nodeId);
      }
    }
    return scopes;
  }

  private void listJoinRequests(
    RoutingContext ctx,
    Map<String, String> allowedFilters,
    Map<String, Object> additionalFilters) {

    PaginatedRequest request;
    try {
      request =
        PaginationRequestBuilder.from(ctx)
          .allowedFiltersDbMap(allowedFilters)
          .apiToDbMap(API_TO_DB_ORG_JOIN_REQUEST)
          .additionalFilters(additionalFilters)
          .allowedTimeFields(Set.of(REQUESTED_AT))
          .defaultTimeField(REQUESTED_AT)
          .defaultSort(REQUESTED_AT, DEFAULT_SORTING_ORDER)
          .allowedSortFields(API_TO_DB_ORG_JOIN_REQUEST.keySet())
          .build();
    } catch (RuntimeException e) {
      // Called from an async callback: fail the request rather than throw.
      ctx.fail(e);
      return;
    }

    organizationService
      .getOrganizationPendingJoinRequests(request)
      .compose(
        result ->
          departmentPaths(result.data())
            .compose(departments -> userService
            .enrichWithUserRoles(
              result.data(),
              OrganizationJoinRequest::userId,
              joinRequest -> toJsonWithDepartment(joinRequest, departments)))
            .map(
              enriched ->
                Map.entry(
                  enriched,
                  result.paginationInfo())))
      .onSuccess(entry -> {

        UserActivityAuditLogBuilder auditLogBuilder =
          OrganizationAuditHelper.buildOrganisationAudit(
            ctx, new JsonObject(), OrganisationAuditOperation.GET);
        CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

        ResponseBuilder.sendSuccess(
          ctx,
          entry.getKey(),
          entry.getValue(),
          urnGenerator);
      })
      .onFailure(ctx::fail);
  }

  public void getUserJoinOrganisationRequests(RoutingContext ctx) {

    DxUser dxUser = RoutingContextHelper.fromPrincipal(ctx);
    UUID userId = dxUser.sub();

    organizationService
        .getOrganizationJoinRequestsByUser(userId)
        .compose(
            requests ->
                departmentPaths(requests)
                    .map(
                        departments ->
                            requests.stream()
                                .map(request -> toJsonWithDepartment(request, departments))
                                .collect(Collectors.toList())))
        .onSuccess(
            result -> {
              UserActivityAuditLogBuilder auditLogBuilder =
                OrganizationAuditHelper.buildOrganisationAudit(
                  ctx, new JsonObject(), OrganisationAuditOperation.GET);
              CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

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

  /** Departments of every organisation the requests belong to, fetched in one query. */
  private Future<DepartmentPaths> departmentPaths(List<OrganizationJoinRequest> requests) {
    List<UUID> orgIds =
        requests.stream()
            .filter(request -> request.requestedNodeId() != null)
            .map(OrganizationJoinRequest::organizationId)
            .distinct()
            .toList();
    if (orgIds.isEmpty()) {
      return Future.succeededFuture(new DepartmentPaths(List.of()));
    }
    return organizationService
        .getHierarchyNodesByOrganizationIds(orgIds)
        .map(
            nodesByOrg ->
                new DepartmentPaths(
                    nodesByOrg.values().stream().flatMap(List::stream).toList()));
  }

  /** The request plus {@code "department"} (name and hierarchy) when one was selected. */
  private static JsonObject toJsonWithDepartment(
      OrganizationJoinRequest request, DepartmentPaths departments) {
    return departments.addTo(request.toJson(), request.requestedNodeId());
  }

  public void deleteUserJoinOrganisationRequests(RoutingContext ctx) {

    DxUser dxUser = RoutingContextHelper.fromPrincipal(ctx);
    UUID userId = dxUser.sub();

    UUID requestId = UUID.fromString(ctx.pathParam("id"));

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
                        UserActivityAuditLogBuilder auditLogBuilder =
                          OrganizationAuditHelper.buildOrganisationAudit(
                            ctx, new JsonObject().put(ID,requestId.toString()), OrganisationAuditOperation.DELETE_PENDING_ORG_JOIN_REQUEST);
                        CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

                        ResponseBuilder.sendSuccess(
                            ctx, "Join organisation request deleted successfully", urnGenerator);
                        return Future.succeededFuture(true);
                      });
            })
        .onFailure(ctx::fail);
  }

  public void approveJoinOrganisationRequests(RoutingContext ctx) {

    String orgIdparam = ctx.pathParam("org_id");

    DxUser dxUser = RoutingContextHelper.fromPrincipal(ctx);
    if (orgIdparam != null && !orgIdparam.equals(dxUser.organisationId())) {
      ctx.fail(new DxForbiddenException(
        "The org id of the user and the path parameter are not same"));
      return;
    }

    JsonObject orgRequestJson = ctx.body().asJsonObject();
    UUID requestId = RequestHelper.getPathParamAsUUID(ctx, "req_id");
    Status status = Status.fromString(orgRequestJson.getString("status"));

    // The request must belong to the caller's own organisation -- a flat
    // organisation's update path has no approver check of its own.
    // (Whether a dept_admin covers its department is enforced by the
    // approval engine in the service.)
    organizationService
      .getOrganizationJoinRequestById(requestId)
      .compose(
        joinRequest -> {
          if (joinRequest == null
            || !joinRequest.organizationId().toString().equals(dxUser.organisationId())) {
            return Future.failedFuture(new DxNotFoundException("Request Not Found"));
          }
          return organizationService.updateOrganizationJoinRequestStatus(
            requestId, status, dxUser.sub());
        })
      .onSuccess(
        approved -> {
          if (!approved) {
            ctx.fail(
              new DxNotFoundException("Request Not Found"));
            return;
          }

          UserActivityAuditLogBuilder auditLogBuilder =
            OrganizationAuditHelper.buildOrganisationAudit(
              ctx, new JsonObject().put(ID,requestId.toString()), OrganisationAuditOperation.UPDATE_ORG_JOIN_REQUEST);
          CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

          emailComposer
            .sendUserEmailForOrgJoinRequestApproval(
              requestId, status)
            .onFailure(
              err ->
                LOGGER.error(
                  "Email send failed", err));

          String message = status == Status.GRANTED
            ? "Organisation join request approved successfully"
            : "Organisation join request rejected successfully";
          ResponseBuilder.sendSuccess(ctx, message, urnGenerator);
        })
      .onFailure(ctx::fail);
  }

  public void withdrawJoinRequest(RoutingContext ctx) {

    DxUser dxUser = RoutingContextHelper.fromPrincipal(ctx);
    UUID userId = dxUser.sub();
    UUID orgJoinReqId = UUID.fromString(ctx.pathParam("id"));

    organizationService
      .withdrawJoinRequest(userId, orgJoinReqId)
      .onSuccess(res -> {

        UserActivityAuditLogBuilder auditLogBuilder =
          OrganizationAuditHelper.buildOrganisationAudit(
            ctx, new JsonObject().put(ID,orgJoinReqId.toString()), OrganisationAuditOperation.WITHDRAW_PENDING_ORG_JOIN_REQUEST);
        CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

        ResponseBuilder.sendSuccess(ctx, "Organisation join request withdrawn successfully", urnGenerator);
      })
      .onFailure(ctx::fail);
  }
}