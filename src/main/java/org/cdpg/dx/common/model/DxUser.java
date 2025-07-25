package org.cdpg.dx.common.model;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.organization.models.OrganizationJoinRequest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record DxUser(
        List<String> roles,
        String organisationId,
        String organisationName,
        UUID sub,
        boolean emailVerified,
        boolean kycVerified,
        String name,
        String preferredUsername,
        String givenName,
        String familyName,
        String email,
        List<String> pendingRoles,
        JsonObject organisation,
        LocalDateTime createdAt,
        JsonObject kycData,
        String twitter_account,
        String linkedin_account,
        String github_account,
        Boolean account_enabled// newly added field
) {
    public JsonObject toJson() {
        String isoCreatedAt = createdAt != null
                ? createdAt.toString()
                : null;

        return new JsonObject()
                .put("roles", roles != null ? new JsonArray(roles) : new JsonArray())
                .put("organisationId", organisationId)
                .put("organisationName", organisationName)
                .put("sub", sub != null ? sub.toString() : null)
                .put("emailVerified", emailVerified)
                .put("kycVerified", kycVerified)
                .put("name", name)
                .put("preferredUsername", preferredUsername)
                .put("givenName", givenName)
                .put("familyName", familyName)
                .put("email", email)
                .put("pending_roles", pendingRoles != null ? new JsonArray(pendingRoles) : new JsonArray())
                .put("organisation", organisation != null ? organisation : new JsonObject())
                .put("createdAt", isoCreatedAt)
                .put("kycInformation", kycData)
                .put("twitter_account", twitter_account)
                .put("linkedin_account", linkedin_account)
                .put("github_account", github_account)
        .put("account_enabled", account_enabled);
    }

    public static DxUser withPendingRoles(DxUser user, List<String> pendingRoles, JsonObject organisation) {
        return new DxUser(
                user.roles(),
                user.organisationId(),
                user.organisationName(),
                user.sub(),
                user.emailVerified(),
                user.kycVerified(),
                user.name(),
                user.preferredUsername(),
                user.givenName(),
                user.familyName(),
                user.email(),
                pendingRoles,
                organisation,
                user.createdAt(),
                user.kycData(),
                user.twitter_account(),
                user.linkedin_account(),
                user.github_account(),
                user.account_enabled()// retain createdAt
        );
    }
}

