package org.cdpg.dx.aaa.organization.models;

public enum OrganisationAuditOperation
{
  UPDATE_ORG_CREATE_REQUEST("Update organisation creation request status"),
  UPDATE_ORG_JOIN_REQUEST("Update organisation join request status"),
  UPDATE_PROVIDER_REQUEST("Update provider role requests"),
  REQUEST_ORG_JOIN("Request for joining an organisation"),
  REQUEST_ORG_CREATE("Request for creation of organisation"),
  REQUEST_PROVIDER_ROLE("Request for upgrading to provider role"),
  CREATE_PROVIDER_ROLE("Upgrade to provider role by admin"),
  WITHDRAW_PENDING_ORG_JOIN_REQUEST("Withdraw pending org join request"),
  WITHDRAW_PENDING_ORG_CREATE_REQUEST("Withdraw pending org create request"),
  GET("Get requests"),
  GET_PROVIDER_REQS("Get provider requests"),
  GET_ORG("List organisations"),
  GET_USER_INFO("Get user info"),
  GET_USERS("Get Users"),
  UPDATE_USER_INFO("Update user info"),
  DELETE_USER("Delete user"),
  DELETE_PENDING_ORG_CREATE_REQUEST("Delete pending org creation request"),
  DELETE_PENDING_ORG_JOIN_REQUEST("Delete pending org join request"),
  DELETE_PENDING_PROVIDER_REQUEST("Delete pending org join request"),
  DELETE_ORG("Delete Organisation"),
  UPDATE_ORG("Update org details"),
  REQUEST_PLATFORM_PROVIDER_ROLE("Request for platform provider role"),
  GET_PLATFORM_PROVIDER_REQS("Get platform provider role requests"),
  UPDATE_PLATFORM_PROVIDER_REQUEST("Update platform provider role request");
  ;


  private final String value;

  OrganisationAuditOperation(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
