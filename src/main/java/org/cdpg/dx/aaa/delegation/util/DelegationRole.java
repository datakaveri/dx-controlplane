package org.cdpg.dx.aaa.delegation.util;

import org.cdpg.dx.common.exception.DxBadRequestException;

public enum  DelegationRole {

  COS_ADMIN("cos_admin"),
  ORG_ADMIN("org_admin"),
  PROVIDER("provider"),
  CONSUMER("consumer"),
  COMPUTE("compute");

  private final String role;

  DelegationRole(String role) {
    this.role = role;
  }

  public String getRole() {
    return role;
  }

  public static DelegationRole fromString(String roleStr) {
    for (DelegationRole role : DelegationRole.values()) {
      if (role.role.equalsIgnoreCase(roleStr)) {
        return role;
      }
    }
    throw new DxBadRequestException("Invalid delegation role: " + roleStr);
  }
}
