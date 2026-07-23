package org.cdpg.dx.auditing.v2.util;

import static org.cdpg.dx.auditing.v2.Constant.ActivityApiParamConstants.ALLOWED_FILTER_MAP_FOR_ADMIN_V2;
import static org.cdpg.dx.auditing.v2.Constant.UserActivityAuditSchema.ORG_ID;
import static org.cdpg.dx.auditing.v2.Constant.UserActivityAuditSchema.USER_ID;

import java.util.HashMap;
import java.util.Map;
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
