package org.cdpg.dx.acl.rule.dao.impl;

import static org.cdpg.dx.aaa.common.Constants.ACTIVE;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.ACCESS_RULE_ALLOWED_ORG_TABLE;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.ACCESS_RULE_ALLOWED_ROLE_TABLE;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.ACCESS_RULE_TABLE;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.ACSESS_RULE_ALLOWED_USER_TABLE;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.ALLOWED_ORG_IDS;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.ALLOWED_ROLES;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.ALLOWED_USER_IDS;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_CONSTRAINTS;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_EXPIRY_AT;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_ID;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_ITEM_ID;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_ORG_ID;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_OWNER_ID;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_POLICY_ID;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_ROLE;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_RULE_ID;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_STATUS;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_USER_ID;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.POLICY_TABLE;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.acl.policy.dao.model.PolicyDto;
import org.cdpg.dx.acl.rule.dao.AccessRuleDao;
import org.cdpg.dx.database.postgres.models.Condition;
import org.cdpg.dx.database.postgres.models.InsertQuery;
import org.cdpg.dx.database.postgres.models.Join;
import org.cdpg.dx.database.postgres.models.QueryResult;
import org.cdpg.dx.database.postgres.models.SelectQuery;
import org.cdpg.dx.database.postgres.models.UpdateQuery;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class AccessRuleDaoImpl implements AccessRuleDao {

  private static final Logger LOGGER = LogManager.getLogger(AccessRuleDaoImpl.class);

  private final PostgresService postgresService;

  public AccessRuleDaoImpl(PostgresService postgresService) {
    this.postgresService = postgresService;
  }

  // ============================================================
  // RULE MATCH CHECK (USING SELECT QUERY)
  // ============================================================
  @Override
  public Future<Boolean> ruleMatches(UUID itemId, String userId, String orgId, List<String> roles) {

    List<Join> joins =
        List.of(
            new Join(Join.JoinType.LEFT, ACSESS_RULE_ALLOWED_USER_TABLE, "U", "R._id", DB_RULE_ID),
            new Join(Join.JoinType.LEFT, ACCESS_RULE_ALLOWED_ORG_TABLE, "O", "R._id", DB_RULE_ID),
            new Join(
                Join.JoinType.LEFT, ACCESS_RULE_ALLOWED_ROLE_TABLE, "RL", "R._id", DB_RULE_ID));

    // R.item_id = ?
    Condition itemCondition =
        new Condition("R.item_id", Condition.Operator.EQUALS, List.of(itemId.toString()));

    // R.status = 'ACTIVE'
    Condition statusCondition =
        new Condition("R.status", Condition.Operator.EQUALS, List.of("ACTIVE"));

    // U.user_id = ?
    Condition userCondition =
        new Condition("U.user_id", Condition.Operator.EQUALS, List.of(userId));

    // O.org_id = ?
    Condition orgCondition = new Condition("O.org_id", Condition.Operator.EQUALS, List.of(orgId));

    // (U.user_id = ? OR O.org_id = ?)
    Condition subjectGroup =
        new Condition(List.of(userCondition, orgCondition), Condition.LogicalOperator.OR);

    // ROLE MATCHING
    Condition roleGroup;

    if (roles != null && !roles.isEmpty()) {

      // RL.role IN (...)
      Condition roleInCondition =
          new Condition("RL.role", Condition.Operator.IN, new ArrayList<>(roles));

      // RL.role IS NULL
      Condition roleIsNullCondition = new Condition("RL.role", Condition.Operator.IS_NULL, null);

      // (RL.role IN (...) OR RL.role IS NULL)
      roleGroup =
          new Condition(
              List.of(roleInCondition, roleIsNullCondition), Condition.LogicalOperator.OR);

    } else {
      // If no roles passed, only allow rules without role restriction
      roleGroup = new Condition("RL.role", Condition.Operator.IS_NULL, null);
    }

    // Final AND group
    Condition finalCondition =
        new Condition(
            List.of(itemCondition, statusCondition, subjectGroup, roleGroup),
            Condition.LogicalOperator.AND);

    SelectQuery selectQuery =
        new SelectQuery()
            .setTable(ACCESS_RULE_TABLE)
            .setTableAlias("R")
            .setColumns(List.of("1"))
            .setJoins(joins)
            .setCondition(finalCondition)
            .setLimit(1);

    return postgresService
        .select(selectQuery, false)
        .map(result -> !result.getRows().isEmpty())
        .onFailure(err -> LOGGER.error("ruleMatches failed", err));
  }

  // ============================================================
  // FIND MATCHING RULE
  // ============================================================

  @Override
  public Future<PolicyDto> findMatchingRule(
      UUID itemId, String userId, String orgId, List<String> roles) {

    List<Join> joins =
        List.of(
            new Join(Join.JoinType.INNER, POLICY_TABLE, "P", "R.policy_id", "_id"),
            new Join(Join.JoinType.LEFT, ACSESS_RULE_ALLOWED_USER_TABLE, "U", "R._id", DB_RULE_ID),
            new Join(Join.JoinType.LEFT, ACCESS_RULE_ALLOWED_ORG_TABLE, "O", "R._id", DB_RULE_ID),
            new Join(
                Join.JoinType.LEFT, ACCESS_RULE_ALLOWED_ROLE_TABLE, "RL", "R._id", DB_RULE_ID));

    Condition itemCondition =
        new Condition("R.item_id", Condition.Operator.EQUALS, List.of(itemId.toString()));

    Condition statusCondition =
        new Condition("R.status", Condition.Operator.EQUALS, List.of("ACTIVE"));

    Condition userCondition =
        new Condition("U.user_id", Condition.Operator.EQUALS, List.of(userId));

    Condition orgCondition = new Condition("O.org_id", Condition.Operator.EQUALS, List.of(orgId));

    Condition subjectGroup =
        new Condition(List.of(userCondition, orgCondition), Condition.LogicalOperator.OR);

    Condition roleGroup;

    if (roles != null && !roles.isEmpty()) {

      Condition roleInCondition =
          new Condition("RL.role", Condition.Operator.IN, new ArrayList<>(roles));

      Condition roleIsNullCondition = new Condition("RL.role", Condition.Operator.IS_NULL, null);

      roleGroup =
          new Condition(
              List.of(roleInCondition, roleIsNullCondition), Condition.LogicalOperator.OR);

    } else {
      roleGroup = new Condition("RL.role", Condition.Operator.IS_NULL, null);
    }

    Condition finalCondition =
        new Condition(
            List.of(itemCondition, statusCondition, subjectGroup, roleGroup),
            Condition.LogicalOperator.AND);

    SelectQuery selectQuery =
        new SelectQuery()
            .setTable(ACCESS_RULE_TABLE)
            .setTableAlias("R")
            .setColumns(
                List.of(
                    "P._id",
                    "P.request_id",
                    "P.policy_type",
                    "P.item_id",
                    "P.status",
                    "P.expiry_at",
                    "P.constraints",
                    "P.additional_info",
                    "P.created_at",
                    "P.updated_at",
                    "P.consumer_id",
                    "P.owner_id"))
            .setJoins(joins)
            .setCondition(finalCondition)
            .setLimit(1);

    return postgresService
        .select(selectQuery, false)
        .map(
            result -> {
              if (result.getRows().isEmpty()) {
                return null;
              }

              JsonObject row = (JsonObject) result.getRows().getList().getFirst();

              return new PolicyDto(row);
            })
        .onFailure(err -> LOGGER.error("findMatchingRule failed", err));
  }

  // ============================================================
  // CREATE RULE
  // ============================================================
  @Override
  public Future<Void> createRule(
      UUID policyId,
      UUID itemId,
      UUID ownerId,
      JsonObject subjects,
      JsonObject constraints,
      String expiryAt) {

    UUID ruleId = UUID.randomUUID();

    InsertQuery insertRule =
        new InsertQuery()
            .setTable(ACCESS_RULE_TABLE)
            .setColumns(
                List.of(
                    DB_ID,
                    DB_POLICY_ID,
                    DB_ITEM_ID,
                    DB_OWNER_ID,
                    DB_CONSTRAINTS,
                    DB_STATUS,
                    DB_EXPIRY_AT))
            .setValues(
                List.of(
                    ruleId.toString(),
                    policyId.toString(),
                    itemId.toString(),
                    ownerId.toString(),
                    constraints != null ? constraints.encode() : "{}",
                    ACTIVE,
                    expiryAt));

    return postgresService
        .insert(insertRule)
        .compose(v -> insertSubjects(ruleId, subjects))
        .mapEmpty();
  }

  // ============================================================
  // INSERT SUBJECTS
  // ============================================================
  private Future<Void> insertSubjects(UUID ruleId, JsonObject subjects) {

    if (subjects == null || subjects.isEmpty()) {
      return Future.succeededFuture();
    }

    List<Future<?>> futures = new ArrayList<>();

    JsonArray orgs = subjects.getJsonArray(ALLOWED_ORG_IDS, new JsonArray());

    for (int i = 0; i < orgs.size(); i++) {

      InsertQuery insertOrg =
          new InsertQuery()
              .setTable(ACCESS_RULE_ALLOWED_ORG_TABLE)
              .setColumns(List.of(DB_RULE_ID, DB_ORG_ID))
              .setValues(List.of(ruleId.toString(), orgs.getString(i)));

      futures.add(postgresService.insert(insertOrg));
    }

    JsonArray users = subjects.getJsonArray(ALLOWED_USER_IDS, new JsonArray());

    for (int i = 0; i < users.size(); i++) {

      InsertQuery insertUser =
          new InsertQuery()
              .setTable(ACSESS_RULE_ALLOWED_USER_TABLE)
              .setColumns(List.of(DB_RULE_ID, DB_USER_ID))
              .setValues(List.of(ruleId.toString(), users.getString(i)));

      futures.add(postgresService.insert(insertUser));
    }

    JsonArray rolesArray = subjects.getJsonArray(ALLOWED_ROLES, new JsonArray());

    for (int i = 0; i < rolesArray.size(); i++) {

      InsertQuery insertRole =
          new InsertQuery()
              .setTable(ACCESS_RULE_ALLOWED_ROLE_TABLE)
              .setColumns(List.of(DB_RULE_ID, DB_ROLE))
              .setValues(List.of(ruleId.toString(), rolesArray.getString(i)));

      futures.add(postgresService.insert(insertRole));
    }

    if (futures.isEmpty()) {
      return Future.succeededFuture();
    }

    return Future.all(futures).mapEmpty();
  }

  @Override
  public Future<QueryResult> updateStatusByPolicyId(UUID policyId, String status) {

    Condition condition =
        new Condition()
            .setColumn(DB_POLICY_ID)
            .setValues(List.of(policyId.toString()))
            .setOperator(Condition.Operator.EQUALS);
    UpdateQuery updateQuery =
        new UpdateQuery()
            .setTable(ACCESS_RULE_TABLE)
            .setColumns(List.of(DB_STATUS))
            .setValues(List.of(status))
            .setCondition(condition);

    LOGGER.debug("Update AccessRule status by policyId Query: {}", updateQuery.toSQL());

    return postgresService
        .update(updateQuery)
        .onSuccess(v -> LOGGER.info("Access rules deactivated for policyId={}", policyId))
        .onFailure(
            err -> LOGGER.error("Failed to update access rules for policyId={}", policyId, err));
  }

  @Override
  public Future<Set<String>> getAccessiblePolicyIds(String userId, String orgId,
                                                   List<String> roles) {

    List<Join> joins =
        List.of(
            new Join(Join.JoinType.LEFT, ACSESS_RULE_ALLOWED_USER_TABLE, "U", "R._id", DB_RULE_ID),
            new Join(Join.JoinType.LEFT, ACCESS_RULE_ALLOWED_ORG_TABLE, "O", "R._id", DB_RULE_ID),
            new Join(
                Join.JoinType.LEFT, ACCESS_RULE_ALLOWED_ROLE_TABLE, "RL", "R._id", DB_RULE_ID));

    List<Condition> conditions = new ArrayList<>();

    /*
     * ORG CONDITION
     *
     * Rule matches if:
     *   - no org restriction exists
     *   OR
     *   - org matches
     */
    if (orgId != null) {

      Condition orgMatch = new Condition("O.org_id", Condition.Operator.EQUALS, List.of(orgId));

      Condition orgNotConfigured = new Condition("O.org_id", Condition.Operator.IS_NULL, null);

      conditions.add(
          new Condition(List.of(orgMatch, orgNotConfigured), Condition.LogicalOperator.OR));
    } else {

      conditions.add(new Condition("O.org_id", Condition.Operator.IS_NULL, null));
    }

    /*
     * USER CONDITION
     *
     * Rule matches if:
     *   - no user restriction exists
     *   OR
     *   - user matches
     */
    Condition userMatch = new Condition("U.user_id", Condition.Operator.EQUALS, List.of(userId));

    Condition userNotConfigured = new Condition("U.user_id", Condition.Operator.IS_NULL, null);

    conditions.add(
        new Condition(List.of(userMatch, userNotConfigured), Condition.LogicalOperator.OR));

    /*
     * ROLE CONDITION
     *
     * Rule matches if:
     *   - no role restriction exists
     *   OR
     *   - role matches
     */
    if (roles != null && !roles.isEmpty()) {

      Condition roleMatch = new Condition("RL.role", Condition.Operator.IN, new ArrayList<>(roles));

      Condition roleNotConfigured = new Condition("RL.role", Condition.Operator.IS_NULL, null);

      conditions.add(
          new Condition(List.of(roleMatch, roleNotConfigured), Condition.LogicalOperator.OR));

    } else {

      conditions.add(new Condition("RL.role", Condition.Operator.IS_NULL, null));
    }

    Condition finalCondition = new Condition(conditions, Condition.LogicalOperator.AND);

    SelectQuery selectQuery =
        new SelectQuery()
            .setTable(ACCESS_RULE_TABLE)
            .setTableAlias("R")
            .setColumns(List.of("DISTINCT R.policy_id"))
            .setJoins(joins)
            .setCondition(finalCondition);

    return postgresService
        .select(selectQuery, false)
        .map(
            result -> {
              Set<String> policyIds = new HashSet<>();

              for (Object rowObj : result.getRows()) {

                JsonObject row = (JsonObject) rowObj;

                policyIds.add(row.getString(DB_POLICY_ID));
              }

              return policyIds;
            });
  }
}
