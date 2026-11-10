package org.cdpg.dx.aaa.subscription.service;

import io.vertx.core.Future;
import java.time.LocalDateTime;
import java.util.UUID;
import org.cdpg.dx.aaa.subscription.model.GetAllSubscription;
import org.cdpg.dx.aaa.subscription.model.GetSubscriptionModel;
import org.cdpg.dx.aaa.subscription.model.RegisterSubscription;

public interface SubscriptionService {
  Future<Void> deleteSubscription(String subscriptionId, String userid);

  Future<GetSubscriptionModel> getSubscriptionById(String subsId, String userId);

  Future<GetAllSubscription> getAllSubscriptions(String userId, int limit, int offset);

  Future<Void> updateSubscription(String entities, String subsId, LocalDateTime expiryAt);

  Future<RegisterSubscription> createSubscription(
      String userId,
      UUID subscriptionId,
      String subscriptionName,
      String entitiesId,
      LocalDateTime expiryAt,
      String providerId, String did);
}
