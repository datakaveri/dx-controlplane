package org.cdpg.dx.aaa.delegation.models;

import io.vertx.core.json.JsonObject;
import java.util.List;
import org.cdpg.dx.common.util.PaginationInfo;

/**
 * A page of delegation grants already enriched with their scope constraints and Keycloak user
 * information, paired with that page's pagination metadata.
 *
 * <p>Mirrors {@code UserInteractionsPaginatedResponse} and friends: the common {@code
 * PaginatedRequest} carries the query in, while the enriched result needs a module-local response
 * record because {@code PaginatedResult<T>} is bounded to {@code T extends BaseEntity<T>} and these
 * rows are merged {@link JsonObject}s rather than {@code DelegationGrant} entities.
 */
public record DelegationsPaginatedResponse(List<JsonObject> data, PaginationInfo paginationInfo) {}
