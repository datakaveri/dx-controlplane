package org.cdpg.dx.auditing.v2.Constant;

import static org.cdpg.dx.auditing.v2.Constant.UserActivityAuditSchema.*;

import java.util.Map;
import java.util.Set;

public final class ActivityApiParamConstants {

  // -------------------------------------------------
  // Admin filters (many entries → Map.ofEntries)
  // -------------------------------------------------
  public static final Map<String, String> ALLOWED_FILTER_MAP_FOR_ADMIN_V2 =
      Map.ofEntries(
          Map.entry("assetProviderId", ASSET_PROVIDER_ID),
          Map.entry("assetType", ASSET_TYPE),
          Map.entry("assetId", ASSET_ID),
          Map.entry("assetOrgType", ASSET_ORG_TYPE),
          Map.entry("accessPolicy", ASSET_ACCESS_POLICY),
          Map.entry("action", ACTION),
          Map.entry("logType", LOG_TYPE),
          Map.entry("sandboxType", SANDBOX_TYPE),
          Map.entry("userId", USER_ID),
          Map.entry("delegateId", DELEGATE_ID),
          Map.entry("actorType", ACTOR_TYPE));
  // -------------------------------------------------
  // Sort fields
  // -------------------------------------------------
  public static final Set<String> ALLOWED_SORT_FIELDS_V2 =
      Set.of(
          "createdAt",
          "userName",
          "assetType",
          "orgName",
          "assetName",
          "assetShortDescription",
          "action",
          "amount",
          "sandboxType");
  // -------------------------------------------------
  // Consumer filters (many entries → Map.ofEntries)
  // -------------------------------------------------
  public static final Map<String, String> ALLOWED_FILTER_MAP_FOR_CONSUMER_V2 =
      Map.ofEntries(
          Map.entry("orgId", ORG_ID),
          Map.entry("assetProviderId", ASSET_PROVIDER_ID),
          Map.entry("assetType", ASSET_TYPE),
          Map.entry("assetId", ASSET_ID),
          Map.entry("assetOrgType", ASSET_ORG_TYPE), // ✅ fixed
          Map.entry("accessPolicy", ASSET_ACCESS_POLICY),
          Map.entry("action", ACTION),
          Map.entry("logType", LOG_TYPE),
          Map.entry("sandboxType", SANDBOX_TYPE),
          Map.entry("delegateId", DELEGATE_ID),
          Map.entry("actorType", ACTOR_TYPE));
  // -------------------------------------------------
  // API → DB field mapping (filters + sorts)
  // -------------------------------------------------
  public static final Map<String, String> API_TO_DB_FIELD_MAP_V2 =
      Map.ofEntries(
          // ---- Common ----
          Map.entry("createdAt", CREATED_AT),
          Map.entry("action", ACTION),
          Map.entry("logType", LOG_TYPE),

          // ---- User / Org ----
          Map.entry("userId", USER_ID),
          Map.entry("userName", USER_NAME),
          Map.entry("orgId", ORG_ID),
          Map.entry("orgName", ORG_NAME),

          // ---- Asset ----
          Map.entry("assetId", ASSET_ID),
          Map.entry("assetName", ASSET_NAME),
          Map.entry("assetShortDescription", ASSET_SHORT_DESCRIPTION),
          Map.entry("assetType", ASSET_TYPE),
          Map.entry("accessPolicy", ASSET_ACCESS_POLICY),
          Map.entry("assetOrgType", ASSET_ORG_TYPE),
          Map.entry("assetProviderId", ASSET_PROVIDER_ID),
          Map.entry("sandboxType", SANDBOX_TYPE),
          Map.entry("delegateId", DELEGATE_ID),
          Map.entry("actorType", ACTOR_TYPE));

  private ActivityApiParamConstants() {}
}
