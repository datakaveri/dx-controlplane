package org.cdpg.dx.auditing.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum EntityType {
  AI_MODEL,
  DATABANK,
  APPS,
  USECASE,
  ASSET,
  USER_ACCOUNT,
  ORGANIZATION,
  ORG_REQUEST,
  CREDIT_REQUEST,
  COMPUTE_REQUEST,
  DELEGATION,
  POLICY,
  RESOURCE_SERVER,
  ACCESS_REQUEST,
  KYC;

  @JsonCreator
  public static EntityType from(String value) {
    return EntityType.valueOf(value.toUpperCase());
  }
}
