package org.cdpg.dx.acl.policy.service.model;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.cdpg.dx.catalogueService.models.ItemType;

public class CreatePolicyRequest {
  private static long defaultExpiryDays;
  private String userId;
  private String requestId;
  private String policyType;
  private String itemOrganizationId;
  private UUID itemId;
  private ItemType itemType;
  private LocalDateTime expiryTime;
  private JsonObject constraints;
  private JsonObject additionalInfo;
  private String providerComment;
  private String feedbackToConsumer;

  private static CreatePolicyRequest fromJsonToCreatePolicy(JsonObject jsonObject) {

    CreatePolicyRequest createPolicyRequest = new CreatePolicyRequest();
    createPolicyRequest.setConstraints(jsonObject.getJsonObject("constraints"));
    createPolicyRequest.setUserId(jsonObject.getString("userId"));
    createPolicyRequest.setItemId(jsonObject.getString("itemId"));
    createPolicyRequest.setRequestId(jsonObject.getString("requestId"));
    createPolicyRequest.setPolicyType(jsonObject.getString("policyType"));
    createPolicyRequest.setItemOrganizationId(jsonObject.getString("itemOrganizationId"));
    createPolicyRequest.setAdditionalInfo(jsonObject.getJsonObject("additionalInfo", null));
    createPolicyRequest.setProviderComment(jsonObject.getString("providerComment", null));
    createPolicyRequest.setFeedbackToConsumer(jsonObject.getString("feedbackToConsumer", null));
    createPolicyRequest.setItemType(
        ItemType.fromTypeValue(jsonObject.getString("itemType").toUpperCase()));
    createPolicyRequest.setExpiryTime(jsonObject.getString("expiryTime"));
    return createPolicyRequest;
  }

  public static List<CreatePolicyRequest> jsonArrayToList(
      JsonArray jsonArray, long defaultExpiryDays) {
    CreatePolicyRequest.defaultExpiryDays = defaultExpiryDays;
    List<CreatePolicyRequest> createPolicyRequestList = new ArrayList<>();

    List<JsonObject> jsonObjectList =
        IntStream.range(0, jsonArray.size())
            .mapToObj(jsonArray::getJsonObject)
            .collect(Collectors.toList());

    for (JsonObject jsonObject : jsonObjectList) {
      createPolicyRequestList.add(fromJsonToCreatePolicy(jsonObject));
    }
    return createPolicyRequestList;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getRequestId() {
    return requestId;
  }

  public void setRequestId(String requestId) {
    this.requestId = requestId;
  }

  public String getPolicyType() {
    return policyType;
  }

  public void setPolicyType(String policyType) {
    this.policyType = policyType;
  }

  public UUID getItemId() {
    return itemId;
  }

  public void setItemId(String itemId) {
    this.itemId = UUID.fromString(itemId);
  }

  public ItemType getItemType() {
    return itemType;
  }

  public void setItemType(ItemType itemType) {
    if (itemType != null) {
      if (Arrays.asList(ItemType.values()).contains(itemType)) {
        this.itemType = itemType;
      } else {
        throw new IllegalArgumentException(
            "Invalid item type. Allowed values are 'RESOURCE' and 'RESOURCE_GROUP'.");
      }
    } else {
      throw new IllegalArgumentException("Item type cannot be null.");
    }
  }

  public LocalDateTime getExpiryTime() {
    return expiryTime;
  }

  public void setExpiryTime(String expiryTime) {
    LocalDateTime currentTime = LocalDateTime.now(ZoneOffset.UTC);

    if (expiryTime != null) {
      try {
        LocalDateTime localDateTimeExpiryTime = LocalDateTime.parse(expiryTime);
        if (localDateTimeExpiryTime.isBefore(currentTime)) {
          throw new IllegalArgumentException("Expiry time must be a future date/time");
        }
        this.expiryTime = localDateTimeExpiryTime;
      } catch (DateTimeParseException e) {
        throw new IllegalArgumentException("Invalid expiry time format");
      }
    } else {
      this.expiryTime = currentTime.plusDays(defaultExpiryDays).truncatedTo(ChronoUnit.SECONDS);
    }
  }

  public JsonObject getConstraints() {
    return constraints;
  }

  public void setConstraints(JsonObject constraints) {
    this.constraints = constraints;
  }

  public String getFeedbackToConsumer() {
    return feedbackToConsumer;
  }

  public CreatePolicyRequest setFeedbackToConsumer(String feedbackToConsumer) {
    this.feedbackToConsumer = feedbackToConsumer;
    return this;
  }

  public String getProviderComment() {
    return providerComment;
  }

  public CreatePolicyRequest setProviderComment(String providerComment) {
    this.providerComment = providerComment;
    return this;
  }

  public JsonObject getAdditionalInfo() {
    return additionalInfo;
  }

  public CreatePolicyRequest setAdditionalInfo(JsonObject additionalInfo) {
    this.additionalInfo = additionalInfo;
    return this;
  }

  public String getItemOrganizationId() {
    return itemOrganizationId;
  }

  public CreatePolicyRequest setItemOrganizationId(String itemOrganizationId) {
    this.itemOrganizationId = itemOrganizationId;
    return this;
  }

  @Override
  public String toString() {
    return "CreatePolicyRequest{" +
        "userId='" + userId + '\'' +
        ", itemId=" + itemId +
        ", requestId=" + requestId +
        ", policyType=" + policyType +
        ", itemOrganizationId=" + itemOrganizationId +
        ", itemType=" + itemType +
        ", expiryTime=" + expiryTime +
        ", constraints=" + constraints +
        ", additionalInfo=" + additionalInfo +
        ", providerComment='" + providerComment + '\'' +
        ", feedbackToConsumer='" + feedbackToConsumer + '\'' +
        '}';
  }
}
