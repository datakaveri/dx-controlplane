package org.cdpg.dx.aaa.activity.controller;

import static org.cdpg.dx.aaa.apiserver.OperationIds.OP_GET_ACTIVITY_FOR_ADMIN;
import static org.cdpg.dx.aaa.apiserver.OperationIds.OP_GET_ACTIVITY_FOR_CONSUMER;
import static org.cdpg.dx.auditing.v2.Constant.ActivityApiParamConstants.*;
import static org.cdpg.dx.auditing.v2.Constant.UserActivityAuditSchema.CREATED_AT;
import static org.cdpg.dx.auditing.v2.Constant.UserActivityAuditSchema.USER_ID;
import static org.cdpg.dx.database.postgres.util.Constants.DEFAULT_SORTING_FIELD;
import static org.cdpg.dx.database.postgres.util.Constants.DEFAULT_SORTING_ORDER;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.apiserver.ApiController;
import org.cdpg.dx.aaa.activity.service.UserActivityAuditLogService;
import org.cdpg.dx.auditing.v2.util.Util;
import org.cdpg.dx.auth.authorization.handler.AuthorizationHandler;
import org.cdpg.dx.auth.authorization.model.DxRole;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.request.PaginationRequestBuilder;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.common.util.RoutingContextHelper;

public class ActivityController implements ApiController {
  private static final Logger LOGGER = LogManager.getLogger(ActivityController.class);
  private final UserActivityAuditLogService userActivityAuditLogService;
  private final URNGenerator urnGenerator;

  public ActivityController(
      UserActivityAuditLogService userActivityAuditLogService, URNGenerator urnGenerator) {
    this.userActivityAuditLogService = userActivityAuditLogService;
    this.urnGenerator = urnGenerator;
  }

  @Override
  public void register(RouterBuilder builder) {

    Handler<RoutingContext> adminAccessHandler =
        AuthorizationHandler.forRoles(DxRole.ORG_ADMIN, DxRole.COS_ADMIN);
    Handler<RoutingContext> consumerAccessHandler = AuthorizationHandler.forRoles(DxRole.CONSUMER);

    builder
        .operation(OP_GET_ACTIVITY_FOR_CONSUMER)
        .handler(consumerAccessHandler)
        .handler(this::handleGetAllActivityLogsForUser);
    builder
        .operation(OP_GET_ACTIVITY_FOR_ADMIN)
        .handler(adminAccessHandler)
        .handler(this::handleGetAllActivityLogsForAdmin);
  }

  private void handleGetAllActivityLogsForUser(RoutingContext context) {
    LOGGER.info("handleGetAllActivityLogsForUser() started");

    User user = context.user();

    Map<String, Object> additionalFilters = Map.of(USER_ID, user.subject());

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
              LOGGER.info("Successfully fetched activity logs for user: {}", user.subject());
              if (pagedResult.data().isEmpty()) {
                LOGGER.info("No activity logs found for user: {}", user.subject());
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

    DxUser user = RoutingContextHelper.fromPrincipal(context);

    PaginatedRequest request =
        PaginationRequestBuilder.from(context)
            .allowedFiltersDbMap(Util.getAllowedFilterMapForAdmin(user))
            .additionalFilters(Util.getAdditionalFilters(user))
            .apiToDbMap(API_TO_DB_FIELD_MAP_V2)
            .allowedTimeFields(Set.of(CREATED_AT))
            .defaultTimeField(CREATED_AT)
            .defaultSort(DEFAULT_SORTING_FIELD, DEFAULT_SORTING_ORDER)
            .allowedSortFields(ALLOWED_SORT_FIELDS_V2)
            .build();

    LOGGER.info("PaginatedRequest created for handleGetAllActivityLogsForAdmin:  {}", request);

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
