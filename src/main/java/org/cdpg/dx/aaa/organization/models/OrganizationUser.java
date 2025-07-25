package org.cdpg.dx.aaa.organization.models;

import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.organization.config.Constants;
import org.cdpg.dx.common.exception.DxValidationException;
import org.cdpg.dx.database.postgres.base.entity.BaseEntity;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.cdpg.dx.common.util.DateTimeHelper.FORMATTER;
import static org.cdpg.dx.common.util.DateTimeHelper.parseDateTime;
import static org.cdpg.dx.common.util.ValidationUtils.requireNonNull;

public record OrganizationUser(
        UUID id,
        UUID organizationId,
        UUID userId,
        String userName,
        Role role,
        String jobTitle,
        String empId,
        String orgManagerPhoneNo,
        String officialEmail,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) implements BaseEntity<OrganizationUser> {

    public static OrganizationUser fromJson(JsonObject json) {
        try {
            return new OrganizationUser(
                    json.getString(Constants.ORG_USER_ID) != null
                            ? UUID.fromString(json.getString(Constants.ORG_USER_ID))
                            : null,
                    UUID.fromString(json.getString(Constants.ORGANIZATION_ID)),
                    UUID.fromString(json.getString(Constants.USER_ID)),
                    json.getString(Constants.USER_NAME),
                    Role.fromString(requireNonNull(json.getString(Constants.ROLE), Constants.ROLE)),
                    requireNonNull(json.getString(Constants.JOB_TITLE), Constants.JOB_TITLE),
                    requireNonNull(json.getString(Constants.EMP_ID), Constants.EMP_ID),
                    json.getString(Constants.PHONE_NO),
                    requireNonNull(json.getString(Constants.OFFICIAL_EMAIL), Constants.OFFICIAL_EMAIL),
                    parseDateTime(json.getString(Constants.CREATED_AT)),
                    parseDateTime(json.getString(Constants.UPDATED_AT)
                    ));
        } catch (IllegalArgumentException e) {
            throw new DxValidationException("Missing or invalid required field: " + e.getMessage());
        }
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();

        if (id != null) json.put(Constants.ORG_USER_ID, id.toString());
        json.put(Constants.ORGANIZATION_ID, organizationId.toString());
        json.put(Constants.USER_ID, userId.toString());
        json.put(Constants.USER_NAME, userName);
        json.put(Constants.ROLE, role.getRoleName());
        json.put(Constants.JOB_TITLE, jobTitle);
        json.put(Constants.EMP_ID, empId);
        if (orgManagerPhoneNo != null && !orgManagerPhoneNo.isEmpty())
            json.put(Constants.PHONE_NO, orgManagerPhoneNo);
        json.put(Constants.OFFICIAL_EMAIL, officialEmail);
        if (createdAt != null) json.put(Constants.CREATED_AT, createdAt.format(FORMATTER));
        if (updatedAt != null) json.put(Constants.UPDATED_AT, updatedAt.format(FORMATTER));

        return json;
    }

    @Override
    public Map<String, Object> toNonEmptyFieldsMap() {
        Map<String, Object> map = new HashMap<>();

        if (id != null) map.put(Constants.ORG_USER_ID, id.toString());
        map.put(Constants.ORGANIZATION_ID, organizationId.toString());
        map.put(Constants.USER_ID, userId.toString());
        map.put(Constants.USER_NAME, userName);
        map.put(Constants.ROLE, role.getRoleName());
        if (!jobTitle.isEmpty()) map.put(Constants.JOB_TITLE, jobTitle);
        if (!empId.isEmpty()) map.put(Constants.EMP_ID, empId);
        if (orgManagerPhoneNo != null && !orgManagerPhoneNo.isEmpty())
            map.put(Constants.PHONE_NO, orgManagerPhoneNo);
        map.put(Constants.OFFICIAL_EMAIL, officialEmail);
        if (createdAt != null) map.put(Constants.CREATED_AT, createdAt.format(FORMATTER));
        if (updatedAt != null) map.put(Constants.UPDATED_AT, updatedAt.format(FORMATTER));

        return map;
    }

    @Override
    public String getTableName() {
        return Constants.ORG_USER_TABLE;
    }
}

