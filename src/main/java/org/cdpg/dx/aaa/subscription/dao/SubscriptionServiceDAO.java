package org.cdpg.dx.aaa.subscription.dao;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import java.time.LocalDateTime;
import java.util.UUID;
import org.cdpg.dx.aaa.subscription.model.GetAllSubscription;
import org.cdpg.dx.aaa.subscription.model.SubscriptionDTO;
import org.cdpg.dx.database.postgres.base.dao.BaseDAO;

public interface SubscriptionServiceDAO extends BaseDAO<SubscriptionDTO> {
  Future<String> getEntityIdByQueueName(String subscriptionId);

  Future<Void> deleteSubscriptionBySubId(String subscriptionId);

  Future<GetAllSubscription> getAllSubscriptionByUserId(String userId, int limit, int offset);

  Future<JsonArray> getSubscriptionByQueueNameAndEntityId(String subscriptionId, String entityId);

  Future<Void> updateSubscriptionExpiryByQueueNameAndEntityId(
      String subsId, /*String entitiesid,*/ LocalDateTime expiryAt);

  Future<JsonArray> getEntitiesIdAndQueueNameBySubscriptionId(UUID subscriptionId);

  Future<Void> insertSubscription(SubscriptionDTO subscriptionDTO);
}
