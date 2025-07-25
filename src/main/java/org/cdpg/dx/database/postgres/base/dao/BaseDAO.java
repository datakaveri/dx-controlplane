package org.cdpg.dx.database.postgres.base.dao;

import io.vertx.core.Future;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.database.postgres.base.entity.BaseEntity;
import org.cdpg.dx.database.postgres.models.PaginatedResult;

public interface BaseDAO<T extends BaseEntity<T>> {

  Future<T> create(T entity);

  Future<Boolean> delete(UUID id);

  Future<List<T>> getAll();

  Future<List<T>> getAllWithFilters(Map<String, Object> filters);

  Future<T> update(Map<String, Object> conditionMap, Map<String, Object> updateMap);

  Future<T> get(UUID id);

  Future<PaginatedResult<T>> getAll(PaginatedRequest request);

  Future<PaginatedResult<T>> getAllWithFilters(PaginatedRequest request);
}
