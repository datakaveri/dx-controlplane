package org.cdpg.dx.acl.policy.dao.model;

import static org.cdpg.dx.acl.accessRequest.config.Constants.OWNER_EMAIL;
import static org.cdpg.dx.acl.accessRequest.config.Constants.OWNER_FIRST_NAME;
import static org.cdpg.dx.acl.accessRequest.config.Constants.OWNER_LAST_NAME;
import static org.cdpg.dx.acl.accessRequest.config.Constants.OWNER_ORGANIZATION;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.ADDITIONAL_INFO;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.CONSUMER;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.CONSUMER_EMAIL;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.CONSUMER_FIRST_NAME;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.CONSUMER_ID;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.CONSUMER_LAST_NAME;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.CONSUMER_ORGANIZATION;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.OWNER_ID;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.POLICY_TABLE;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.USER;

import io.vertx.core.json.JsonObject;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.cdpg.dx.database.postgres.base.entity.BaseEntity;
import org.cdpg.dx.database.postgres.util.EntityUtil;

public class PolicyDto implements BaseEntity<PolicyDto> {

  private String policyId;
  private String requestId;
  private String policyType;
  private String itemId;
  private String itemType;
  private LocalDateTime expiryAt;
  private JsonObject constraints;
  private JsonObject additionalInfo;
  private String providerId;
  private String providerEmail;
  private String providerFirstName;
  private String providerLastName;
  private String providerOrganization;
  private String status;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private JsonObject consumer;
  private JsonObject provider;
  private String assetName;
  private String assetType;
  private String itemOrganizationId;
  private String itemOrganizationName;
  private String shortDescription;
  private String consumerId;
  private String consumerEmail;
  private String consumerFirstName;
  private String consumerLastName;
  private String consumerOrganization;


  public PolicyDto() {}

  public PolicyDto(JsonObject row) {
    this.policyId = row.getString("_id");
    this.requestId = row.getString("request_id");
    this.policyType = row.getString("policy_type");
    this.itemId = row.getString("item_id");
    this.itemType = row.getString("itemType");
    this.status = row.getString("status");

    if (row.containsKey("expiry_at") && row.getString("expiry_at") != null) {
      this.expiryAt = LocalDateTime.parse(row.getString("expiry_at"));
    }

    if (row.containsKey("created_at") && row.getString("created_at") != null) {
      this.createdAt = LocalDateTime.parse(row.getString("created_at"));
    }

    if (row.containsKey("updated_at") && row.getString("updated_at") != null) {
      this.updatedAt = LocalDateTime.parse(row.getString("updated_at"));
    }

    this.constraints = row.getJsonObject("constraints");
    this.additionalInfo = row.getJsonObject("additional_info");

    if (row.containsKey("consumer_id")) {
      this.consumerId = row.getString("consumer_id");
    }

    if (row.containsKey("owner_id")) {
      this.providerId = row.getString("owner_id");
    }
  }

  @Override
  public Map<String, Object> toNonEmptyFieldsMap() {
    Map<String, Object> map = new HashMap<>();
    EntityUtil.putIfPresent(map, "policyId", policyId);
    EntityUtil.putIfPresent(map, "policyType", policyType);
    EntityUtil.putIfPresent(map, "requestId", requestId);
    EntityUtil.putIfPresent(map, "itemId", itemId);
    EntityUtil.putIfPresent(map, "itemType", itemType);
    EntityUtil.putIfPresent(map, "status", status);
    EntityUtil.putIfPresent(map, "expiryAt", expiryAt);
    EntityUtil.putIfPresent(map, "constraints", constraints);
    EntityUtil.putIfPresent(map, "additionalInfo", additionalInfo);

    return map;
  }

  public JsonObject toJson() {
    return new JsonObject()
        .put("policyId", policyId)
        .put("requestId", requestId)
        .put("policyType", policyType)
        .put("status", status)
        .put("constraints", constraints)
        .put(ADDITIONAL_INFO, additionalInfo)
        .put(
            CONSUMER,
            new JsonObject()
                .put(CONSUMER_ID, getConsumerId())
                .put(CONSUMER_FIRST_NAME, getConsumerFirstName())
                .put(CONSUMER_LAST_NAME, getConsumerLastName())
                .put(CONSUMER_EMAIL, getConsumerEmail())
                .put(CONSUMER_ORGANIZATION, getConsumerOrganization()))
        .put(
            USER,
            new JsonObject()
                .put(OWNER_ID, getProviderId())
                .put(OWNER_FIRST_NAME, getProviderFirstName())
                .put(OWNER_LAST_NAME, getProviderLastName())
                .put(OWNER_EMAIL, getProviderEmail())
                .put(OWNER_ORGANIZATION, getProviderOrganization()))
        .put("asset", new JsonObject()
            .put("itemId", itemId)
            .put("assetName", assetName)
            .put("assetType", assetType)
            .put("shortDescription", shortDescription)
            .put("organization", itemOrganizationName)
            .put("organizationId", itemOrganizationId)
        )
        .put("expiryAt", expiryAt != null ? expiryAt.toString() : null)
        .put("createdAt", createdAt != null ? createdAt.toString() : null)
        .put("updatedAt", updatedAt != null ? updatedAt.toString() : null);
  }

