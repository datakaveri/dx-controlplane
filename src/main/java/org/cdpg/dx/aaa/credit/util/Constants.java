package org.cdpg.dx.aaa.credit.util;

import java.util.List;
import java.util.Map;

public class Constants {

  // CREDIT_REQUEST_TABLE
  public static final String CREDIT_REQUEST_TABLE="credit_requests";
  public static final String USER_ID = "user_id";
  public static final String CREDIT_REQUEST_ID = "id";
  public static final String AMOUNT = "amount";
  public static final String STATUS = "status";
  public static final String REQUESTED_AT = "requested_at";
  public static final String PROCESSED_AT = "processed_at";
  public static final String USER_NAME = "user_name";


  // USER CREDIT TABLE
  public static final String USER_CREDIT_TABLE="user_credits";
  public static final String USER_CREDIT_ID = "id";
  public static final String BALANCE ="balance";
  public static final String UPDATED_AT ="updated_at";

  // CREDIT DEDUCTION TABLE
  public static final String CREDIT_TRANSACTION_TABLE="credit_transactions";
  public static final String CREDIT_TRANSACTION_ID = "id";
  public static final String TRANSACTED_BY = "transacted_by";
  public static final String CREATED_AT = "created_at";
  public static final String TRANSACTION_STATUS = "transaction_status";
  public static final String TRANSACTION_TYPE = "transaction_type";


  public static final String COMPUTE_ROLE_TABLE="compute_role";
  public static final String COMPUTE_ROLE_ID="id";
  public static final String APPROVED_BY="approved_by";
  public static final String UPDATED_BALANCE="updated_balance";

  public static final String ADDITONAL_INFO= "additional_info";

  public static final Map<String, String> ALLOWED_FILTER_MAP_FOR_CREDIT_REQUEST = Map.of(
          "userName", USER_NAME,
          "status", STATUS,
          "processedAt", PROCESSED_AT,
          "requestedAt", REQUESTED_AT
  );

  public static final Map<String, String> API_TO_DB_CREDIT_REQUEST = Map.of(
          "userName", USER_NAME,
          "additionalInfo", ADDITONAL_INFO,
          "userId", USER_ID,
          "status", STATUS,
          "processedAt", PROCESSED_AT,
          "requestedAt", REQUESTED_AT
  );


  public static final Map<String, String> ALLOWED_FILTER_MAP_FOR_COMPUTE_ROLE = Map.of(
          "userName", USER_NAME,
          "status", STATUS,
          "approvedBy", "approved_by",
          "createdAt", CREATED_AT,
          "requested_at", UPDATED_AT
  );

}
