package org.cdpg.dx.common.model;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public enum AuditAction {
  USER_ACTION {
    @Override
    public Map<String, String> getActions() {
      return Arrays.stream(UserAction.values())
        .collect(LinkedHashMap::new,
          (m, e) -> m.put(e.name(), e.getDescription()),
          Map::putAll);
    }
  },
  COMPUTE {
    @Override
    public Map<String, String> getActions() {
      return Arrays.stream(ComputeAction.values())
        .collect(LinkedHashMap::new,
          (m, e) -> m.put(e.name(), e.getDescription()),
          Map::putAll);
    }
  },
  ASSET {
    @Override
    public Map<String, String> getActions() {
      return Arrays.stream(AssetAction.values())
        .collect(LinkedHashMap::new,
          (m, e) -> m.put(e.name(), e.getDescription()),
          Map::putAll);
    }
  };

  public abstract Map<String, String> getActions();

  // Inner enums
  public enum UserAction {
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
    VERIFY("Verify kyc"),
    CONFIRM("confirm kyc"),
    REVOKE("Revoke kyc"),
    CREATE("Create data upload type request SFTP/API"),
    GET_DATA_INGESTION_TYPE("Get data upload type requests"),
    DELETE("Delete pending data type upload request"),
    UPDATE("Update data upload type requests"),
    CREATE_SUBSCRIPTION("Create subscription"),
    UPDATE_SUBSCRIPTION("Update subscription"),
    Delete_SUBSCRIPTION("Delete subscription"),
      VIEW_SUBSCRIPTION("View subscription"),
    LIST_SUBSCRIPTION("List subscription");

    private final String description;
    UserAction(String description) { this.description = description; }
    public String getDescription() { return description; }
  }

  public enum ComputeAction {
    UPDATE("Update request status"),
    REQUEST_COMPUTE("Request for compute role"),
    REQUEST_CREDITS("Request for credits"),
    CREDIT("Addition of credits"),
    DEBIT("Debit of credits"),
    GET_CREDIT_REQUESTS("Get credit requests"),
    GET_COMPUTE_REQUESTS("Get compute requests"),
    GET_BALANCE("Get Balance"),
    DELETE("Delete pending request");

    private final String description;
    ComputeAction(String description) { this.description = description; }
    public String getDescription() { return description; }
  }

  public enum AssetAction {
    VIEW("View"),
    DOWNLOAD("Download"),
    UPLOAD("Upload"),
    CREATE("Create"),
    UPDATE("Update"),
    DELETE("Delete"),
    GRANT("Download Access Granted"),
    WITHDRAW("Download Access Withdrawn"),
    REJECT("Download Access Rejected"),
    REQUEST("Download Access Requested");

    private final String description;
    AssetAction(String description) { this.description = description; }
    public String getDescription() { return description; }
  }
}
