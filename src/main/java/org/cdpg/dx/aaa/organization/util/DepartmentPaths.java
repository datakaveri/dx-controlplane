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
 *   "path": "google.engineering.platform.data_services"
 * }
 * </pre>
 *
 * {@code path} is the node's ltree path as stored. Its labels are slugified, so display names in
 * {@code hierarchy} come from walking {@code parentId} over the organisation's nodes.
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
    OrganizationHierarchyNode node = node(nodeId);
    if (node == null) {
      return null;
    }

    JsonArray hierarchy = new JsonArray();
    for (OrganizationHierarchyNode step : chain(node)) {
      hierarchy.add(new JsonObject().put("id", step.id().toString()).put("name", step.name()));
    }

    return new JsonObject()
        .put("id", node.id().toString())
        .put("name", node.name())
        .put("path", node.path())
        .put("hierarchy", hierarchy);
  }

  /**
   * The chain's display names joined with " / " (e.g. "Google / Engineering / Platform"), for
   * human-facing text such as emails; null if {@code nodeId} is null or not a known node.
   */
  public String label(UUID nodeId) {
    OrganizationHierarchyNode node = node(nodeId);
    if (node == null) {
      return null;
    }
    return String.join(
        LABEL_SEPARATOR, chain(node).stream().map(OrganizationHierarchyNode::name).toList());
  }

  private OrganizationHierarchyNode node(UUID nodeId) {
    return nodeId == null ? null : nodesById.get(nodeId);
  }

  /** {@code node} and its ancestors, top-level first; stops on a cycle or a missing parent. */
  private Deque<OrganizationHierarchyNode> chain(OrganizationHierarchyNode node) {
    Deque<OrganizationHierarchyNode> chain = new ArrayDeque<>();
    Set<UUID> seen = new HashSet<>();
    for (OrganizationHierarchyNode current = node;
        current != null && seen.add(current.id());
        current = current.parentId() == null ? null : nodesById.get(current.parentId())) {
      chain.addFirst(current);
    }
    return chain;
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
