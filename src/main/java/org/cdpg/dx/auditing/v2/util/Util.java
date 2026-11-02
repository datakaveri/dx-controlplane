package org.cdpg.dx.auditing.v2.util;

import static org.cdpg.dx.auditing.v2.Constant.ActivityApiParamConstants.ALLOWED_FILTER_MAP_FOR_ADMIN_V2;
import static org.cdpg.dx.auditing.v2.Constant.UserActivityAuditSchema.ACTOR_TYPE;
import static org.cdpg.dx.auditing.v2.Constant.UserActivityAuditSchema.DELEGATE_ID;
import static org.cdpg.dx.auditing.v2.Constant.UserActivityAuditSchema.ORG_ID;
import static org.cdpg.dx.auditing.v2.Constant.UserActivityAuditSchema.USER_ID;

import io.vertx.ext.web.RoutingContext;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.cdpg.dx.auditing.v2.model.ActorType;
import org.cdpg.dx.auth.authorization.model.AuthLevel;
import org.cdpg.dx.auth.authorization.model.AuthorizationContext;

public class Util {

  private Util() {}

  public static Map<String, Object> getAdditionalFilters(AuthorizationContext authCtx) {
    if (authCtx.getLevel() == AuthLevel.ORG) {
      return Map.of("org_Id", authCtx.getOrgId());
    }
    return Map.of();
  }

  /**
   * When a consumer request went through header-based delegation (the {@code did} header),
   * forces the results down to just this delegate's own actions rather than everything visible to
   * the delegator being acted as. Not applied on admin endpoints: a delegate that passes the admin
   * scope check gets the full admin view. Returns an empty map for non-delegated requests.
   */
  public static Map<String, Object> getDelegationFilters(RoutingContext ctx) {
    UUID delegateId = AuditContextExtractor.getDelegateId(ctx);
    if (delegateId == null) {
      return Map.of();
    }
    return Map.of(DELEGATE_ID, delegateId.toString(), ACTOR_TYPE, ActorType.DELEGATE.name());
  }

  public static Map<String, String> getAllowedFilterMapForAdmin(AuthorizationContext authCtx) {
    Map<String, String> allowedFilter = new HashMap<>(ALLOWED_FILTER_MAP_FOR_ADMIN_V2);
    allowedFilter.put("orgId", ORG_ID);
    allowedFilter.put("userId", USER_ID);
    if (authCtx.getLevel() == AuthLevel.PLATFORM) {
      allowedFilter.put("orgId", ORG_ID);
    }
    return allowedFilter;
  }
}
