package org.cdpg.dx.aaa.organization.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.vertx.core.json.JsonObject;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.cdpg.dx.aaa.organization.config.Constants;
import org.cdpg.dx.common.exception.DxValidationException;
import org.cdpg.dx.database.postgres.base.entity.BaseEntity;

import static org.cdpg.dx.common.util.DateTimeHelper.FORMATTER;
import static org.cdpg.dx.common.util.DateTimeHelper.parseDateTime;
import static org.cdpg.dx.common.util.ValidationUtils.requireNonNull;

/**
 * A one-time request to define an organization's entire department tree in
 * one go -- approved/rejected by cos_admin as a single atomic unit. Every
 * organization starts flat; this request is how it becomes hierarchical.
 *
 * <p>The organization itself is the implicit top of the tree, so the proposal
 * is the list of its top-level departments (each with nested sub-departments).
 * It is stored as {@code {"departments": [...]}} in the JSONB column.
 */
public record OrganizationHierarchyDefinitionRequest(
    UUID id,
    UUID organizationId,
    List<ProposedDepartmentNode> departments,
    String status,
    String remarks,
    LocalDateTime createdAt,
    LocalDateTime updatedAt)
    implements BaseEntity<OrganizationHierarchyDefinitionRequest> {

  public static final String DEPARTMENTS = "departments";

  public OrganizationHierarchyDefinitionRequest {
    departments = departments == null ? List.of() : List.copyOf(departments);
  }

  private static List<ProposedDepartmentNode> parseDepartments(JsonObject json) {
    Object raw = json.getValue(Constants.PROPOSED_HIERARCHY);
    JsonObject proposed = null;
    if (raw instanceof JsonObject jsonObject) {
      proposed = jsonObject;
    } else if (raw instanceof String str && !str.isBlank()) {
      proposed = new JsonObject(str);
    }
    if (proposed == null) {
      throw new IllegalArgumentException(Constants.PROPOSED_HIERARCHY);
    }
    return ProposedDepartmentNode.listFromJson(proposed.getJsonArray(DEPARTMENTS));
  }

  private JsonObject proposedHierarchyJson() {
    return new JsonObject().put(DEPARTMENTS, ProposedDepartmentNode.listToJson(departments));
  }

  public static OrganizationHierarchyDefinitionRequest fromJson(JsonObject json) {
    try {
      return new OrganizationHierarchyDefinitionRequest(
          json.getString("id") != null ? UUID.fromString(json.getString("id")) : null,
          UUID.fromString(
              requireNonNull(
                  json.getString(Constants.ORGANIZATION_ID), Constants.ORGANIZATION_ID)),
          parseDepartments(json),
          json.getString(Constants.STATUS) != null
              ? json.getString(Constants.STATUS)
              : Status.PENDING.getStatus(),
          json.getString(Constants.REMARKS),
          parseDateTime(json.getString(Constants.CREATED_AT)),
          parseDateTime(json.getString(Constants.UPDATED_AT)));
    } catch (IllegalArgumentException e) {
      throw new DxValidationException("Missing or invalid required field: " + e.getMessage());
    }
  }

  @Override
  public JsonObject toJson() {
    JsonObject json = new JsonObject();

    if (id != null) json.put("id", id.toString());
    json.put(Constants.ORGANIZATION_ID, organizationId.toString());
    json.put(Constants.PROPOSED_HIERARCHY, proposedHierarchyJson());
    json.put(Constants.STATUS, status);
    if (remarks != null) json.put(Constants.REMARKS, remarks);
    if (createdAt != null) json.put(Constants.CREATED_AT, createdAt.format(FORMATTER));
    if (updatedAt != null) json.put(Constants.UPDATED_AT, updatedAt.format(FORMATTER));

    return json;
  }

  @Override
  public Map<String, Object> toNonEmptyFieldsMap() {
    Map<String, Object> map = new HashMap<>();

    if (id != null) map.put("id", id.toString());
    map.put(Constants.ORGANIZATION_ID, organizationId.toString());
    map.put(Constants.PROPOSED_HIERARCHY, proposedHierarchyJson());
    if (status != null && !status.isEmpty()) map.put(Constants.STATUS, status);
    if (remarks != null) map.put(Constants.REMARKS, remarks);
    if (createdAt != null) map.put(Constants.CREATED_AT, createdAt.format(FORMATTER));
    if (updatedAt != null) map.put(Constants.UPDATED_AT, updatedAt.format(FORMATTER));

    return map;
  }

  @Override
  @JsonIgnore
  public String getTableName() {
    return Constants.ORG_HIERARCHY_DEFINITION_REQUEST_TABLE;
  }
}
