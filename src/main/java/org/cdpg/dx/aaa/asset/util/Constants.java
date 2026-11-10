package org.cdpg.dx.aaa.asset.util;

import java.util.Map;


public class Constants {
  public static final String ASSET_REQUEST_TABLE="asset_request";
  public static final String ASSET_REQUEST_ID = "id";
  public static final String USER_ID = "user_id";
  public static final String ASSET_ID = "asset_id";
  public static final String TYPE = "type";
  public static final String STATUS = "status";
  public static final String REQUESTED_AT = "requested_at";
  public static final String ADDITONAL_INFO = "additional_info";
  public static final String UPDATED_AT = "updated_at";
  public static final String ASSET = "asset";


  public static final Map<String, String> ALLOWED_FILTER_MAP_FOR_ASSET_REQUEST = Map.of(
    "status", STATUS,
    "userId" , USER_ID,
    "assetId", ASSET_ID,
    "requestedAt",REQUESTED_AT ,
    "updatedAt", UPDATED_AT
  );

  public static final Map<String, String> API_TO_DB_ASSET_REQUEST = Map.of(
    "additionalInfo", ADDITONAL_INFO,
    "type", TYPE,
    "status", STATUS,
    "userId" , USER_ID,
    "assetId", ASSET_ID,
    "requestedAt",REQUESTED_AT ,
    "updatedAt", UPDATED_AT
  );


}
