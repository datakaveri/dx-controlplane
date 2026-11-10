package org.cdpg.dx.aaa.shareAssets.service.impl;

import static org.cdpg.dx.aaa.shareAssets.util.Constants.USER;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.aaa.shareAssets.dao.VisibilityDao;
import org.cdpg.dx.aaa.shareAssets.dao.model.VisibilityEntity;
import org.cdpg.dx.aaa.shareAssets.service.VisibilityService;
import org.cdpg.dx.common.exception.DxConflictException;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

public class VisibilityServiceImpl implements VisibilityService {

  private final VisibilityDao visibilityDao;
  private final KeycloakUserService keycloakUserService;
  private final OrganizationService organizationService;

  public VisibilityServiceImpl(
      VisibilityDao visibilityDao,
      KeycloakUserService keycloakUserService,
      OrganizationService organizationService) {
    this.visibilityDao = visibilityDao;
    this.keycloakUserService = keycloakUserService;
    this.organizationService = organizationService;
  }

  @Override
  public Future<Void> shareAssets(UUID itemId, String shareType, UUID sharedBy, List<UUID> ids) {

    if (USER.equalsIgnoreCase(shareType)) {

      return validateUsers(ids)
          .compose(v -> validateDuplicateUserShares(itemId, ids))
          .compose(v -> visibilityDao.shareWithUsers(itemId, sharedBy, ids));
    }

    return validateOrganizations(ids)
        .compose(v -> validateDuplicateOrganizationShares(itemId, ids))
        .compose(v -> visibilityDao.shareWithOrganizations(itemId, sharedBy, ids));
  }

  private Future<Void> validateUsers(List<UUID> userIds) {

    List<Future> futures =
        userIds.stream()
            .map(keycloakUserService::getUserById)
            .map(future -> future.mapEmpty())
            .collect(Collectors.toList());

    return CompositeFuture.all(futures).mapEmpty();
  }

  private Future<Void> validateOrganizations(List<UUID> orgIds) {

    List<Future> futures =
        orgIds.stream()
            .map(organizationService::getOrganizationById)
            .map(future -> future.mapEmpty())
            .collect(Collectors.toList());

    return CompositeFuture.all(futures).mapEmpty();
  }

  private Future<Void> validateDuplicateUserShares(UUID itemId, List<UUID> userIds) {

    List<Future> futures =
        userIds.stream()
            .map(
                userId ->
                    visibilityDao
                        .hasActiveUserShare(itemId, userId)
                        .compose(
                            exists -> {
                              if (exists) {
                                return Future.failedFuture(
                                    new DxConflictException(
                                        "Asset already shared with user : " + userId));
                              }

                              return Future.succeededFuture((Void) null);
                            }))
            .collect(Collectors.toList());

    return CompositeFuture.all(futures).mapEmpty();
  }

  private Future<Void> validateDuplicateOrganizationShares(UUID itemId, List<UUID> orgIds) {

    List<Future> futures =
        orgIds.stream()
            .map(
                orgId ->
                    visibilityDao
                        .hasActiveOrganizationShare(itemId, orgId)
                        .compose(
                            exists -> {
                              if (exists) {
                                return Future.failedFuture(
                                    new DxConflictException(
                                        "Asset already shared with organization : " + orgId));
                              }

                              return Future.succeededFuture((Void) null);
                            }))
            .collect(Collectors.toList());

    return CompositeFuture.all(futures).mapEmpty();
  }

  @Override
  public Future<Void> revokeAssets(UUID itemId, String shareType, List<UUID> ids) {

    if (USER.equalsIgnoreCase(shareType)) {
      return visibilityDao.revokeUserShare(itemId, ids);
    }

    return visibilityDao.revokeOrganizationShare(itemId, ids);
  }

  @Override
  public Future<List<VisibilityEntity>> getAssetsSharedWithMe(UUID userId, String orgId) {

    return visibilityDao.getAssetsSharedWithMe(userId, orgId);
  }

  @Override
  public Future<List<VisibilityEntity>> getVisibilityDetails(UUID itemId) {

    return visibilityDao.getVisibilityDetails(itemId);
  }
}
