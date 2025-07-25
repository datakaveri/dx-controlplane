package org.cdpg.dx.common.request;

import java.util.List;
import java.util.Map;
import org.cdpg.dx.database.postgres.models.OrderBy;

public record PaginatedRequest(
    int page,
    int size,
    Map<String, Object> filters,
    List<TemporalRequest> temporalRequests,
    List<OrderBy> orderByList) {}
