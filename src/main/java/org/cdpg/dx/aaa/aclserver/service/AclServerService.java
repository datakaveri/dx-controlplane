package org.cdpg.dx.aaa.aclserver.service;

import io.vertx.core.Future;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.cdpg.dx.aaa.aclserver.model.AclServer;

public interface AclServerService {
  Future<AclServer> create(AclServer aclServer);
  Future<AclServer> get(UUID id);
  Future<List<AclServer>> getAllByRole(String userRole, UUID userId);
  Future<Boolean> deleteByRole(UUID id, String userRole, UUID userId);
  Future<AclServer> update(UUID uuid, Map<String, Object> updates);
}
