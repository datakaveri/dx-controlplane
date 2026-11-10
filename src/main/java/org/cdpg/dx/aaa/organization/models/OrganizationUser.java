package org.cdpg.dx.aaa.organization.models;

import static org.cdpg.dx.common.util.DateTimeHelper.FORMATTER;
import static org.cdpg.dx.common.util.DateTimeHelper.parseDateTime;
import static org.cdpg.dx.common.util.ValidationUtils.requireNonNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.vertx.core.json.JsonObject;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.cdpg.dx.aaa.organization.config.Constants;
import org.cdpg.dx.common.exception.DxValidationException;
import org.cdpg.dx.database.postgres.base.entity.BaseEntity;

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
    UUID hierarchyNodeId,
    UUID adminOfNodeId,
    LocalDateTime createdAt,
    LocalDateTime updatedAt)
    implements BaseEntity<OrganizationUser> {

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
          json.getString(Constants.HIERARCHY_NODE_ID) != null
              ? UUID.fromString(json.getString(Constants.HIERARCHY_NODE_ID))
              : null,
          json.getString(Constants.ADMIN_OF_NODE_ID) != null
              ? UUID.fromString(json.getString(Constants.ADMIN_OF_NODE_ID))
              : null,
          parseDateTime(json.getString(Constants.CREATED_AT)),
          parseDateTime(json.getString(Constants.UPDATED_AT)));
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
        if (hierarchyNodeId != null) json.put(Constants.HIERARCHY_NODE_ID, hierarchyNodeId.toString());
        if (adminOfNodeId != null) json.put(Constants.ADMIN_OF_NODE_ID, adminOfNodeId.toString());
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
        if (hierarchyNodeId != null) map.put(Constants.HIERARCHY_NODE_ID, hierarchyNodeId.toString());
        if (adminOfNodeId != null) map.put(Constants.ADMIN_OF_NODE_ID, adminOfNodeId.toString());
        if (createdAt != null) map.put(Constants.CREATED_AT, createdAt.format(FORMATTER));
        if (updatedAt != null) map.put(Constants.UPDATED_AT, updatedAt.format(FORMATTER));

        return map;
    }

    @Override
    @JsonIgnore
    public String getTableName() {
        return Constants.ORG_USER_TABLE;
    }
}

