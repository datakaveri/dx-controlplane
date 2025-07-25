package org.cdpg.dx.keycloak.util;

import io.vertx.core.json.JsonObject;
import org.cdpg.dx.keycloak.config.KeycloakConstants;
import org.cdpg.dx.common.model.DxUser;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import java.util.*;
import java.util.stream.Collectors;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;


public class DxUserMapper {
    public static DxUser fromUserRepresentation(UserRepresentation user, List<RoleRepresentation> roles) {
        Map<String, List<String>> attrs = Optional.ofNullable(user.getAttributes()).orElse(Map.of());
        List<String> roleNames = roles.stream().map(RoleRepresentation::getName).collect(Collectors.toList());

        Long createdTs = user.getCreatedTimestamp();
        LocalDateTime createdAt = createdTs != null
                ? Instant.ofEpochMilli(createdTs).atZone(ZoneOffset.UTC).toLocalDateTime()
                : null;

        return new DxUser(
                roleNames,
                getAttr(attrs, KeycloakConstants.ORGANISATION_ID),
                getAttr(attrs, KeycloakConstants.ORGANISATION_NAME),
                UUID.fromString(user.getId()),
                user.isEmailVerified(),
                Boolean.parseBoolean(getAttr(attrs, KeycloakConstants.KYC_VERIFIED)),
                user.getFirstName() + " " + user.getLastName(),
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                new ArrayList<>(),
                new JsonObject(),
                createdAt,
                getAttr(attrs, "aadhaar_kyc_data").isBlank() ? new JsonObject() : new JsonObject(getAttr(attrs, "aadhaar_kyc_data")),
                getAttr(attrs, "twitter_account") != null ? getAttr(attrs, "twitter_account") : "",
                getAttr(attrs, "linkedin_account") != null ? getAttr(attrs, "linkedin_account") : "",
                getAttr(attrs, "github_account") != null ? getAttr(attrs, "github_account") : "",
                user.isEnabled()
        );
    }


    private static String getAttr(Map<String, List<String>> attrs, String key) {
        return attrs.getOrDefault(key, List.of("")).get(0);
    }
}
