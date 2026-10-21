package org.cdpg.dx.aaa.organization.models;

import static org.cdpg.dx.common.util.DateTimeHelper.FORMATTER;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.vertx.core.json.JsonObject;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.cdpg.dx.aaa.organization.config.Constants;
import org.cdpg.dx.database.postgres.base.entity.BaseEntity;

public record OrganizationHierarchyNode(
    UUID id,
    UUID organizationId,
    UUID parentId,
    String name,
    int level,
    String path,
    LocalDateTime createdAt,
    LocalDateTime updatedAt)
    implements BaseEntity<OrganizationHierarchyNode> {

  public static OrganizationHierarchyNode fromJson(JsonObject jsonObject) {
    // The table's own primary key column is literally "id" (see
    // V85__add_organization_hierarchy_support.sql) -- Constants.HIERARCHY_NODE_ID
    // ("hierarchy_node_id") is the FK column name used on *other* tables that
    // reference this one, not this table's own PK, so it must not be used here.
    String idStr = jsonObject.getString("id");
    String parentIdStr = jsonObject.getString(Constants.PARENT_ID);

    return new OrganizationHierarchyNode(
        idStr != null ? UUID.fromString(idStr) : null,
        UUID.fromString(jsonObject.getString(Constants.ORGANIZATION_ID)),
        parentIdStr != null ? UUID.fromString(parentIdStr) : null,
        jsonObject.getString(Constants.NODE_NAME),
        jsonObject.getInteger(Constants.LEVEL),
        jsonObject.getString(Constants.PATH),
        LocalDateTime.parse(jsonObject.getString(Constants.CREATED_AT)),
        LocalDateTime.parse(jsonObject.getString(Constants.UPDATED_AT)));
  }

  @Override
  public Map<String, Object> toNonEmptyFieldsMap() {
    Map<String, Object> fields = new HashMap<>();

    if (id != null) {
      fields.put("id", id.toString());
    }

    if (organizationId != null) {
      fields.put(Constants.ORGANIZATION_ID, organizationId.toString());
    }

    if (parentId != null) {
      fields.put(Constants.PARENT_ID, parentId.toString());
    }

    if (name != null) {
      fields.put(Constants.NODE_NAME, name);
    }

    fields.put(Constants.LEVEL, level);

    if (path != null) {
      fields.put(Constants.PATH, path);
    }

    if (createdAt != null) {
      fields.put(Constants.CREATED_AT, createdAt.format(FORMATTER));
    }

    if (updatedAt != null) {
      fields.put(Constants.UPDATED_AT, updatedAt.format(FORMATTER));
    }

    return fields;
  }

  @Override
  public JsonObject toJson() {
    JsonObject json = new JsonObject();

    if (id != null) json.put("id", id.toString());
    if (organizationId != null) json.put("organizationId", organizationId.toString());
    if (parentId != null) json.put("parentId", parentId.toString());
    json.put("name", name);
    json.put("level", level);
    json.put("path", path);
    if (createdAt != null) json.put("createdAt", createdAt.toString());
    if (updatedAt != null) json.put("updatedAt", updatedAt.toString());

    return json;
  }

  @Override
  @JsonIgnore
  public String getTableName() {
    return Constants.ORGANIZATION_HIERARCHY_NODE_TABLE;
  }
}
