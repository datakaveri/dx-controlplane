package org.cdpg.dx.auth.authentication.lookup.local;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.cdpg.dx.aaa.delegation.service.DelegationService;
import org.cdpg.dx.aaa.delegation.util.Status;
import org.cdpg.dx.auth.authentication.lookup.DelegationLookup;
import org.cdpg.dx.auth.model.DelegationRecord;

/**
 * In-process {@link DelegationLookup} for dx-controlplane. Wraps {@link
 * DelegationService#getAllDelegationsOfDelegate(String)} and filters for the active grant where
 * the delegator matches.
 */
public final class LocalDelegationLookup implements DelegationLookup {

  private static final ZoneId EXPIRY_ZONE = ZoneId.of("Asia/Kolkata");

  private final DelegationService delegationService;

  public LocalDelegationLookup(DelegationService delegationService) {
    this.delegationService = Objects.requireNonNull(delegationService, "delegationService");
  }

  @Override
  public Future<Optional<DelegationRecord>> findActive(String delegatorSub, String delegateeSub) {
    if (delegatorSub == null || delegateeSub == null) {
      return Future.succeededFuture(Optional.empty());
    }
    return delegationService
        .getAllDelegationsOfDelegate(delegateeSub)
        .map(
            grants -> {
              if (grants == null || grants.isEmpty()) {
                return Optional.<DelegationRecord>empty();
              }
              for (JsonObject grant : grants) {
                if (matches(grant, delegatorSub)) {
                  return Optional.of(toRecord(grant, delegatorSub, delegateeSub));
                }
              }
              return Optional.<DelegationRecord>empty();
            });
  }

  private static boolean matches(JsonObject grant, String delegatorSub) {
    if (!delegatorSub.equals(grant.getString("delegator_id"))) return false;
    String status = grant.getString("status");
    return Status.ACTIVE.getStatus().equalsIgnoreCase(status);
  }

  private static DelegationRecord toRecord(JsonObject grant, String delegatorSub, String delegateeSub) {
    Set<String> scopes = new HashSet<>();
    boolean fullDelegation = false;

    JsonArray constraints = grant.getJsonArray("constraints");
    if (constraints != null) {
      for (int i = 0; i < constraints.size(); i++) {
        JsonObject c = constraints.getJsonObject(i);
        if (c == null) continue;
        String scope = c.getString("scope");
        if ("*".equals(scope)) {
          fullDelegation = true;
        } else if (scope != null && !scope.isBlank()) {
          scopes.add(scope);
        }
      }
    }

    long expiresAt = 0L;
    String expiryStr = grant.getString("expiry_at");
    if (expiryStr != null && !expiryStr.isBlank()) {
      try {
        expiresAt = LocalDateTime.parse(expiryStr).atZone(EXPIRY_ZONE).toInstant().getEpochSecond();
      } catch (Exception ignored) {
        // leave 0 → resolver treats as no expiry
      }
    }

    return new DelegationRecord(delegatorSub, delegateeSub, scopes, fullDelegation, true, expiresAt);
  }
}