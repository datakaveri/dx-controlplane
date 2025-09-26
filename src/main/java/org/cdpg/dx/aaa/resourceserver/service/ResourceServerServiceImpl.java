package org.cdpg.dx.aaa.resourceserver.service;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.sqlclient.PreparedQuery;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.resourceserver.dao.ResourceServerDAO;
import org.cdpg.dx.aaa.resourceserver.models.ResourceServer;
import org.cdpg.dx.acl.accessRequest.controller.AccessRequestController;
import org.cdpg.dx.common.exception.BaseDxException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.exception.DxUnauthorizedException;
import org.cdpg.dx.common.exception.NoRowFoundException;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.database.postgres.models.PaginatedResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ResourceServerServiceImpl implements ResourceServerService {

  private final ResourceServerDAO dao;
    private static final Logger LOGGER = LogManager.getLogger(ResourceServerServiceImpl.class);

  public ResourceServerServiceImpl(ResourceServerDAO dao) {
    this.dao = dao;
  }

  @Override
  public Future<ResourceServer> create(ResourceServer rs) {
      LOGGER.debug("Creating Resource Server: " + rs);
    return dao.create(rs);
  }

  @Override
  public Future<ResourceServer> get(UUID id) {
      return dao.get(id)
              .recover(err -> {
                  BaseDxException dxEx = BaseDxException.from(err);
                  if (dxEx instanceof NoRowFoundException) {
                      LOGGER.debug("True");
                      return Future.failedFuture(new DxNotFoundException("No matching requestId found in resource table", dxEx));
                  }
                  return Future.failedFuture(dxEx);
              });
  }

  @Override
  public Future<List<ResourceServer>> getAllByRole(String userRole, UUID userId) {
    LOGGER.debug("Getting resource servers by role: {} for user: {}", userRole, userId);
    if ("cos_admin".equals(userRole)) {
      return dao.getAll().recover(err -> {
          BaseDxException dxEx = BaseDxException.from(err);
          if (dxEx instanceof NoRowFoundException) {
              LOGGER.debug("True");
              return Future.failedFuture(new DxNotFoundException("No matching requestId found in resource table", dxEx));
          }
          return Future.failedFuture(dxEx);
      });
    } else {
      // ORG admin can only see resource servers they created
      Map<String, Object> filters = Map.of("owner_id", userId.toString());
      return dao.getAllWithFilters(filters).recover(err -> {
          BaseDxException dxEx = BaseDxException.from(err);
          if (dxEx instanceof NoRowFoundException) {
              LOGGER.debug("True");
              return Future.failedFuture(new DxNotFoundException("No matching requestId found in resource table", dxEx));
          }
          return Future.failedFuture(dxEx);
      });
    }
  }

  @Override
  public Future<Boolean> deleteByRole(UUID id, String userRole, UUID userId) {
    LOGGER.debug("Deleting resource server by role: {} for user: {}", userRole, userId);
    
    if ("cos_admin".equals(userRole)) {
      return dao.delete(id).recover(err -> {
          BaseDxException dxEx = BaseDxException.from(err);
          if (dxEx instanceof NoRowFoundException) {
              LOGGER.debug("True");
              return Future.failedFuture(new DxNotFoundException("No matching requestId found in resource table", dxEx));
          }
          return Future.failedFuture(dxEx);
      });
    } else if ("org_admin".equals(userRole)) {
      return dao.get(id)
          .recover(err -> {
              BaseDxException dxEx = BaseDxException.from(err);
              if (dxEx instanceof NoRowFoundException) {
                  LOGGER.debug("True");
                  return Future.failedFuture(new DxNotFoundException("No matching requestId found in resource table", dxEx));
              }
              return Future.failedFuture(dxEx);
          })
          .compose(resourceServer -> {

            if (resourceServer.ownerId() != null && resourceServer.ownerId().equals(userId)) {
              return dao.delete(id);
            } else {
                
              return Future.failedFuture(new DxUnauthorizedException("You can only delete resource servers you own"));
            }
          });
    } else {
      LOGGER.warn("Unsupported role for resource server deletion: {}", userRole);
      return Future.failedFuture(new DxUnauthorizedException("Not Authorized, should be a cos_admin or org_admin"));
    }
  }

    @Override
    public Future<Object> update(UUID uuid, Map<String, Object> updates) {
        return null;
    }
}


