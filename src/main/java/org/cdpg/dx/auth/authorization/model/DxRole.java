package org.cdpg.dx.auth.authorization.model;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.cdpg.dx.common.model.DxUser;

public enum DxRole {
  DELEGATE("delegate"),
  CONSUMER("consumer"),
  PROVIDER("provider"),
  COS_ADMIN("cos_admin"),
  ORG_ADMIN("org_admin"),
  CONSUMER_DELEGATE("consumerDelegate"),
  PROVIDER_DELEGATE("providerDelegate"),
  COMPUTE("compute"),
  DELEGATOR("delegator");

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

  public static DxRole fromRoles(final DxUser user) {
    return user.roles().stream()
        .map(String::toUpperCase)
        .map(roleStr -> {
          if (roleStr.equals(DELEGATE.getRole().toUpperCase())) {
            return DELEGATE;
          } else if (roleStr.equals(PROVIDER.getRole().toUpperCase())) {
            return PROVIDER;
          } else if (roleStr.equals(CONSUMER.getRole().toUpperCase())) {
            return CONSUMER;
          } else {
            return null;
          }
        })
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  @Override
  public String toString() {
    return role;
  }
}
