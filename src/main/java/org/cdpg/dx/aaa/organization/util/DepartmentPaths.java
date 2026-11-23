package org.cdpg.dx.aaa.organization.util;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.cdpg.dx.aaa.organization.models.OrganizationHierarchyNode;

/**
 * Describes a department by its place in the tree, for responses: its id and name plus the chain
 * of departments from the top-level one down to it.
 *
 * <pre>
 * {
 *   "id": "...", "name": "Data Services",
 *   "hierarchy": [{"id": "...", "name": "Google"}, ..., {"id": "...", "name": "Data Services"}],
 *   "hierarchyLabel": "Google / Engineering / Platform / Data Services"
 * }
 * </pre>
 *
 * Node {@code path}s are slugified ltree labels, so display names come from walking {@code
 * parentId} over the organisation's nodes.
 */
public final class DepartmentPaths {

  public static final String DEPARTMENT = "department";
  static final String LABEL_SEPARATOR = " / ";

  private final Map<UUID, OrganizationHierarchyNode> nodesById = new HashMap<>();

  public DepartmentPaths(Collection<OrganizationHierarchyNode> nodes) {
    nodes.forEach(node -> nodesById.put(node.id(), node));
  }

  /** The department's description, or null if {@code nodeId} is null or not a known node. */
  public JsonObject describe(UUID nodeId) {
    OrganizationHierarchyNode node = nodeId == null ? null : nodesById.get(nodeId);
    if (node == null) {
      return null;
    }

    Deque<OrganizationHierarchyNode> chain = new ArrayDeque<>();
    Set<UUID> seen = new HashSet<>();
    for (OrganizationHierarchyNode current = node;
        current != null && seen.add(current.id());
        current = current.parentId() == null ? null : nodesById.get(current.parentId())) {
      chain.addFirst(current);
    }

    JsonArray hierarchy = new JsonArray();
    StringBuilder label = new StringBuilder();
    for (OrganizationHierarchyNode step : chain) {
      hierarchy.add(new JsonObject().put("id", step.id().toString()).put("name", step.name()));
      if (!label.isEmpty()) {
        label.append(LABEL_SEPARATOR);
      }
      label.append(step.name());
    }

    return new JsonObject()
        .put("id", node.id().toString())
        .put("name", node.name())
        .put("hierarchy", hierarchy)
        .put("hierarchyLabel", label.toString());
  }

  /** Adds {@code "department"} to {@code json} when {@code nodeId} resolves; returns {@code json}. */
  public JsonObject addTo(JsonObject json, UUID nodeId) {
    JsonObject department = describe(nodeId);
    if (department != null) {
      json.put(DEPARTMENT, department);
    }
    return json;
  }
}
