package org.cdpg.dx.acl.rule.dao.impl;

import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_POLICY_ID;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.DB_STATUS;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
            new Join(Join.JoinType.LEFT, "access_rule_allowed_user", "U", "R._id", "rule_id"),
            new Join(Join.JoinType.LEFT, "access_rule_allowed_org", "O", "R._id", "rule_id"),
            new Join(Join.JoinType.LEFT, "access_rule_allowed_role", "RL", "R._id", "rule_id"));

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
            .setTable("access_rule")
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
  public Future<JsonObject> findMatchingRule(
      UUID itemId, String userId, String orgId, List<String> roles) {

    List<Join> joins =
        List.of(
            new Join(Join.JoinType.LEFT, "access_rule_allowed_user", "U", "R._id", "rule_id"),
            new Join(Join.JoinType.LEFT, "access_rule_allowed_org", "O", "R._id", "rule_id"),
            new Join(Join.JoinType.LEFT, "access_rule_allowed_role", "RL", "R._id", "rule_id"));

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
            .setTable("access_rule")
            .setTableAlias("R")
            .setColumns(List.of("R._id", "R.constraints"))
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

              JsonObject rule =
                  new JsonObject()
                      .put("ruleId", row.getString("_id"))
                      .put(
                          "constraints",
                          row.getString("constraints") != null
                              ? new JsonObject(row.getString("constraints"))
                              : new JsonObject());

              return rule;
            })
        .onFailure(err -> LOGGER.error("findMatchingRule failed", err));
  }

  // ============================================================
  // CREATE RULE
  // ============================================================
  @Override
  public Future<Void> createRule(
      UUID policyId, UUID itemId, UUID ownerId, JsonObject subjects, JsonObject constraints) {

    UUID ruleId = UUID.randomUUID();

    InsertQuery insertRule =
        new InsertQuery()
            .setTable("access_rule")
            .setColumns(List.of("_id", "policy_id", "item_id", "owner_id", "constraints", "status"))
            .setValues(
                List.of(
                    ruleId.toString(),
                    policyId.toString(),
                    itemId.toString(),
                    ownerId.toString(),
                    constraints != null ? constraints.encode() : "{}",
                    "ACTIVE"));

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

    JsonArray orgs = subjects.getJsonArray("allowedOrgIds", new JsonArray());

    for (int i = 0; i < orgs.size(); i++) {

      InsertQuery insertOrg =
          new InsertQuery()
              .setTable("access_rule_allowed_org")
              .setColumns(List.of("rule_id", "org_id"))
              .setValues(List.of(ruleId.toString(), orgs.getString(i)));

      futures.add(postgresService.insert(insertOrg));
    }

    JsonArray users = subjects.getJsonArray("allowedUserIds", new JsonArray());

    for (int i = 0; i < users.size(); i++) {

      InsertQuery insertUser =
          new InsertQuery()
              .setTable("access_rule_allowed_user")
              .setColumns(List.of("rule_id", "user_id"))
              .setValues(List.of(ruleId.toString(), users.getString(i)));

      futures.add(postgresService.insert(insertUser));
    }

    JsonArray rolesArray = subjects.getJsonArray("allowedRoles", new JsonArray());

    for (int i = 0; i < rolesArray.size(); i++) {

      InsertQuery insertRole =
          new InsertQuery()
              .setTable("access_rule_allowed_role")
              .setColumns(List.of("rule_id", "role"))
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
            .setTable("access_rule")
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
}
