package org.cdpg.dx.aaa.delegation.util;

import org.cdpg.dx.common.exception.DxBadRequestException;
import java.util.List;

public enum RoleScopeMapping {

  COS_ADMIN("cos_admin", List.of(
    "cos_admin_access",
    "org_management",
    "asset_management",
    "compute_management",
    "credit_management"
  )),

  ORG_ADMIN("org_admin", List.of(
    "org_management",
    "asset_management",
    "data_access"
  )),

  PROVIDER("provider", List.of(
    "asset_management",
    "data_access"
  )),

  CONSUMER("consumer", List.of(
    "data_access",
    "api",
    "subs"
  ));

  private final String role;
  private final List<String> allowedScopes;

  RoleScopeMapping(String role, List<String> allowedScopes) {
    this.role = role;
    this.allowedScopes = allowedScopes;
  }

  public String getRole() {
    return role;
  }

  public List<String> getAllowedScopes() {
    return allowedScopes;
  }

  public static int getRoleRank(String role) {
    return switch (role.toLowerCase()) {
      case "cos_admin" -> 4;
      case "org_admin" -> 3;
      case "provider" -> 2;
      case "consumer" -> 1;
      default -> 0;
    };
  }

  public static RoleScopeMapping fromString(String roleStr) {
    for (RoleScopeMapping roleMapping : RoleScopeMapping.values()) {
      if (roleMapping.getRole().equalsIgnoreCase(roleStr)) {
        return roleMapping;
      }
    }
    throw new DxBadRequestException("Invalid role type: " + roleStr);
  }

  @Override
  public String toString() {
    return role;
  }
}
