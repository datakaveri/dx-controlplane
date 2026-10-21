package org.cdpg.dx.aaa.organization.models;

import java.util.List;
import java.util.UUID;

/**
 * The department subtree a dept_admin caller is restricted to -- published on
 * the routing context by {@code DeptAdminAccessHandler} only when the caller
 * is a dept_admin. Every other caller (org_admin, cos_admin, providers, ...)
 * has no scope and keeps the existing unrestricted org-level behaviour.
 *
 * <p>{@code nodeIds} is the administered department plus all of its
 * descendants (inclusive), never empty.
 */
public record DeptAdminScope(
    UUID organizationId, UUID userId, UUID adminOfNodeId, List<UUID> nodeIds) {

  /** Whether {@code nodeId} lies in the administered subtree (ancestor-or-self). */
  public boolean covers(UUID nodeId) {
    return nodeId != null && nodeIds.contains(nodeId);
  }

  /**
   * Like {@link #covers(UUID)}, but excluding the administered department
   * itself -- for actions a dept_admin must not take on their own department
   * (e.g. approving a request to become that department's admin).
   */
  public boolean coversStrictly(UUID nodeId) {
    return covers(nodeId) && !nodeId.equals(adminOfNodeId);
  }

  public List<String> nodeIdStrings() {
    return nodeIds.stream().map(UUID::toString).distinct().toList();
  }

  /** {@link #nodeIdStrings()} without the administered department itself. */
  public List<String> strictNodeIdStrings() {
    return nodeIds.stream()
        .filter(id -> !id.equals(adminOfNodeId))
        .map(UUID::toString)
        .distinct()
        .toList();
  }

  /**
   * The subtree narrowed by {@code filter}, for list filters -- never empty: when nothing
   * matches (e.g. {@link DepartmentFilter#SUB} on a leaf department) it is
   * {@link #NO_DEPARTMENT}, because an empty list filter is dropped by the query builders and
   * would otherwise widen the listing to the whole organization.
   */
  public List<String> nodeIdStrings(DepartmentFilter filter) {
    List<String> ids =
        switch (filter) {
          case ALL -> nodeIdStrings();
          case OWN -> List.of(adminOfNodeId.toString());
          case SUB -> strictNodeIdStrings();
        };
    return ids.isEmpty() ? List.of(NO_DEPARTMENT) : ids;
  }

  /** Nil UUID -- never a hierarchy node id, so filtering on it matches nothing. */
  public static final String NO_DEPARTMENT = "00000000-0000-0000-0000-000000000000";

  /** Which part of the subtree a dept_admin list endpoint returns ({@code ?departmentScope=}). */
  public enum DepartmentFilter {
    /** The administered department and all of its sub-departments (default). */
    ALL,
    /** Only the administered department itself. */
    OWN,
    /** Only the sub-departments, excluding the administered department itself. */
    SUB;

    public static DepartmentFilter fromParam(String value) {
      if (value == null || value.isBlank()) {
        return ALL;
      }
      for (DepartmentFilter filter : values()) {
        if (filter.name().equalsIgnoreCase(value.trim())) {
          return filter;
        }
      }
      throw new IllegalArgumentException(
          "Invalid departmentScope: " + value + " (expected one of all, own, sub)");
    }
  }
}