  @Override
  public String getTableName() {
    return POLICY_TABLE;
  }

  // Getters and setters
  public String getPolicyId() { return policyId; }
  public PolicyDto setPolicyId(String policyId) { this.policyId = policyId; return this; }

  public String getItemId() { return itemId; }
  public PolicyDto setItemId(String itemId) { this.itemId = itemId; return this; }

  public String getItemType() { return itemType; }
  public PolicyDto setItemType(String itemType) { this.itemType = itemType; return this; }

  public LocalDateTime getExpiryAt() { return expiryAt; }
  public PolicyDto setExpiryAt(LocalDateTime expiryAt) { this.expiryAt = expiryAt; return this; }

  public JsonObject getConstraints() { return constraints; }
  public PolicyDto setConstraints(JsonObject constraints) { this.constraints = constraints; return this; }

  public String getStatus() { return status; }
  public PolicyDto setStatus(String status) { this.status = status; return this; }

  public LocalDateTime getCreatedAt() { return createdAt; }
  public PolicyDto setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }

  public LocalDateTime getUpdatedAt() { return updatedAt; }
  public PolicyDto setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }

  public JsonObject getConsumer() { return consumer; }
  public PolicyDto setConsumer(JsonObject consumer) { this.consumer = consumer; return this; }

  public JsonObject getProvider() { return provider; }
  public PolicyDto setProvider(JsonObject provider) { this.provider = provider; return this; }

  public String getAssetName() {
    return assetName;
  }

  public PolicyDto setAssetName(String assetName) {
    this.assetName = assetName;
    return this;
  }

  public String getAssetType() {
    return assetType;
  }

  public PolicyDto setAssetType(String assetType) {
    this.assetType = assetType;
    return this;
  }

  public String getItemOrganizationId() {
    return itemOrganizationId;
  }

  public PolicyDto setItemOrganizationId(String itemOrganizationId) {
    this.itemOrganizationId = itemOrganizationId;
    return this;
  }

  public String getItemOrganizationName() {
    return itemOrganizationName;
  }

  public PolicyDto setItemOrganizationName(String itemOrganizationName) {
    this.itemOrganizationName = itemOrganizationName;
    return this;
  }

  public String getShortDescription() {
    return shortDescription;
  }

  public PolicyDto setShortDescription(String shortDescription) {
    this.shortDescription = shortDescription;
    return this;
  }

  public PolicyDto setProviderId(String providerId) {
    this.providerId = providerId;
    return this;
  }

  public String getProviderId() {
    return providerId;
  }

  public String getConsumerId() {
    return consumerId;
  }

  public void setConsumerId(String consumerId) {
    this.consumerId = consumerId;
  }

  public String getConsumerEmail() {
    return consumerEmail;
  }

  public PolicyDto setConsumerEmail(String consumerEmail) {
    this.consumerEmail = consumerEmail;
    return this;
  }

  public String getConsumerFirstName() {
    return consumerFirstName;
  }

  public PolicyDto setConsumerFirstName(String consumerFirstName) {
    this.consumerFirstName = consumerFirstName;
    return this;
  }

  public String getConsumerLastName() {
    return consumerLastName;
  }

  public PolicyDto setConsumerLastName(String consumerLastName) {
    this.consumerLastName = consumerLastName;
    return this;
  }

  public String getConsumerOrganization() {
    return consumerOrganization;
  }

  public PolicyDto setConsumerOrganization(String consumerOrganization) {
    this.consumerOrganization = consumerOrganization;
    return this;
  }

  public JsonObject getAdditionalInfo() {
    return additionalInfo;
  }

  public PolicyDto setAdditionalInfo(JsonObject additionalInfo) {
    this.additionalInfo = additionalInfo;
    return this;
  }

  public String getRequestId() {
    return requestId;
  }

  public PolicyDto setRequestId(String requestId) {
    this.requestId = requestId;
    return this;
  }

  public String getPolicyType() {
    return policyType;
  }

  public PolicyDto setPolicyType(String policyType) {
    this.policyType = policyType;
    return this;
  }

  public String getProviderEmail() {
    return providerEmail;
  }

  public PolicyDto setProviderEmail(String providerEmail) {
    this.providerEmail = providerEmail;
    return this;
  }

  public String getProviderFirstName() {
    return providerFirstName;
  }

  public PolicyDto setProviderFirstName(String providerFirstName) {
    this.providerFirstName = providerFirstName;
    return this;
  }

  public String getProviderLastName() {
    return providerLastName;
  }

  public PolicyDto setProviderLastName(String providerLastName) {
    this.providerLastName = providerLastName;
    return this;
  }

  public String getProviderOrganization() {
    return providerOrganization;
  }

  public PolicyDto setProviderOrganization(String providerOrganization) {
    this.providerOrganization = providerOrganization;
    return this;
  }
}
