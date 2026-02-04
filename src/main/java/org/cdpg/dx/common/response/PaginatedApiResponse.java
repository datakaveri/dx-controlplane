package org.cdpg.dx.common.response;

import java.util.List;
import org.cdpg.dx.common.util.PaginationInfo;

public record PaginatedApiResponse<T>(List<T> result, PaginationInfo paginationInfo) {}
