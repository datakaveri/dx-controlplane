package org.cdpg.dx.acl.rule.dao;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.UUID;
import org.cdpg.dx.database.postgres.models.QueryResult;

public interface AccessRuleDao {

  Future<Boolean> ruleMatches(UUID itemId, String userId, String orgId, List<String> roles);

  Future<JsonObject> findMatchingRule(
      UUID itemId,
      String userId,
      String orgId,
      List<String> roles
  );

  Future<Void> createRule(
      UUID policyId,
      UUID itemId,
      UUID ownerId,
      JsonObject subjects,
      JsonObject constraints
  );

  Future<QueryResult> updateStatusByPolicyId(UUID policyId, String status);
}
