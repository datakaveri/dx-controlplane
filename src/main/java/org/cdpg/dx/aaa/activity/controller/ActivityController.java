package org.cdpg.dx.aaa.activity.controller;

import static org.cdpg.dx.aaa.activity.util.ActivityConstants.*;
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
import org.cdpg.dx.aaa.activity.service.ActivityLogService;
import org.cdpg.dx.aaa.activity.service.ActivityService;
import org.cdpg.dx.aaa.activity.util.Util;
import org.cdpg.dx.aaa.apiserver.ApiController;
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
  private final ActivityService activityService;
  private final ActivityLogService activityLogService;
  private final URNGenerator urnGenerator;

  public ActivityController(
      ActivityService activityService,
      ActivityLogService activityLogService,
      URNGenerator urnGenerator) {
    this.activityService = activityService;
    this.activityLogService = activityLogService;
    this.urnGenerator = urnGenerator;
  }

  @Override
  public void register(RouterBuilder builder) {

    Handler<RoutingContext> adminAccessHandler =
        AuthorizationHandler.forRoles(DxRole.ORG_ADMIN, DxRole.COS_ADMIN);
    Handler<RoutingContext> consumerAccessHandler = AuthorizationHandler.forRoles(DxRole.CONSUMER);

    builder
        .operation("get-ActivityLogs-for-consumer")
        .handler(consumerAccessHandler)
        .handler(this::handleGetAllActivityLogsForUser);
    builder
        .operation("get-activityLogs-for-admin")
        .handler(adminAccessHandler)
        .handler(this::handleGetAllActivityLogsForAdmin);
  }

  private void handleGetAllActivityLogsForUser(RoutingContext context) {
    LOGGER.info("handleGetAllActivityLogsForUser() started");

    User user = context.user();

    Map<String, Object> additionalFilters =
        Map.of(USER_ID, user.subject(), MYACTIVITY_ENABLED, true);

    PaginatedRequest request =
        PaginationRequestBuilder.from(context)
            .allowedFiltersDbMap(ALLOWED_FILTER_MAP_FOR_USER)
            .additionalFilters(additionalFilters)
            .allowedTimeFields(Set.of(CREATED_AT))
            .defaultTimeField(CREATED_AT)
            .defaultSort(DEFAULT_SORTING_FIELD, DEFAULT_SORTING_ORDER)
            .allowedSortFields(ALLOWED_SORT_FEILDS)
            .build();

    LOGGER.info("PaginatedRequest created for getActivityLogForUser:  {}", request);

    activityLogService
        .getActivityLogForConsumer(request)
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

    Map<String, Object> additionalFilters = Util.getAdditionalFilters(user);
    Map<String, String> allowedFilterMap = Util.getAllowedFilterMapForAdmin(user);

    PaginatedRequest request =
        PaginationRequestBuilder.from(context)
            .allowedFiltersDbMap(allowedFilterMap)
            .additionalFilters(additionalFilters)
            .allowedTimeFields(Set.of(CREATED_AT))
            .defaultTimeField(CREATED_AT)
            .defaultSort(DEFAULT_SORTING_FIELD, DEFAULT_SORTING_ORDER)
            .allowedSortFields(ALLOWED_SORT_FEILDS)
            .build();

    LOGGER.info("PaginatedRequest created for handleGetAllActivityLogsForAdmin:  {}", request);

    activityLogService
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
