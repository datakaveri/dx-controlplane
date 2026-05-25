package org.cdpg.dx.aaa.shareAssets.service;

import io.vertx.core.Future;
import java.util.List;
import java.util.UUID;
import org.cdpg.dx.aaa.shareAssets.dao.model.VisibilityEntity;

public interface VisibilityService {

  Future<Void> shareAssets(UUID itemId, String shareType, UUID sharedBy, List<UUID> ids);

  Future<Void> revokeAssets(UUID itemId, String shareType, List<UUID> ids);

  Future<List<VisibilityEntity>> getAssetsSharedWithMe(UUID userId, String orgId);

  Future<List<VisibilityEntity>> getVisibilityDetails(UUID itemId);
}
