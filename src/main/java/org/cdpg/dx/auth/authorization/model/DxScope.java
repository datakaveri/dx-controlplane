package org.cdpg.dx.auth.authorization.model;

import org.cdpg.dx.common.model.DxUser;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public enum DxScope {
  ORG_MANAGEMENT("org_management"),
  DATA_ACCESS("data_access"),
  COS_ADMIN("cos_admin_access"),
  COMPUTE_MANAGEMENT("compute_management"),
  USER_MANAGEMENT("user_management"),
  ASSET_MANAGEMENT("asset_management"),
  WILDCARD("*");

  private final String scope;

  private static final Map<String, DxScope> SCOPE_LOOKUP =
    Arrays.stream(values())
      .collect(Collectors.toMap(r -> r.scope.toLowerCase(), r -> r));

  DxScope(String scope) {
    this.scope = scope;
  }

  public String getScope() {
    return scope;
  }

  public static DxScope fromString(String scope) {
    return SCOPE_LOOKUP.get(scope.toLowerCase());
  }

  @Override
  public String toString() {
    return scope;
  }
}
