package org.cdpg.dx.auth.authorization.model;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public enum DxRole {
  CONSUMER("consumer"),
  PROVIDER("provider"),
  COS_ADMIN("cos_admin"),
  ORG_ADMIN("org_admin"),
  COMPUTE("compute");

  private final String role;

  private static final Map<String, DxRole> ROLE_LOOKUP =
          Arrays.stream(values())
                  .collect(Collectors.toMap(r -> r.role.toLowerCase(), r -> r));

  DxRole(String role) {
    this.role = role;
  }

  public String getRole() {
    return role;
  }

  public static Optional<DxRole> fromString(String role) {
    if (role == null || role.isEmpty()) return Optional.empty();
    return Optional.ofNullable(ROLE_LOOKUP.get(role.toLowerCase()));
  }

  @Override
  public String toString() {
    return role;
  }
}
