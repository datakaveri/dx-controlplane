package org.cdpg.dx.aaa.organization.util;

import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.organization.models.OrganizationUser;
import org.cdpg.dx.aaa.organization.models.ProviderRoleRequest;
import org.cdpg.dx.common.model.DxUser;

public class ProviderRoleRequestMapper {
    public static JsonObject toJsonWithOrganisationUser(ProviderRoleRequest providerRoleRequest, OrganizationUser organizationUser) {
        return new JsonObject()
                .put("id", providerRoleRequest.id())
                .put("user_id", providerRoleRequest.userId())
                .put("organization_id", providerRoleRequest.orgId())
                .put("status", providerRoleRequest.status())
                .put("created_at", providerRoleRequest.createdAt())
                .put("updated_at", providerRoleRequest.updatedAt())
                .put("user_name", organizationUser.userName())
                .put("job_title", organizationUser.jobTitle())
                .put("emp_id", organizationUser.empId());

    }
}
