package org.cdpg.dx.aaa.organization.service.impl;

import io.vertx.core.Future;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.cdpg.dx.aaa.organization.dao.OrganizationDAO;
import org.cdpg.dx.aaa.organization.dao.OrganizationHierarchyNodeDAO;
import org.cdpg.dx.aaa.organization.models.OrganizationHierarchyNode;
import org.cdpg.dx.aaa.organization.models.ProposedDepartmentNode;
import org.cdpg.dx.aaa.organization.service.OrganizationHierarchyService;
import org.cdpg.dx.common.exception.DxConflictException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.exception.DxValidationException;

public class OrganizationHierarchyServiceImpl implements OrganizationHierarchyService {

  private final OrganizationDAO organizationDAO;
  private final OrganizationHierarchyNodeDAO hierarchyNodeDAO;

  public OrganizationHierarchyServiceImpl(
      OrganizationDAO organizationDAO, OrganizationHierarchyNodeDAO hierarchyNodeDAO) {
    this.organizationDAO = organizationDAO;
    this.hierarchyNodeDAO = hierarchyNodeDAO;
  }

  @Override
  public Future<Boolean> hasHierarchy(UUID organizationId) {
    return hierarchyNodeDAO.existsByOrganizationId(organizationId);
  }

  @Override
  public Future<OrganizationHierarchyNode> getNode(UUID organizationId, UUID nodeId) {
    return hierarchyNodeDAO.getByIdAndOrganizationId(nodeId, organizationId);
  }

  @Override
  public Future<List<OrganizationHierarchyNode>> getOrganizationNodes(UUID organizationId) {
    return hierarchyNodeDAO.getByOrganizationId(organizationId);
  }

  @Override
  public Future<List<OrganizationHierarchyNode>> getChildren(UUID organizationId, UUID parentId) {
    return hierarchyNodeDAO.getChildren(organizationId, parentId);
  }

  @Override
  public Future<List<OrganizationHierarchyNode>> getDescendants(UUID organizationId, UUID nodeId) {
    return hierarchyNodeDAO
        .getByIdAndOrganizationId(nodeId, organizationId)
        .compose(
            node -> {
              if (node == null) {
                return Future.failedFuture(new DxNotFoundException("Hierarchy node not found"));
              }

              return hierarchyNodeDAO.getDescendants(organizationId, node.path());
            });
  }

  @Override
  public Future<List<OrganizationHierarchyNode>> bulkCreateTree(
      UUID organizationId, List<ProposedDepartmentNode> departments) {

    if (departments == null || departments.isEmpty()) {
      return Future.failedFuture(
          new DxValidationException("Proposed department tree cannot be empty"));
    }

    return organizationDAO
        .get(organizationId)
        .compose(
            organization -> {
              if (organization == null) {
                return Future.failedFuture(new DxNotFoundException("Organization not found"));
              }

              return hierarchyNodeDAO.existsByOrganizationId(organizationId);
            })
        .compose(
            exists -> {
              if (exists) {
                return Future.failedFuture(
                    new DxConflictException(
                        "Organization already has a department hierarchy -- it can only be"
                            + " defined once"));
              }

              List<OrganizationHierarchyNode> precomputed = new ArrayList<>();
              try {
                buildSubtrees(organizationId, departments, null, precomputed);
              } catch (IllegalArgumentException e) {
                return Future.failedFuture(new DxValidationException(e.getMessage()));
              }

              return hierarchyNodeDAO.insertTree(precomputed);
            });
  }

  /**
   * Walk the proposed departments depth-first, parent before children,
   * generating a real id and computing the level and ltree path for every
   * department before anything is written to the database. The organization
   * is the implicit top: top-level departments are level 1 with no parent.
   * Throws {@link IllegalArgumentException} on a blank name or a duplicate
   * name under the same parent.
   */
  private void buildSubtrees(
      UUID organizationId,
      List<ProposedDepartmentNode> departments,
      OrganizationHierarchyNode parent,
      List<OrganizationHierarchyNode> result) {

    Set<String> siblingNames = new HashSet<>();
    for (ProposedDepartmentNode department : departments) {
      String normalizedName = normalizeNodeName(department.name());
      if (!siblingNames.add(normalizedName)) {
        throw new IllegalArgumentException(
            "Duplicate department name \""
                + department.name()
                + "\""
                + (parent == null ? " at the top level" : " under \"" + parent.name() + "\""));
      }

      OrganizationHierarchyNode node =
          new OrganizationHierarchyNode(
              UUID.randomUUID(),
              organizationId,
              parent == null ? null : parent.id(),
              department.name().trim(),
              parent == null ? 1 : parent.level() + 1,
              parent == null ? normalizedName : parent.path() + "." + normalizedName,
              null,
              null);
      result.add(node);

      buildSubtrees(organizationId, department.subDepartments(), node, result);
    }
  }

  @Override
  public Future<List<UUID>> getDescendantNodeIds(UUID organizationId, UUID nodeId) {
    return hierarchyNodeDAO
        .getByIdAndOrganizationId(nodeId, organizationId)
        .compose(
            node -> {
              if (node == null) {
                return Future.failedFuture(new DxNotFoundException("Hierarchy node not found"));
              }

              return hierarchyNodeDAO
                  .getDescendants(organizationId, node.path())
                  .map(
                      descendants -> {
                        List<UUID> ids = new ArrayList<>();
                        ids.add(node.id());
                        descendants.forEach(d -> ids.add(d.id()));
                        return ids;
                      });
            });
  }

  @Override
  public Future<Boolean> isAncestorOrSelf(
      UUID organizationId, UUID ancestorNodeId, UUID targetNodeId, boolean strict) {

    if (ancestorNodeId == null || targetNodeId == null) {
      return Future.succeededFuture(false);
    }

    if (ancestorNodeId.equals(targetNodeId)) {
      return Future.succeededFuture(!strict);
    }

    return hierarchyNodeDAO
        .getByIdAndOrganizationId(ancestorNodeId, organizationId)
        .compose(
            ancestor -> {
              if (ancestor == null) {
                return Future.succeededFuture(false);
              }

              return hierarchyNodeDAO
                  .getByIdAndOrganizationId(targetNodeId, organizationId)
                  .map(
                      target -> {
                        if (target == null) {
                          return false;
                        }

                        return target.path().equals(ancestor.path())
                            || target.path().startsWith(ancestor.path() + ".");
                      });
            });
  }

  private String normalizeNodeName(String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Hierarchy node name cannot be empty");
    }

    String normalized = name.trim().toLowerCase().replaceAll("[^a-z0-9_]", "_");

    if (normalized.isBlank()) {
      throw new IllegalArgumentException("Invalid hierarchy node name");
    }

    return normalized;
  }
}
