package org.cdpg.dx.aaa.accessRequest.util;

import static org.cdpg.dx.aaa.accessRequest.dao.config.DbConstants.*;

import java.util.Map;

public class Constants {

  public static final Map<String, String> API_TO_DB_MAP =
      Map.ofEntries(
          Map.entry("requestId", DB_REQUEST_ID),
          Map.entry("requestStatus", DB_STATUS),
          Map.entry("requestType", DB_REQUEST_TYPE),
          Map.entry("additionalInfo", DB_ADDITIONAL_INFO),
          Map.entry("providerId", DB_PROVIDER_ID),
          Map.entry("consumerId", DB_CONSUMER_ID),
          Map.entry("itemId", DB_ITEM_ID),
          Map.entry("assetName", DB_ASSET_NAME),
          Map.entry("assetType", DB_ASSET_TYPE),
          Map.entry("consumerOrganization", DB_CONSUMER_ORGANIZATION),
          Map.entry("expiryAt", DB_EXPIRY_AT),
          Map.entry("createdAt", DB_CREATED_AT),
          Map.entry("updatedAt", DB_UPDATED_AT),
          Map.entry("consumerEmail", DB_CONSUMER_EMAIL),
          Map.entry("consumerFirstName", DB_CONSUMER_FIRST_NAME),
          Map.entry("consumerLastName", DB_CONSUMER_LAST_NAME),
          Map.entry("providerOrganization", DB_ASSET_ORGANIZATION_ID),
          Map.entry("shortDescription", DB_SHORT_DESCRIPTION));
}
