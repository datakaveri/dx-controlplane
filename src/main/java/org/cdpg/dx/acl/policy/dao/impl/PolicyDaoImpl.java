package org.cdpg.dx.acl.policy.dao.impl;

import static org.cdpg.dx.aaa.common.Constants.ACTIVE;
import static org.cdpg.dx.aaa.common.Constants.DETAIL;
import static org.cdpg.dx.aaa.common.Constants.TITLE;
import static org.cdpg.dx.aaa.common.Constants.TYPE;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_ADDITIONAL_INFO;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_ASSET_ORGANIZATION_ID;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_CONSTRAINTS;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_CONSUMER_ID;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_CREATED_AT;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_EXPIRY_AT;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_FEEDBACK_TO_CONSUMER;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_ID;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_ITEM_ID;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_OWNER_ID;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_POLICY_ID;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_POLICY_TYPE;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_PROVIDER_COMMENT;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_REQUEST_ID;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_STATUS;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.POLICY_TABLE;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.USER_TABLE;
import static org.cdpg.dx.common.HttpStatusCode.INTERNAL_SERVER_ERROR;
import static org.cdpg.dx.database.postgres.util.ConditionBuilder.fromFilters;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.acl.policy.dao.PolicyDao;
import org.cdpg.dx.acl.policy.dao.model.PolicyDto;
import org.cdpg.dx.acl.policy.service.model.CreatePolicyRequest;
import org.cdpg.dx.common.HttpStatusCode;
import org.cdpg.dx.common.exception.BaseDxException;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.database.postgres.base.dao.AbstractBaseDAO;
import org.cdpg.dx.database.postgres.models.Condition;
import org.cdpg.dx.database.postgres.models.InsertQuery;
import org.cdpg.dx.database.postgres.models.Join;
import org.cdpg.dx.database.postgres.models.OrderBy;
import org.cdpg.dx.database.postgres.models.PaginatedResult;
import org.cdpg.dx.database.postgres.models.QueryResult;
import org.cdpg.dx.database.postgres.models.SelectQuery;
import org.cdpg.dx.database.postgres.models.UpdateQuery;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class PolicyDaoImpl extends AbstractBaseDAO<PolicyDto> implements PolicyDao {
  private static final Logger LOGGER = LogManager.getLogger(PolicyDaoImpl.class);
  public PolicyDaoImpl(PostgresService postgresService) {
    super(postgresService, POLICY_TABLE, DB_POLICY_ID, PolicyDto::new);
  }

  @Override
  public Future<QueryResult> checkExistingPoliciesForIds(
      UUID itemId, UUID ownerId, String userId) {
    Promise<QueryResult> promise = Promise.promise();

    Condition condition =
        new Condition(
            List.of(
                new Condition(DB_ITEM_ID, Condition.Operator.EQUALS, List.of(itemId.toString())),
                new Condition(DB_STATUS, Condition.Operator.EQUALS, List.of(ACTIVE)),
                new Condition(DB_CONSUMER_ID, Condition.Operator.EQUALS, List.of(userId)),
                new Condition(
                    DB_EXPIRY_AT,
                    Condition.Operator.GREATER,
                    List.of(LocalDateTime.now().toString()))),
            Condition.LogicalOperator.AND);

    SelectQuery query =
        new SelectQuery()
            .setTable(POLICY_TABLE)
            .setColumns(List.of(DB_ID, DB_CONSTRAINTS, DB_EXPIRY_AT, DB_CREATED_AT))
            .setCondition(condition);

    postgresService
        .select(query, false)
        .onSuccess(promise::complete)
        .onFailure(
            err -> {
              LOGGER.error("checkExistingPoliciesForIds failed: {}", err.getMessage());
              promise.fail(generateErrorResponse(INTERNAL_SERVER_ERROR, err.getMessage()));
            });

    return promise.future();
  }

  @Override
  public Future<QueryResult> checkExistingPoliciesForIds(
      List<CreatePolicyRequest> requests, UUID ownerId) {
    Promise<QueryResult> promise = Promise.promise();

    List<Object> itemIds =
        requests.stream().map(req -> req.getItemId().toString()).collect(Collectors.toList());

    List<Object> consumerIds =
        requests.stream().map(CreatePolicyRequest::getUserId).collect(Collectors.toList());

    Condition cond =
        new Condition(
            List.of(
                new Condition(DB_ITEM_ID, Condition.Operator.EQUALS, itemIds),
                new Condition(DB_STATUS, Condition.Operator.EQUALS, List.of(ACTIVE)),
                new Condition(DB_CONSUMER_ID, Condition.Operator.EQUALS, consumerIds),
                new Condition(DB_EXPIRY_AT, Condition.Operator.GREATER,
                    List.of(LocalDateTime.now().toString()))),
            Condition.LogicalOperator.AND);

    SelectQuery query =
        new SelectQuery()
            .setTable(POLICY_TABLE)
            .setColumns(List.of(DB_ID, DB_CONSTRAINTS))
            .setCondition(cond);

    postgresService
        .select(query, false)
        .onSuccess(promise::complete)
        .onFailure(
            err -> {
              LOGGER.error("getExistingPolicies failed: {}", err.getMessage());
              promise.fail(generateErrorResponse(INTERNAL_SERVER_ERROR, err.getMessage()));
            });

    return promise.future();
  }

  @Override
  public Future<List<QueryResult>> insertPolicies(
      List<CreatePolicyRequest> createPolicyRequestList, UUID userId) {
    // Compose futures for each policy insert
    List<Future<QueryResult>> insertFutures =
        createPolicyRequestList.stream()
            .map(
                req -> {
                  List<String> columns = new ArrayList<>();
                  List<Object> values = new ArrayList<>();

                  if (req.getUserId() != null) {
                    columns.add(DB_CONSUMER_ID);
                    values.add(req.getUserId());
                  }

                  // item_organization_id is null for independent providers — omit the column
                  // so the DB defaults to NULL rather than causing a param count mismatch
                  if (req.getItemOrganizationId() != null) {
                    columns.add(DB_ASSET_ORGANIZATION_ID);
                    values.add(req.getItemOrganizationId());
                  }
                  if (req.getRequestId() != null) {
                    columns.add(DB_REQUEST_ID);
                    values.add(req.getRequestId());
                  }

                  columns.add(DB_ITEM_ID);
                  values.add(req.getItemId().toString());
                  columns.add(DB_OWNER_ID);
                  values.add(userId.toString());
                  columns.add(DB_EXPIRY_AT);
                  values.add(req.getExpiryTime() != null ? req.getExpiryTime().toString() : null);
                  columns.add(DB_CONSTRAINTS);
                  values.add(Optional.ofNullable(req.getConstraints()).orElse(new JsonObject()));
                  columns.add(DB_STATUS);
                  values.add(ACTIVE);
                  columns.add(DB_POLICY_TYPE);
                  values.add(req.getPolicyType());
                  columns.add(DB_ADDITIONAL_INFO);
                  values.add(Optional.ofNullable(req.getAdditionalInfo()).orElse(new JsonObject()));
                  columns.add(DB_PROVIDER_COMMENT);
                  values.add(Optional.ofNullable(req.getProviderComment()).orElse(""));
                  columns.add(DB_FEEDBACK_TO_CONSUMER);
                  values.add(Optional.ofNullable(req.getFeedbackToConsumer()).orElse(""));

                  InsertQuery insertQuery =
                      new InsertQuery()
                          .setTable(POLICY_TABLE)
                          .setColumns(columns)
                          .setValues(values);

                  LOGGER.debug("Insert Policy Query: {}", insertQuery.toSQL());
                  return postgresService.insert(insertQuery);
                })
            .toList();

    // Combine all futures and collect results
    return Future.all(new ArrayList<>(insertFutures))
        .map(cf -> insertFutures.stream().map(Future::result).collect(Collectors.toList()))
        .onSuccess(results -> LOGGER.info("All policies inserted successfully"))
        .onFailure(err -> LOGGER.error("createPolicy fail :: {}", err.getLocalizedMessage()));
  }

  @Override
  public Future<QueryResult> getPoliciesByConsumer(String consumerId) {

    SelectQuery selectQuery =
        new SelectQuery()
            .setTable(POLICY_TABLE)
            .setTableAlias("P")
            .setColumns(
                List.of(
                    "P._id AS \"policyId\"",
                    "P.item_id AS \"itemId\"",
                    "P.consumer_id AS \"consumerId\"",
                    "U.first_name AS \"consumerFirstName\"",
                    "U.last_name AS \"consumerLastName\"",
                    "U._id AS \"consumerId\"",
                    "P.status AS \"status\"",
                    "P.additional_info AS \"additionalInfo\"",
                    "P.provider_comment AS \"providerComment\"",
                    "P.feedback_to_consumer AS \"feedbackToConsumer\"",
                    "P.expiry_at AS \"expiryAt\"",
                    "P.constraints AS \"constraints\"",
                    "P.updated_at AS \"updatedAt\"",
                    "P.created_at AS \"createdAt\""))
            .setCondition(
                new Condition()
                    .setColumn("P.consumer_id")
                    .setValues(List.of(consumerId))
                    .setOperator(Condition.Operator.EQUALS))
            .setJoins(
                List.of(
                    new Join(Join.JoinType.LEFT, USER_TABLE, "U", "P.consumer_id", "_id")))
            .setOrderBy(List.of(new OrderBy("P.updated_at", OrderBy.Direction.DESC)));

    return postgresService.select(selectQuery, false).map(result -> result);
  }

  @Override
  public Future<QueryResult> getPoliciesByProvider(String ownerId) {

    SelectQuery selectQuery =
        new SelectQuery()
            .setTable(POLICY_TABLE)
            .setTableAlias("P")
            .setColumns(
                List.of(
                    "P._id AS \"policyId\"",
                    "P.item_id AS \"itemId\"",
                    "P.owner_id AS \"ownerId\"",
                    "U.first_name AS \"ownerFirstName\"",
                    "U.last_name AS \"ownerLastName\"",
                    "U.email_id AS \"ownerEmailId\"",
                    "U._id AS \"ownerId\"",
                    "P.status AS \"status\"",
                    "P.additional_info AS \"additionalInfo\"",
                    "P.provider_comment AS \"providerComment\"",
                    "P.feedback_to_consumer AS \"feedbackToConsumer\"",
                    "P.expiry_at AS \"expiryAt\"",
                    "P.constraints AS \"constraints\"",
                    "P.updated_at AS \"updatedAt\"",
                    "P.created_at AS \"createdAt\""))
            .setJoins(List.of(new Join(Join.JoinType.INNER, USER_TABLE, "U", "P.owner_id", "_id")))
            .setCondition(
                new Condition()
                    .setColumn("P.owner_id")
                    .setValues(List.of(ownerId))
                    .setOperator(Condition.Operator.EQUALS))
            .setOrderBy(List.of(new OrderBy("P.updated_at", OrderBy.Direction.DESC)));

    return postgresService.select(selectQuery, false).map(result -> result);
  }

  /** Verify if a policy exists, belongs to correct user, and is ACTIVE */
  @Override
  public Future<QueryResult> verifyPolicy(UUID policyId) {
    Condition condition =
        new Condition()
            .setColumn("p._id")
            .setOperator(Condition.Operator.EQUALS)
            .setValues(List.of(policyId.toString()));
    SelectQuery query =
        new SelectQuery()
            .setTable(POLICY_TABLE)
            .setTableAlias("p")
            .setColumns(List.of("p.owner_id", "p.status", "p.item_organization_id"))
            .setCondition(condition);

    return postgresService.select(query, false);
  }

  /** Update policy status to DELETED if not expired */
  @Override
  public Future<QueryResult> deActivatePolicy(UUID policyId) {
    Condition condition =
        new Condition()
            .setGroup(true)
            .setLogicalOperator(Condition.LogicalOperator.AND)
            .setConditions(
                List.of(
                    new Condition()
                        .setColumn(DB_ID)
                        .setValues(List.of(policyId.toString()))
                        .setOperator(Condition.Operator.EQUALS),
                    new Condition()
                        .setColumn(DB_EXPIRY_AT)
                        .setValues(List.of(LocalDateTime.now().toString()))
                        .setOperator(Condition.Operator.GREATER)));
    UpdateQuery query =
        new UpdateQuery()
            .setTable(POLICY_TABLE)
            .setColumns(List.of(DB_STATUS))
            .setValues(List.of("DELETED"))
            .setCondition(condition);

    return postgresService.update(query);
  }

  @Override
  public Future<QueryResult> deActivatePolicyByUserAndItem(
      UUID itemId, UUID requestId, String userId) {

    Condition condition =
        new Condition()
            .setGroup(true)
            .setLogicalOperator(Condition.LogicalOperator.AND)
            .setConditions(
                List.of(
                    new Condition()
                        .setColumn(DB_ITEM_ID)
                        .setOperator(Condition.Operator.EQUALS)
                        .setValues(List.of(itemId.toString())),
                    new Condition()
                        .setColumn(DB_REQUEST_ID)
                        .setOperator(Condition.Operator.EQUALS)
                        .setValues(List.of(requestId.toString())),
                    new Condition()
                        .setColumn(DB_CONSUMER_ID)
                        .setOperator(Condition.Operator.EQUALS)
                        .setValues(List.of(userId)),
                    new Condition()
                        .setColumn(DB_STATUS)
                        .setOperator(Condition.Operator.EQUALS)
                        .setValues(List.of(ACTIVE)),
                    new Condition()
                        .setColumn(DB_EXPIRY_AT)
                        .setOperator(Condition.Operator.GREATER)
                        .setValues(List.of(LocalDateTime.now().toString()))));

    UpdateQuery query =
        new UpdateQuery()
            .setTable(POLICY_TABLE)
            .setColumns(List.of(DB_STATUS))
            .setValues(List.of("DELETED"))
            .setCondition(condition);

    LOGGER.debug("Soft deleting policy for item {} user {}", itemId, userId);

    return postgresService.update(query);
  }

  @Override
  public Future<PaginatedResult<PolicyDto>> getPoliciesWithAccessControl(
      PaginatedRequest request,
      Set<String> policyIds,
      String consumerId) {

    int page = request.page() > 0 ? request.page() : 1;
    int size = request.size() > 0 ? request.size() : 10;
    int offset = (page - 1) * size;

    /*
     * Existing request filters
     */
    Condition requestCondition =
        fromFilters(request.filters(), request.temporalRequests());

    /*
     * (_id IN (...) OR consumer_id = ?)
     */
    List<Condition> orConditions = new ArrayList<>();

    if (policyIds != null && !policyIds.isEmpty()) {
      orConditions.add(
          new Condition(DB_ID, Condition.Operator.IN, new ArrayList<>(policyIds)));
    }

    if (consumerId != null) {
      orConditions.add(
          new Condition(DB_CONSUMER_ID,
              Condition.Operator.EQUALS,
              List.of(consumerId)));
    }

    Condition accessCondition =
        new Condition(orConditions, Condition.LogicalOperator.OR);

    /*
     * Final:
     * (request filters)
     * AND
     * (_id IN (...) OR consumer_id = ?)
     */
    Condition finalCondition;

    if (requestCondition != null) {
      finalCondition =
          new Condition(
              List.of(requestCondition, accessCondition),
              Condition.LogicalOperator.AND);
    } else {
      finalCondition = accessCondition;
    }

    SelectQuery query =
        new SelectQuery(
            POLICY_TABLE,
            List.of("*"),
            finalCondition,
            null,
            request.orderByList(),
            size,
            offset);

    LOGGER.info("Executing consumer policy query: {}", query);

    return postgresService
        .select(query, true)
        .map(result -> toPaginatedResult(result, page, size))
        .recover(
            err -> {
              LOGGER.error("Failed fetching policies: {}", err.getMessage(), err);
              return Future.failedFuture(BaseDxException.from(err));
            });
  }

  @Override
  public Future<List<PolicyDto>> getMatchingPolicies(UUID itemId, String consumerId) {

    LocalDateTime now = LocalDateTime.now();

    Condition finalCondition =
        new Condition(
            List.of(
                new Condition(DB_ITEM_ID, Condition.Operator.EQUALS, List.of(itemId.toString())),
                new Condition(DB_CONSUMER_ID, Condition.Operator.EQUALS, List.of(consumerId)),
                new Condition(DB_STATUS, Condition.Operator.EQUALS, List.of(ACTIVE)),
                new Condition(DB_EXPIRY_AT, Condition.Operator.GREATER, List.of(now.toString()))),
            Condition.LogicalOperator.AND);

    SelectQuery query =
        new SelectQuery()
            .setTable(POLICY_TABLE)
            .setColumns(List.of("*"))
            .setCondition(finalCondition);

    return postgresService
        .select(query, false)
        .compose(
            result -> {
              List<PolicyDto> policies = new ArrayList<>();

              for (Object rowObj : result.getRows()) {

                JsonObject row = (JsonObject) rowObj;

                policies.add(new PolicyDto(row));
              }

              return Future.succeededFuture(policies);
            });
  }

  @Override
  public Future<Boolean> hasActivePolicies(UUID assetId) {

    Condition condition =
        new Condition(
            List.of(
                new Condition(DB_ITEM_ID, Condition.Operator.EQUALS, List.of(assetId.toString())),
                new Condition(DB_STATUS, Condition.Operator.EQUALS, List.of(ACTIVE)),
                new Condition(
                    DB_EXPIRY_AT,
                    Condition.Operator.GREATER,
                    List.of(LocalDateTime.now().toString()))),
            Condition.LogicalOperator.AND);

    SelectQuery query =
        new SelectQuery()
            .setTable(POLICY_TABLE)
            .setColumns(List.of(DB_ID))
            .setCondition(condition)
            .setLimit(1);

    return postgresService
        .select(query, false)
        .map(result -> result.getRows() != null && !result.getRows().isEmpty());
  }

  private String generateErrorResponse(HttpStatusCode httpStatusCode, String errorMessage) {
    return new JsonObject()
        .put(TYPE, httpStatusCode.getValue())
        .put(TITLE, httpStatusCode.getPath())
        .put(DETAIL, errorMessage)
        .encode();
  }
}
