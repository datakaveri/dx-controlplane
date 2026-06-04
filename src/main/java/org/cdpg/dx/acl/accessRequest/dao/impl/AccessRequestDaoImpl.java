package org.cdpg.dx.acl.accessRequest.dao.impl;

import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.*;
import static org.cdpg.dx.database.postgres.models.Condition.Operator.EQUALS;
import static org.cdpg.dx.database.postgres.models.Condition.Operator.IN;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.acl.accessRequest.dao.AccessRequestDao;
import org.cdpg.dx.acl.accessRequest.dao.model.AccessRequestDto;
import org.cdpg.dx.acl.accessRequest.dao.model.AccessRequestSummary;
import org.cdpg.dx.acl.accessRequest.dao.model.Status;
import org.cdpg.dx.common.exception.*;
import org.cdpg.dx.database.postgres.base.dao.AbstractBaseDAO;
import org.cdpg.dx.database.postgres.models.Condition;
import org.cdpg.dx.database.postgres.models.OrderBy;
import org.cdpg.dx.database.postgres.models.SelectQuery;
import org.cdpg.dx.database.postgres.models.UpdateQuery;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class AccessRequestDaoImpl extends AbstractBaseDAO<AccessRequestDto>
    implements AccessRequestDao {
  private static final Logger LOGGER = LogManager.getLogger(AccessRequestDaoImpl.class);

  public AccessRequestDaoImpl(
      PostgresService postgresService,
      String tableName,
      String idField,
      Function<JsonObject, AccessRequestDto> fromJson) {
    super(postgresService, tableName, idField, fromJson);
  }

  // select * from request where consumer_id = givenId and status IS GRANTED or PENDING AND item_id
  // = itemId
  public Future<Boolean> isAccessRequestPresent(UUID consumerId, UUID itemId) {
    Condition condition = new Condition();
    Condition conditionWithStatus =
        new Condition()
            .setColumn(DB_STATUS)
            .setValues(List.of(Status.GRANTED, Status.PENDING))
            .setOperator(IN);
    Condition conditionWithConsumer =
        new Condition()
            .setColumn(DB_CONSUMER_ID)
            .setValues(List.of(consumerId.toString()))
            .setOperator(EQUALS);
    Condition conditionWithItemId =
        new Condition()
            .setColumn(DB_ITEM_ID)
            .setValues(List.of(itemId.toString()))
            .setOperator(EQUALS);

    condition
        .setConditions(List.of(conditionWithStatus, conditionWithConsumer, conditionWithItemId))
        .setLogicalOperator(Condition.LogicalOperator.AND)
        .setGroup(true);
    SelectQuery selectQuery =
        new SelectQuery().setTable(tableName).setColumns(List.of("*")).setCondition(condition);
    LOGGER.info("Select Query : {}", selectQuery.toSQL());
    return postgresService
        .select(selectQuery, false)
        .compose(
            queryResult -> {
              LOGGER.info("Access Requests details : {}", queryResult.toString());
              if (!queryResult.getRows().isEmpty()) {
                return Future.succeededFuture(true);
              }
              return Future.succeededFuture(false);
            });
  }

  public Future<List<AccessRequestDto>> getActivePendingRequests(UUID consumerId, UUID itemId) {
    Condition condition =
        new Condition()
            .setGroup(true)
            .setLogicalOperator(Condition.LogicalOperator.AND)
            .setConditions(
                List.of(
                    new Condition()
                        .setColumn(DB_CONSUMER_ID)
                        .setValues(List.of(consumerId.toString()))
                        .setOperator(EQUALS),
                    new Condition()
                        .setColumn(DB_ITEM_ID)
                        .setValues(List.of(itemId.toString()))
                        .setOperator(EQUALS),
                    new Condition()
                        .setColumn(DB_STATUS)
                        .setValues(List.of(Status.PENDING.getStatus()))
                        .setOperator(IN)));

    SelectQuery query =
        new SelectQuery().setTable(tableName).setColumns(List.of("*")).setCondition(condition);

    return postgresService
        .select(query, false)
        .map(
            result ->
                result.getRows().stream()
                    .map(row -> new AccessRequestDto((JsonObject) row))
                    .toList());
  }

  // select * from request where request_id = given id AND provider_id = id AND itemOrgId =
  // providerOrganizationId From token AND status = PENDING
  // select * from request where request_id = given id  AND itemOrgId = org Admin organizationId
  // From token AND status = PENDING
  public Future<Boolean> ownershipCheck(
      UUID requestId, UUID providerId, UUID providerOrganizationId, boolean isUserOrgAdmin) {
    Promise<Boolean> promise = Promise.promise();

    get(requestId)
        .onSuccess(
            accessRequestDto -> {
              boolean doesProviderOrganizationMatch =
                  accessRequestDto.getItemOrganizationId() != null
                      && accessRequestDto
                          .getItemOrganizationId()
                          .equals(providerOrganizationId.toString());
              boolean doesProviderIdMatch =
                  accessRequestDto.getProviderId() != null
                      && accessRequestDto.getProviderId().equals(providerId.toString());
              /*if the provider ID does not match check if the user is org Admin */
              if ((doesProviderIdMatch && doesProviderOrganizationMatch)
                  || (isUserOrgAdmin && doesProviderOrganizationMatch)) {
                promise.complete(true);
              } else {
                LOGGER.error(
                    "Ownership check failed: requestId {} does not belong to providerId {}",
                    requestId,
                    providerId);
                promise.fail(
                    new DxForbiddenException(
                        "The access request does not belong to the given provider"));
              }
            })
        .onFailure(
            err -> {
              if (err.getMessage().contains("no rows")) {
                LOGGER.error("Access request not found for requestId: {}", requestId);
                promise.fail(new DxNotFoundException("Access request not found"));
                return;
              }
              LOGGER.error(
                  "Failed to fetch access request with requestId {}: {}",
                  requestId,
                  err.getMessage());
              promise.fail(err);
            });

    return promise.future();
  }

  @Override
  public Future<AccessRequestDto> approveAccessRequest(
      UUID requestId, String granted, LocalDateTime expiryTime) {
    return get(requestId)
        .compose(
            request -> {
              Condition requestIdCondition =
                  new Condition()
                      .setColumn(DB_REQUEST_ID)
                      .setValues(List.of(requestId.toString()))
                      .setOperator(EQUALS);
              UpdateQuery updateQuery =
                  new UpdateQuery()
                      .setCondition(requestIdCondition)
                      .setColumns(List.of(DB_STATUS, DB_EXPIRY_AT))
                      .setValues(List.of(Status.GRANTED.toString(), expiryTime.toString()))
                      .setTable(tableName);
              LOGGER.info("Update Query : {}", updateQuery.toSQL());
              return postgresService.update(updateQuery);
            })
        .compose(
            queryResult -> {
              AccessRequestDto accessRequestDto = AccessRequestDto.fromJson(queryResult.toJson());
              LOGGER.info("Access Request Approved : {}", accessRequestDto.toString());
              return Future.succeededFuture(accessRequestDto);
            });
  }

  // select * from request where consumer_id = givenId and status = APPROVED AND item_id = itemId
  // AND expiry_at > now() ORDER BY updated_at DESC
  public Future<Boolean> hasAccess(String consumerId, String itemId) {
    LocalDateTime now = LocalDateTime.now();
    Condition condition =
        new Condition().setGroup(true).setLogicalOperator(Condition.LogicalOperator.AND);
    condition.setConditions(
        List.of(
            new Condition().setColumn(DB_ITEM_ID).setValues(List.of(itemId)).setOperator(EQUALS),
            new Condition()
                .setColumn(DB_CONSUMER_ID)
                .setValues(List.of(consumerId))
                .setOperator(EQUALS)));

    SelectQuery selectQuery =
        new SelectQuery()
            .setTable(tableName)
            .setColumns(List.of(DB_STATUS, DB_EXPIRY_AT))
            .setOrderBy(List.of(new OrderBy(DB_UPDATED_AT, OrderBy.Direction.DESC)))
            .setCondition(condition);

    LOGGER.debug("Executing access check query: {}", selectQuery);

    return postgresService
        .select(selectQuery, false)
        .compose(
            queryResult -> {
              if (queryResult == null || queryResult.getRows().isEmpty()) {
                LOGGER.warn(
                    "Access denied: No active access request found for consumerId={} and itemId={}",
                    consumerId,
                    itemId);
                return Future.failedFuture(
                    new DxForbiddenNoAccessException(
                        "User does not have access to the given item"));
              }
              for (Object object : queryResult.getRows()) {
                JsonObject row = (JsonObject) object;
                Status status = Status.fromString(row.getString(DB_STATUS));
                if (Status.GRANTED.equals(status)) {
                  String expiryAt = row.getString(DB_EXPIRY_AT);
                  if (expiryAt != null && LocalDateTime.parse(expiryAt).isAfter(now)) {
                    LOGGER.info(
                        "Access granted for consumerId={} on itemId={}", consumerId, itemId);
                    return Future.succeededFuture(true);
                  }
                } else if (Status.PENDING.equals(status)) {
                  LOGGER.warn(
                      "Access request is still pending for consumerId={} and itemId={}",
                      consumerId,
                      itemId);
                  return Future.failedFuture(
                      new DxForbiddenPendingAccessException(
                          "Access request is still pending for the given item"));
                } else if (Status.REJECTED.equals(status)) {
                  LOGGER.warn(
                      "Access request rejected for consumerId={} and itemId={}",
                      consumerId,
                      itemId);
                  return Future.failedFuture(
                      new DxForbiddenAccessRejectedException(
                          "Access request rejected for the given item"));
                }
              }
              LOGGER.warn(
                  "Access denied: No valid GRANTED access found for consumerId={} and itemId={}",
                  consumerId,
                  itemId);
              return Future.failedFuture(
                  new DxForbiddenNoAccessException("User does not have access to the given item"));
            });
  }

  public Future<AccessRequestSummary> getAccessSummary(String consumerId, String itemId) {

    LocalDateTime now = LocalDateTime.now();

    Condition condition =
        new Condition().setGroup(true).setLogicalOperator(Condition.LogicalOperator.AND);

    condition.setConditions(
        List.of(
            new Condition().setColumn(DB_ITEM_ID).setValues(List.of(itemId)).setOperator(EQUALS),
            new Condition()
                .setColumn(DB_CONSUMER_ID)
                .setValues(List.of(consumerId))
                .setOperator(EQUALS)));

    SelectQuery selectQuery =
        new SelectQuery()
            .setTable(tableName)
            .setColumns(List.of("*"))
            .setOrderBy(List.of(new OrderBy(DB_UPDATED_AT, OrderBy.Direction.DESC)))
            .setCondition(condition);

    return postgresService
        .select(selectQuery, false)
        .map(
            queryResult -> {
              boolean hasAccess = false;
              boolean hasRejectedRequests = false;
              List<AccessRequestDto> pendingRequests = new ArrayList<>();

              if (queryResult == null || queryResult.getRows().isEmpty()) {

                return new AccessRequestSummary(false, false, false, List.of());
              }

              for (Object object : queryResult.getRows()) {

                JsonObject row = (JsonObject) object;

                Status status = Status.fromString(row.getString(DB_STATUS));

                if (Status.GRANTED.equals(status)) {

                  String expiryAt = row.getString(DB_EXPIRY_AT);

                  if (expiryAt != null && LocalDateTime.parse(expiryAt).isAfter(now)) {

                    hasAccess = true;
                  }

                } else if (Status.PENDING.equals(status)) {

                  pendingRequests.add(new AccessRequestDto(row));
                } else if (Status.REJECTED.equals(status)) {
                  hasRejectedRequests = true;
                }
              }

              return new AccessRequestSummary(
                  hasAccess, !pendingRequests.isEmpty(), hasRejectedRequests, pendingRequests);
            });
  }
}
