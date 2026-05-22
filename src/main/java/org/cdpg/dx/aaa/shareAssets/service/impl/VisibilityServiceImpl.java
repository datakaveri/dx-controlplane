package org.cdpg.dx.aaa.shareAssets.service.impl;

import static org.cdpg.dx.aaa.shareAssets.util.Constants.USER;

import io.vertx.core.Future;
import java.util.List;
import java.util.UUID;
import org.cdpg.dx.aaa.shareAssets.dao.VisibilityDao;
import org.cdpg.dx.aaa.shareAssets.dao.model.VisibilityEntity;
import org.cdpg.dx.aaa.shareAssets.service.VisibilityService;

public class VisibilityServiceImpl implements VisibilityService {

  private final VisibilityDao visibilityDao;

  public VisibilityServiceImpl(VisibilityDao visibilityDao) {
    this.visibilityDao = visibilityDao;
  }

  @Override
  public Future<Void> shareAssets(UUID itemId, String shareType, UUID sharedBy, List<UUID> ids) {

    if (USER.equalsIgnoreCase(shareType)) {
      return visibilityDao.shareWithUsers(itemId, sharedBy, ids);
    }

    return visibilityDao.shareWithOrganizations(itemId, sharedBy, ids);
  }

  @Override
  public Future<Void> revokeAssets(UUID itemId, String shareType, List<UUID> ids) {

    if (USER.equalsIgnoreCase(shareType)) {
      return visibilityDao.revokeUserShare(itemId, ids);
    }

    return visibilityDao.revokeOrganizationShare(itemId, ids);
  }

  @Override
  public Future<List<VisibilityEntity>> getAssetsSharedWithMe(UUID userId, UUID orgId) {

    return visibilityDao.getAssetsSharedWithMe(userId, orgId);
  }

  @Override
  public Future<List<VisibilityEntity>> getVisibilityDetails(UUID itemId) {

    return visibilityDao.getVisibilityDetails(itemId);
  }
}
