package org.cdpg.dx.aaa.aclserver.service;

import io.vertx.core.Future;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.aclserver.dao.AclServerDAO;
import org.cdpg.dx.aaa.aclserver.model.AclServer;
import org.cdpg.dx.common.exception.DxUnauthorizedException;
import org.cdpg.dx.common.util.ServiceErrorHelper;

public class AclServerServiceImpl implements AclServerService {
  private static final Logger LOGGER = LogManager.getLogger(AclServerServiceImpl.class);
  private final AclServerDAO dao;

  public AclServerServiceImpl(AclServerDAO dao) {
    this.dao = dao;
  }

  @Override
  public Future<AclServer> create(AclServer aclServer) {
    LOGGER.debug("Creating ACL server: {}", aclServer);
    return dao.create(aclServer);
  }

  @Override
  public Future<AclServer> get(UUID id) {
    return dao.get(id)
        .recover(
            ServiceErrorHelper.mapNotFound("No matching requestId found in acl servers table"));
  }

  @Override
  public Future<List<AclServer>> getAllByRole(String userRole, UUID userId) {
    LOGGER.debug("Getting acl servers by role: {} for user: {}", userRole, userId);
    if ("cos_admin".equals(userRole)) {
      return dao.getAll()
          .recover(
              ServiceErrorHelper.mapNotFound(
                  "No matching requestId found in " + "acl server table"));
    } else {
      // ORG admin can only see acl servers they created
      Map<String, Object> filters = Map.of("owner_id", userId.toString());
      return dao.getAllWithFilters(filters)
          .recover(
              ServiceErrorHelper.mapNotFound(
                  "No matching " + "requestId found in acl server table"));
    }
  }

  @Override
  public Future<Boolean> deleteByRole(UUID id, String userRole, UUID userId) {
    LOGGER.debug("Deleting acl server by role: {} for user: {}", userRole, userId);

    if ("cos_admin".equals(userRole)) {
      return dao.delete(id)
          .recover(
              ServiceErrorHelper.mapNotFound(
                  "No matching requestId found " + "in acl server table"));
    } else if ("org_admin".equals(userRole)) {
      return dao.get(id)
          .recover(
              ServiceErrorHelper.mapNotFound(
                  "No matching requestId found in acl server " + "table"))
          .compose(
              aclServer -> {
                if (aclServer.ownerId() != null && aclServer.ownerId().equals(userId)) {
                  return dao.delete(id);
                } else {

                  return Future.failedFuture(
                      new DxUnauthorizedException("You can only delete acl " + "servers you own"));
                }
              });
    } else {
      LOGGER.warn("Unsupported role for acl server deletion: {}", userRole);
      return Future.failedFuture(
          new DxUnauthorizedException("Not Authorized, should be a cos_admin or org_admin"));
    }
  }

  @Override
  public Future<AclServer> update(UUID uuid, Map<String, Object> updates) {
    return null;
  }
}
