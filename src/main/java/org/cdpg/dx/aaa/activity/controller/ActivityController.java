package org.cdpg.dx.aaa.activity.controller;

import static org.cdpg.dx.aaa.apiserver.OperationIds.OP_GET_ACTIVITY_FOR_ADMIN;
import static org.cdpg.dx.aaa.apiserver.OperationIds.OP_GET_ACTIVITY_FOR_CONSUMER;
import static org.cdpg.dx.auditing.v2.Constant.ActivityApiParamConstants.*;
import static org.cdpg.dx.auditing.v2.Constant.UserActivityAuditSchema.CREATED_AT;
import static org.cdpg.dx.auditing.v2.Constant.UserActivityAuditSchema.USER_ID;
import static org.cdpg.dx.database.postgres.util.Constants.DEFAULT_SORTING_FIELD;
import static org.cdpg.dx.database.postgres.util.Constants.DEFAULT_SORTING_ORDER;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.apiserver.ApiController;
import org.cdpg.dx.aaa.activity.service.UserActivityAuditLogService;
import org.cdpg.dx.aaa.organization.dao.OrganizationUserDAO;
import org.cdpg.dx.aaa.organization.handler.DeptAdminAccessHandler;
import org.cdpg.dx.aaa.organization.models.DeptAdminScope;
import org.cdpg.dx.auditing.v2.util.Util;
import org.cdpg.dx.auth.authorization.handler.AuthorizationHandler;
import org.cdpg.dx.auth.authorization.model.AuthLevel;
import org.cdpg.dx.auth.authorization.model.AuthorizationContext;
import org.cdpg.dx.auth.authorization.model.ScopeRule;
import org.cdpg.dx.auth.model.Scopes;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.request.PaginationRequestBuilder;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.common.util.RoutingContextHelper;

public class ActivityController implements ApiController {
  private static final Logger LOGGER = LogManager.getLogger(ActivityController.class);
  private final UserActivityAuditLogService userActivityAuditLogService;
  private final URNGenerator urnGenerator;
  private final DeptAdminAccessHandler deptAdminAccess;
  private final OrganizationUserDAO organizationUserDAO;

  public ActivityController(
      UserActivityAuditLogService userActivityAuditLogService,
      URNGenerator urnGenerator,
      DeptAdminAccessHandler deptAdminAccess,
      OrganizationUserDAO organizationUserDAO) {
    this.userActivityAuditLogService = userActivityAuditLogService;
    this.urnGenerator = urnGenerator;
    this.deptAdminAccess = deptAdminAccess;
    this.organizationUserDAO = organizationUserDAO;
  }

  @Override
  public void register(RouterBuilder builder) {
    builder
        .operation(OP_GET_ACTIVITY_FOR_CONSUMER)
        .handler(this::handleGetAllActivityLogsForUser);
    builder
        .operation(OP_GET_ACTIVITY_FOR_ADMIN)
        // A dept_admin gets the dept scopes from the database here and is then
        // always restricted to the users of their department subtree.
        .handler(
            deptAdminAccess.withDeptScopes(
                AuthorizationHandler.forScopesWithContext(
                    ScopeRule.platform(Scopes.USER_MANAGEMENT),
                    ScopeRule.org(Scopes.ORG_USER_MANAGEMENT),
                    ScopeRule.org(Scopes.DEPT_USER_MANAGEMENT))))
        .handler(this::handleGetAllActivityLogsForAdmin);
  }

  private void handleGetAllActivityLogsForUser(RoutingContext context) {
    LOGGER.info("handleGetAllActivityLogsForUser() started");

    DxUser dxUser;

    try {
      dxUser = RoutingContextHelper.fromPrincipal(context);
    } catch (Exception e) {
      LOGGER.error("Error extracting user from token: {}", e.getMessage(), e);
      context.fail(new DxForbiddenException("Invalid user"));
      return;
    }

    Map<String, Object> additionalFilters = new HashMap<>();
    additionalFilters.put(USER_ID, dxUser.sub().toString());
    additionalFilters.putAll(Util.getDelegationFilters(context));

    PaginatedRequest request =
        PaginationRequestBuilder.from(context)
            .allowedFiltersDbMap(ALLOWED_FILTER_MAP_FOR_CONSUMER_V2)
            .additionalFilters(additionalFilters)
            .apiToDbMap(API_TO_DB_FIELD_MAP_V2)
            .allowedTimeFields(Set.of(CREATED_AT))
            .defaultTimeField(CREATED_AT)
            .defaultSort(DEFAULT_SORTING_FIELD, DEFAULT_SORTING_ORDER)
            .allowedSortFields(ALLOWED_SORT_FIELDS_V2)
            .build();

    LOGGER.info("PaginatedRequest created for getActivityLogForUser:  {}", request);

    userActivityAuditLogService
        .getUserActivityLogForConsumer(request)
        .onSuccess(
            pagedResult -> {
              LOGGER.info("Successfully fetched activity logs for user: {}", dxUser.sub());
              if (pagedResult.data().isEmpty()) {
                LOGGER.info("No activity logs found for user: {}", dxUser.sub());
                ResponseBuilder.sendNoContent(context, urnGenerator);
                return;
              }
              LOGGER.info("Paged Result: {}", pagedResult.data().get(0).toJson());

              JsonArray resultArr = new JsonArray();
              pagedResult.data().forEach(item -> resultArr.add(item.toJson()));

              ResponseBuilder.sendSuccess(
                  context, resultArr, pagedResult.paginationInfo(), urnGenerator);
            })
        .onFailure(
            failure -> {
              LOGGER.error("Failed to fetch activity logs: {}", failure.getMessage(), failure);
              context.fail(failure);
            });
  }

