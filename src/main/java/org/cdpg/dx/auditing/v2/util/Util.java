package org.cdpg.dx.auditing.v2.util;

import static org.cdpg.dx.auditing.v2.Constant.ActivityApiParamConstants.ALLOWED_FILTER_MAP_FOR_ADMIN_V2;
import static org.cdpg.dx.auditing.v2.Constant.UserActivityAuditSchema.ORG_ID;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.cdpg.dx.auth.authorization.model.DxRole;
import org.cdpg.dx.common.model.DxUser;

public class Util {

  public static Map<String, Object> getAdditionalFilters(DxUser user) {

    List<String> roles = user.roles();

    String organizationId = user.organisationId();
    Map<String, Object> additionalFilters = null;
    if (roles.contains(DxRole.ORG_ADMIN.getRole())) {
      additionalFilters = Map.of("org_Id", organizationId);

    } else {
      additionalFilters = Map.of();
    }
    return additionalFilters;
  }

  public static Map<String, String> getAllowedFilterMapForAdmin(DxUser user) {
    List<String> roles = user.roles();

    Map<String, String> allowedFilter = new HashMap<>(ALLOWED_FILTER_MAP_FOR_ADMIN_V2);

    if (roles.contains(DxRole.COS_ADMIN.getRole())) {
      allowedFilter.put("orgId", ORG_ID);
    }
    return allowedFilter;
  }
}
