package org.cdpg.dx.aaa.shareAssets.service.impl;

import static org.cdpg.dx.aaa.shareAssets.util.Constants.USER;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.aaa.shareAssets.dao.VisibilityDao;
import org.cdpg.dx.aaa.shareAssets.dao.model.VisibilityEntity;
import org.cdpg.dx.aaa.shareAssets.service.VisibilityService;
import org.cdpg.dx.common.exception.DxConflictException;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

public class VisibilityServiceImpl implements VisibilityService {

  private static final Logger LOGGER = LogManager.getLogger(VisibilityServiceImpl.class);

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

    return visibilityDao.getAssetsSharedWithMe(userId, orgId).compose(this::enrichWithDetails);
  }

  @Override
  public Future<List<VisibilityEntity>> getVisibilityDetails(UUID itemId) {

    return visibilityDao.getVisibilityDetails(itemId).compose(this::enrichWithDetails);
  }

  /**
   * Attaches user / organization / sharedBy details (names, email) to each share record, the same
   * way access-request and policy listings are enriched. Each distinct id is looked up once; a
   * failed lookup is logged and the record falls back to carrying only the id.
   */
  private Future<List<VisibilityEntity>> enrichWithDetails(List<VisibilityEntity> entities) {

    if (entities == null || entities.isEmpty()) {
      return Future.succeededFuture(entities);
    }

    Set<UUID> userIds = new LinkedHashSet<>();
    Set<UUID> orgIds = new LinkedHashSet<>();
    for (VisibilityEntity e : entities) {
      if (e.getUserId() != null) userIds.add(e.getUserId());
      if (e.getSharedBy() != null) userIds.add(e.getSharedBy());
      if (e.getOrgId() != null) orgIds.add(e.getOrgId());
    }

    Map<UUID, JsonObject> users = new HashMap<>();
    Map<UUID, JsonObject> orgs = new HashMap<>();
    List<Future<?>> futures = new ArrayList<>();

    for (UUID id : userIds) {
      futures.add(
          keycloakUserService
              .getUserById(id)
              .onSuccess(
                  u ->
                      users.put(
                          id,
                          new JsonObject()
                              .put("firstName", u.givenName())
                              .put("lastName", u.familyName())
                              .put("email", u.email())
                              .put("organization", u.organisationName())))
              .<Void>mapEmpty()
              .recover(
                  err -> {
                    LOGGER.warn("Failed to fetch user {}: {}", id, err.getMessage());
                    return Future.succeededFuture();
                  }));
    }

    for (UUID id : orgIds) {
      futures.add(
          organizationService
              .getOrganizationById(id)
              .onSuccess(o -> orgs.put(id, new JsonObject().put("name", o.orgName())))
              .<Void>mapEmpty()
              .recover(
                  err -> {
                    LOGGER.warn("Failed to fetch organization {}: {}", id, err.getMessage());
                    return Future.succeededFuture();
                  }));
    }

    return Future.all(futures)
        .map(
            v -> {
              for (VisibilityEntity e : entities) {
                e.setUserDetails(e.getUserId() != null ? users.get(e.getUserId()) : null)
                    .setOrganizationDetails(e.getOrgId() != null ? orgs.get(e.getOrgId()) : null)
                    .setSharedByDetails(
                        e.getSharedBy() != null ? users.get(e.getSharedBy()) : null);
              }
              return entities;
            });
  }
}