  private void handleGetAllActivityLogsForAdmin(RoutingContext context) {
    LOGGER.info("handleGetAllActivityLogsForAdmin() started");

    AuthorizationContext authCtx = context.get(AuthorizationContext.KEY);
    Map<String, String> allowedFilters = new HashMap<>(Util.getAllowedFilterMapForAdmin(authCtx));
    Map<String, Object> additionalFilter = new HashMap<>(Util.getAdditionalFilters(authCtx));

    LOGGER.info("Admin auth level: {}, allowed filters: {}", authCtx.getLevel(), allowedFilters);

    DeptAdminScope deptScope = DeptAdminAccessHandler.scopeOf(context);
    // Dept scopes in a token are only a UI hint and may be stale; only a caller the
    // database confirms as dept_admin (deptScope != null) may use them here.
    if (deptScope == null && Scopes.DEPT_USER_MANAGEMENT.equals(authCtx.getScope())) {
      context.fail(new DxForbiddenException("Caller is not a department admin"));
      return;
    }

    boolean departmentFilter =
        deptScope != null
            || context.queryParams().contains(DeptAdminAccessHandler.DEPARTMENT_ID_PARAM);

    if (!departmentFilter) {
      fetchAdminActivityLogs(context, allowedFilters, additionalFilter);
      return;
    }

    if (authCtx.getLevel() != AuthLevel.ORG || authCtx.getOrgId() == null) {
      context.fail(
          new DxBadRequestException("departmentId is only supported for an organisation admin"));
      return;
    }

    // The department params are accepted here and always overridden by the
    // user_id filter computed from them.
    allowedFilters.put(DeptAdminAccessHandler.DEPARTMENT_ID_PARAM, USER_ID);
    if (deptScope != null) {
      allowedFilters.put(DeptAdminAccessHandler.DEPARTMENT_SCOPE_PARAM, USER_ID);
    }

    UUID orgId = UUID.fromString(authCtx.getOrgId());
    deptAdminAccess
        .departmentNodeIds(context, orgId)
        .compose(nodeIds -> departmentUserIds(orgId, nodeIds))
        .onSuccess(
            userIds -> {
              additionalFilter.put(USER_ID, narrowToRequestedUser(context, userIds));
              fetchAdminActivityLogs(context, allowedFilters, additionalFilter);
            })
        .onFailure(context::fail);
  }

  /** Members whose department is one of {@code nodeIds}; never empty (see NO_DEPARTMENT). */
  private Future<List<String>> departmentUserIds(UUID orgId, List<String> nodeIds) {
    return organizationUserDAO
        .getAllWithFilters(
            Map.of(
                org.cdpg.dx.aaa.organization.config.Constants.ORGANIZATION_ID, orgId.toString(),
                org.cdpg.dx.aaa.organization.config.Constants.HIERARCHY_NODE_ID, nodeIds))
        .map(
            users -> {
              List<String> ids =
                  users.stream().map(user -> user.userId().toString()).distinct().toList();
              return ids.isEmpty() ? List.of(DeptAdminScope.NO_DEPARTMENT) : ids;
            });
  }

  /** A {@code ?userId=} filter still applies, but only to a user within the department. */
  static List<String> narrowToRequestedUser(RoutingContext context, List<String> userIds) {
    String requested = context.queryParams().get("userId");
    if (requested == null || requested.isBlank()) {
      return userIds;
    }
    return userIds.contains(requested.trim())
        ? List.of(requested.trim())
        : List.of(DeptAdminScope.NO_DEPARTMENT);
  }

  private void fetchAdminActivityLogs(
      RoutingContext context,
      Map<String, String> allowedFilters,
      Map<String, Object> additionalFilter) {
    PaginatedRequest request;
    try {
      request =
          PaginationRequestBuilder.from(context)
              .allowedFiltersDbMap(allowedFilters)
              .additionalFilters(additionalFilter)
              .apiToDbMap(API_TO_DB_FIELD_MAP_V2)
              .allowedTimeFields(Set.of(CREATED_AT))
              .defaultTimeField(CREATED_AT)
              .defaultSort(DEFAULT_SORTING_FIELD, DEFAULT_SORTING_ORDER)
              .allowedSortFields(ALLOWED_SORT_FIELDS_V2)
              .build();
    } catch (RuntimeException e) {
      context.fail(e);
      return;
    }

    LOGGER.debug("PaginatedRequest created for handleGetAllActivityLogsForAdmin:  {}", request);

    userActivityAuditLogService
        .getAllActivityLogsForAdmin(request)
        .onSuccess(
            pagedResult -> {
              LOGGER.info("Successfully fetched all activity logs for admin");
              JsonArray resultArr = new JsonArray();
              pagedResult.data().forEach(item -> resultArr.add(item.toJson()));

              ResponseBuilder.sendSuccess(
                  context, resultArr, pagedResult.paginationInfo(), urnGenerator);
            })
        .onFailure(
            failure -> {
              LOGGER.error("Failed to fetch activity logs: {}", failure.getMessage(), failure);
              context.fail(failure);
            });
  }
}
