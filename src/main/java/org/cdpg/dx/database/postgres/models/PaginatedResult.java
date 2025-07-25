package org.cdpg.dx.database.postgres.models;

import java.util.List;
import org.cdpg.dx.common.util.PaginationInfo;
import org.cdpg.dx.database.postgres.base.entity.BaseEntity;

public record PaginatedResult<T extends BaseEntity<T>>(
    /* int page,
    int size,
    long totalCount,
    int totalPages,
    boolean hasNext,
    boolean hasPrevious,*/
    PaginationInfo paginationInfo, List<T> data) {}
