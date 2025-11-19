package org.cdpg.dx.acl.policy.dao.impl;

import static org.cdpg.dx.aaa.common.Constants.ACTIVE;
import static org.cdpg.dx.aaa.common.Constants.DETAIL;
import static org.cdpg.dx.aaa.common.Constants.TITLE;
import static org.cdpg.dx.aaa.common.Constants.TYPE;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_ADDITIONAL_INFO;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_CONSTRAINTS;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_EXPIRY_AT;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_FEEDBACK_TO_CONSUMER;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_ID;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_ITEM_ID;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_OWNER_ID;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_PROVIDER_COMMENT;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_STATUS;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_USER_EMAIL_ID;
import static org.cdpg.dx.common.HttpStatusCode.INTERNAL_SERVER_ERROR;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.acl.policy.dao.PolicyDao;
import org.cdpg.dx.acl.policy.service.model.CreatePolicyRequest;
import org.cdpg.dx.common.HttpStatusCode;
import org.cdpg.dx.database.postgres.models.Condition;
import org.cdpg.dx.database.postgres.models.InsertQuery;
import org.cdpg.dx.database.postgres.models.Join;
import org.cdpg.dx.database.postgres.models.OrderBy;
import org.cdpg.dx.database.postgres.models.QueryResult;
import org.cdpg.dx.database.postgres.models.SelectQuery;
import org.cdpg.dx.database.postgres.models.UpdateQuery;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class PolicyDaoImpl implements PolicyDao {
  private static final Logger LOGGER = LogManager.getLogger(PolicyDaoImpl.class);
  private static final String POLICY_TABLE = "policy";
  private static final String USER_TABLE = "user_table";
  private final PostgresService postgresService;


  public PolicyDaoImpl(PostgresService postgresService) {
    this.postgresService = postgresService;
  }

  @Override
  public Future<QueryResult> checkExistingPoliciesForIds(UUID itemId, UUID ownerId, String userEmail) {
    Promise<QueryResult> promise = Promise.promise();

    Condition condition = new Condition(
        List.of(
            new Condition(DB_ITEM_ID, Condition.Operator.EQUALS, List.of(itemId.toString())),
            new Condition(DB_OWNER_ID, Condition.Operator.EQUALS, List.of(ownerId.toString())),
            new Condition(DB_STATUS, Condition.Operator.EQUALS, List.of(ACTIVE)),
            new Condition(DB_USER_EMAIL_ID, Condition.Operator.EQUALS, List.of(userEmail)),
            new Condition(DB_EXPIRY_AT, Condition.Operator.GREATER,
                List.of(LocalDateTime.now().toString()))
        ),
        Condition.LogicalOperator.AND
    );

    SelectQuery query = new SelectQuery()
        .setTable(POLICY_TABLE)
        .setColumns(List.of(DB_ID, DB_CONSTRAINTS, DB_EXPIRY_AT))
        .setCondition(condition);

    postgresService.select(query, false)
        .onSuccess(promise::complete)
        .onFailure(err -> {
          LOGGER.error("checkExistingPoliciesForIds failed: {}", err.getMessage());
          promise.fail(generateErrorResponse(INTERNAL_SERVER_ERROR, err.getMessage()));
        });

    return promise.future();
  }

  @Override
  public Future<QueryResult> checkExistingPoliciesForIds(List<CreatePolicyRequest> requests,
                                                         UUID ownerId) {
    Promise<QueryResult> promise = Promise.promise();

    List<Object> itemIds = requests.stream()
        .map(req -> req.getItemId().toString())
        .collect(Collectors.toList());

    List<Object> emails = requests.stream()
        .map(CreatePolicyRequest::getUserEmail)
        .collect(Collectors.toList());

    Condition cond = new Condition(
        List.of(
            new Condition(DB_ITEM_ID, Condition.Operator.EQUALS, itemIds),
            new Condition(DB_OWNER_ID, Condition.Operator.EQUALS, List.of(ownerId.toString())),
            new Condition(DB_STATUS, Condition.Operator.EQUALS, List.of(ACTIVE)),
            new Condition(DB_USER_EMAIL_ID, Condition.Operator.EQUALS, emails),
            new Condition(DB_EXPIRY_AT, Condition.Operator.GREATER, List.of(LocalDateTime.now().toString()))
        ),
        Condition.LogicalOperator.AND
    );

    SelectQuery query = new SelectQuery()
        .setTable(POLICY_TABLE)
        .setColumns(List.of(DB_ID, DB_CONSTRAINTS))
        .setCondition(cond);

    postgresService.select(query, false)
        .onSuccess(promise::complete)
        .onFailure(err -> {
          LOGGER.error("getExistingPolicies failed: {}", err.getMessage());
          promise.fail(generateErrorResponse(INTERNAL_SERVER_ERROR, err.getMessage()));
        });

    return promise.future();
  }

  @Override
  public Future<List<QueryResult>> insertPolicies(List<CreatePolicyRequest> createPolicyRequestList,
                                                  UUID userId) {
    // Compose futures for each policy insert
    List<Future<QueryResult>> insertFutures = createPolicyRequestList.stream()
        .map(req -> {
          List<Object> values = List.of(
              req.getUserEmail(),                     // String
              req.getItemId().toString(),             // UUID
              userId.toString(),                      // UUID
              req.getExpiryTime().toString(),         // LocalDateTime
              req.getConstraints(),                   // JsonObject
              ACTIVE,                                 // String
              req.getAdditionalInfo(),                // String or nullable
              req.getProviderComment(),               // String or nullable
              req.getFeedbackToConsumer()             // String or nullable
          );

          InsertQuery insertQuery = new InsertQuery()
              .setTable(POLICY_TABLE)
              .setColumns(List.of(
                  DB_USER_EMAIL_ID,
                  DB_ITEM_ID,
                  DB_OWNER_ID,
                  DB_EXPIRY_AT,
                  DB_CONSTRAINTS,
                  DB_STATUS,
                  DB_ADDITIONAL_INFO,
                  DB_PROVIDER_COMMENT,
                  DB_FEEDBACK_TO_CONSUMER
              ))
              .setValues(values);

          LOGGER.debug("Insert Policy Query: {}", insertQuery.toSQL());
          return postgresService.insert(insertQuery);
        })
        .toList();

    // Combine all futures and collect results
    return Future.all(new ArrayList<>(insertFutures))
        .map(cf -> insertFutures.stream()
            .map(Future::result)
            .collect(Collectors.toList())
        )
        .onSuccess(results -> LOGGER.info("All policies inserted successfully"))
        .onFailure(err -> LOGGER.error("createPolicy fail :: " + err.getLocalizedMessage()));
  }

  @Override
  public Future<QueryResult> getPoliciesByConsumer(String emailId) {

    SelectQuery selectQuery = new SelectQuery()
        .setTable(POLICY_TABLE)
        .setTableAlias("P")
        .setColumns(List.of(
            "P._id AS \"policyId\"",
            "P.item_id AS \"itemId\"",
            "P.user_emailid AS \"consumerEmailId\"",
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
            "P.created_at AS \"createdAt\""
        ))
        .setCondition(new Condition()
            .setColumn("P.user_emailid")
            .setValues(List.of(emailId))
            .setOperator(Condition.Operator.EQUALS))
        .setJoins(List.of(
            new Join(Join.JoinType.LEFT, USER_TABLE, "U", "P.user_emailid", "email_id")
        ))
        .setOrderBy(List.of(new OrderBy("P.updated_at", OrderBy.Direction.DESC)));

    return postgresService.select(selectQuery, false).map(result -> result);
  }

  @Override
  public Future<QueryResult> getPoliciesByProvider(String ownerId) {

    SelectQuery selectQuery = new SelectQuery()
        .setTable(POLICY_TABLE)
        .setTableAlias("P")
        .setColumns(List.of(
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
            "P.created_at AS \"createdAt\""
        ))
        .setJoins(List.of(
            new Join(Join.JoinType.INNER, USER_TABLE, "U", "P.owner_id", "_id")
        ))
        .setCondition(new Condition().setColumn("P.owner_id").setValues(List.of(ownerId))
            .setOperator(Condition.Operator.EQUALS))
        .setOrderBy(List.of(new OrderBy("P.updated_at", OrderBy.Direction.DESC)));

    return postgresService.select(selectQuery, false).map(result -> result);
  }

  /**
   * Verify if a policy exists, belongs to correct user, and is ACTIVE
   */
  @Override
  public Future<QueryResult> verifyPolicy(UUID policyId) {
    Condition condition = new Condition()
        .setColumn("p._id")
        .setOperator(Condition.Operator.EQUALS)
        .setValues(List.of(policyId.toString()));
    SelectQuery query = new SelectQuery()
        .setTable(POLICY_TABLE)
        .setTableAlias("p")
        .setColumns(List.of("p.owner_id", "p.status"))
        .setCondition(condition);

    return postgresService.select(query, false);
  }

  /**
   * Update policy status to DELETED if not expired
   */
  @Override
  public Future<QueryResult> deletePolicy(UUID policyId) {
    Condition condition = new Condition()
        .setGroup(true)
        .setLogicalOperator(Condition.LogicalOperator.AND)
        .setConditions(List.of(
            new Condition().setColumn(DB_ID).setValues(List.of(policyId.toString()))
                .setOperator(Condition.Operator.EQUALS),
            new Condition().setColumn(DB_EXPIRY_AT)
                .setValues(List.of(LocalDateTime.now().toString()))
                .setOperator(Condition.Operator.GREATER)
        ));
    UpdateQuery query = new UpdateQuery()
        .setTable(POLICY_TABLE)
        .setColumns(List.of(DB_STATUS))
        .setValues(List.of("DELETED"))
        .setCondition(condition);

    return postgresService.update(query);
  }

  private String generateErrorResponse(HttpStatusCode httpStatusCode, String errorMessage) {
    return new JsonObject()
        .put(TYPE, httpStatusCode.getValue())
        .put(TITLE, httpStatusCode.getPath())
        .put(DETAIL, errorMessage)
        .encode();
  }
}