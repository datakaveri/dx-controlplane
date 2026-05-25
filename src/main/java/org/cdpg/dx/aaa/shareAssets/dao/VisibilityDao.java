package org.cdpg.dx.aaa.shareAssets.dao;

import io.vertx.core.Future;
import java.util.List;
import java.util.UUID;
import org.cdpg.dx.aaa.shareAssets.dao.model.VisibilityEntity;

public interface VisibilityDao {

  Future<Void> shareWithUsers(UUID itemId, UUID sharedBy, List<UUID> userIds);

  Future<Void> shareWithOrganizations(UUID itemId, UUID sharedBy, List<UUID> orgIds);

  Future<List<VisibilityEntity>> getAssetsSharedWithMe(UUID userId, String orgId);

  Future<Void> revokeUserShare(UUID itemId, List<UUID> userIds);

  Future<Void> revokeOrganizationShare(UUID itemId, List<UUID> orgIds);

  Future<List<VisibilityEntity>> getVisibilityDetails(UUID itemId);
  Future<Boolean> hasActiveUserShare(UUID itemId, UUID userId);
  Future<Boolean> hasActiveOrganizationShare(UUID itemId, UUID orgId);
}
