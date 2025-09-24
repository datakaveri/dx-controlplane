package org.cdpg.dx.aaa.resourceserver.service;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.resourceserver.models.ResourceServer;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.database.postgres.models.PaginatedResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ResourceServerService {
  Future<ResourceServer> create(ResourceServer rs);
  Future<ResourceServer> get(UUID id);
  Future<List<ResourceServer>> getAllByRole(String userRole, UUID userId);
  Future<Boolean> deleteByRole(UUID id, String userRole, UUID userId);

    Future<Object> update(UUID uuid, Map<String, Object> updates);
}


