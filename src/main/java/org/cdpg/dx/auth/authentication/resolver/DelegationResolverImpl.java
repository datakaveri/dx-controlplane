package org.cdpg.dx.auth.authentication.resolver;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import org.cdpg.dx.aaa.delegation.service.DelegationService;
import org.cdpg.dx.auth.authentication.resolver.DelegationResolver;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.common.util.DateTimeHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves a delegation by calling the in-process {@link DelegationService}, then validates the
 * delegation state and builds a {@link DxUser} with pre-computed capped scopes.
 */
public final class DelegationResolverImpl implements DelegationResolver {

  private static final Logger LOGGER = LoggerFactory.getLogger(DelegationResolverImpl.class);
  private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

  private final DelegationService delegationService;

  public DelegationResolverImpl(DelegationService delegationService) {
    this.delegationService = delegationService;
  }

  @Override
  public Future<DxUser> resolve(String delegatorSub, String delegateeSub) {
    return delegationService
        .findActiveDelegation(delegatorSub, delegateeSub)
        .compose(
            delegation -> {
              // 1. Status check
              String status = delegation.getString("status");
              if (!"active".equalsIgnoreCase(status)) {
                return Future.failedFuture(new DxForbiddenException("No active delegation"));
              }

              // 2. Expiry check
              String expiryAtStr = delegation.getString("expiry_at");
              if (expiryAtStr != null && !expiryAtStr.isBlank()) {
                LocalDateTime expiryLdt = DateTimeHelper.parseDateTime(expiryAtStr);
                if (expiryLdt != null) {
                  ZonedDateTime expiryZdt = expiryLdt.atZone(IST);
                  if (expiryZdt.isBefore(ZonedDateTime.now(IST))) {
                    return Future.failedFuture(new DxForbiddenException("Delegation has expired"));
                  }
                }
              }

              // 3. Delegator sub-object (produced by DelegationServiceImpl from dxUser.toJson() +
              //    "delegation_scope")
              JsonObject delegator = delegation.getJsonObject("delegator");
              if (delegator == null) {
                return Future.failedFuture(new DxForbiddenException("No active delegation"));
              }

              // 4. Delegator enabled check
              Boolean accountEnabled = delegator.getBoolean("account_enabled");
              if (!Boolean.TRUE.equals(accountEnabled)) {
                return Future.failedFuture(
                    new DxForbiddenException("Delegator is no longer active"));
              }

              // 5. Build DxUser from delegator JSON
              DxUser dxUser = buildDxUser(delegator, delegateeSub);
              return Future.succeededFuture(dxUser);
            })
        .recover(
            err -> {
              if (err instanceof DxNotFoundException) {
                return Future.failedFuture(new DxForbiddenException("No active delegation"));
              }
              return Future.failedFuture(err);
            });
  }

  private DxUser buildDxUser(JsonObject delegator, String delegateeSub) {
    UUID sub;
    try {
      sub = UUID.fromString(delegator.getString("sub"));
    } catch (Exception e) {
      sub = null;
    }

    String organisationId = delegator.getString("organisationId");
    String organisationName = delegator.getString("organisationName");

    JsonArray rolesArray = delegator.getJsonArray("roles", new JsonArray());
    List<String> roles = rolesArray.getList();

    boolean emailVerified = Boolean.TRUE.equals(delegator.getBoolean("emailVerified", false));
    boolean kycVerified = Boolean.TRUE.equals(delegator.getBoolean("kycVerified", false));

    String name = delegator.getString("name");
    String preferredUsername = delegator.getString("preferredUsername");
    String givenName = delegator.getString("givenName");
    String familyName = delegator.getString("familyName");
    String email = delegator.getString("email");

    Boolean accountEnabled = delegator.getBoolean("account_enabled");

    JsonArray scopes = delegator.getJsonArray("scopes");

    return new DxUser(
        roles,
        organisationId,
        organisationName,
        sub,
        emailVerified,
        kycVerified,
        name,
        preferredUsername,
        givenName,
        familyName,
        email,
        null, // pendingRoles
        null, // organisation
        null, // createdAt
        null, // kycData
        null, // twitter_account
        null, // linkedin_account
        null, // github_account
        accountEnabled,
        null, // did
        null, // aud
        scopes,
        delegateeSub, // delegateeId — set for audit
        null); // appId
  }
}
