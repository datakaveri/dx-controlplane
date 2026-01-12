package org.cdpg.dx.auth.authorization.model;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public enum DxScope {
  USER_MANAGEMENT("user_management"),
  ORG_ADMIN_ACCESS("org_admin_access"),
  DATA_ACCESS("data_access"),
  COS_ADMIN_ACCESS("cos_admin_access"),
  COMPUTE_MANAGEMENT("compute_management"),
  CREDIT_MANAGEMENT("credit_management"),
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
