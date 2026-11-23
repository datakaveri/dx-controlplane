package org.cdpg.dx.aaa.organization.service;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.organization.models.OrganizationHierarchyNode;
import org.cdpg.dx.aaa.organization.models.ProposedDepartmentNode;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface OrganizationHierarchyService {

  Future<OrganizationHierarchyNode> getNode(UUID organizationId, UUID nodeId);

  Future<List<OrganizationHierarchyNode>> getOrganizationNodes(UUID organizationId);

  /**
   * Departments of each given organization, keyed by organization id, each list ordered by
   * path. Organizations without a hierarchy are absent from the map.
   */
  Future<Map<UUID, List<OrganizationHierarchyNode>>> getNodesByOrganizationIds(
      List<UUID> organizationIds);

  Future<List<OrganizationHierarchyNode>> getChildren(UUID organizationId, UUID parentId);

  Future<List<OrganizationHierarchyNode>> getDescendants(UUID organizationId, UUID nodeId);

  /**
   * Whether the organization has a department tree. Every organization starts
   * flat; it becomes hierarchical only once an approved tree has been applied.
   */
  Future<Boolean> hasHierarchy(UUID organizationId);

  /**
   * Insert a whole proposed department tree -- the organization's top-level
   * {@code departments} and their nested sub-departments -- for an
   * organization that currently has no hierarchy nodes at all, in one atomic
   * statement. The organization itself is the implicit top; there is no root
   * node. All ids are generated server-side. Rejects (fails the future)
   * if the organization already has any existing hierarchy node -- the
   * hierarchy is defined exactly once.
   */
  Future<List<OrganizationHierarchyNode>> bulkCreateTree(
      UUID organizationId, List<ProposedDepartmentNode> departments);

  /**
   * Validate a proposed department tree without writing anything -- the same
   * checks {@link #bulkCreateTree} applies (non-empty, no blank names, no
   * duplicate names under the same parent). Fails with a validation error.
   */
  Future<Void> validateTree(List<ProposedDepartmentNode> departments);

  /**
   * Adds {@code departments} (each with its sub-departments) under the existing department
   * {@code parentNodeId}, or as top-level departments when it is null -- also how a flat
   * organisation gets its first departments. Names must not clash with existing departments at
   * the same place.
   */
  Future<List<OrganizationHierarchyNode>> addDepartments(
      UUID organizationId, UUID parentNodeId, List<ProposedDepartmentNode> departments);

  /** The checks of {@link #addDepartments} without writing anything. */
  Future<Void> validateAddition(
      UUID organizationId, UUID parentNodeId, List<ProposedDepartmentNode> departments);

  /** Ids of {@code nodeId} and all of its descendants (inclusive of itself). */
  Future<List<UUID>> getDescendantNodeIds(UUID organizationId, UUID nodeId);

  /**
   * Whether {@code ancestorNodeId} contains {@code targetNodeId} in its
   * subtree. When {@code strict} is false, a node is considered to contain
   * itself (ancestor-or-self); when true, a node administering its own
   * department does not count as containing itself.
   */
  Future<Boolean> isAncestorOrSelf(
      UUID organizationId, UUID ancestorNodeId, UUID targetNodeId, boolean strict);
}
