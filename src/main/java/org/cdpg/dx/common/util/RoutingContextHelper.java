package org.cdpg.dx.common.util;

import static org.cdpg.dx.aaa.apiserver.config.ApiConstants.*;
import static org.cdpg.dx.common.ResponseUrn.INVALID_TOKEN_URN;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.auditing.model.ActivityAuditLogBuilder;
import org.cdpg.dx.auditing.model.AuditLog;
import org.cdpg.dx.auditing.v2.model.UserActivityAuditLogBuilder;
import org.cdpg.dx.auth.authentication.exception.AuthenticationException;
import org.cdpg.dx.common.HttpStatusCode;
import org.cdpg.dx.common.exception.BaseDxException;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.model.DxUser;

public class RoutingContextHelper {
  private static final Logger LOGGER = LogManager.getLogger(RoutingContextHelper.class);
  //  private static final String JWT_DATA = "jwtData";
  private static final String RESPONSE_SIZE = "responseSize";
  private static final String AUDITING_LOG = "auditingLog";
  private static final String POLICY_EXPIRY_AT = "policyExpiryAt";
  private static final String ITEM_META_DATA = "itemMetaData";
  private static final String PROVIDER_ID = "providerId";
  private static final String IID = "iid";

  public static void setUser(RoutingContext routingContext, User user) {
    routingContext.put(USER, user);
  }

  public static User getUser(RoutingContext routingContext) {
    return routingContext.get(USER);
  }

  public static JsonObject getAuthInfo(RoutingContext routingContext) {
    return new JsonObject()
        .put(API_ENDPOINT, getRequestPath(routingContext))
        .put(HEADER_TOKEN, getToken(routingContext))
        .put(API_METHOD, getMethod(routingContext));
  }

  public static String getPolicyExpiryAt(RoutingContext routingContext) {
    return routingContext.get(POLICY_EXPIRY_AT);
  }

  public static void setPolicyExpiryAt(RoutingContext routingContext, String policyExpiryAt) {
    routingContext.put(POLICY_EXPIRY_AT, policyExpiryAt);
  }

  public static String getProviderId(RoutingContext routingContext) {
    return routingContext.get(PROVIDER_ID);
  }

  public static void setProviderId(RoutingContext routingContext, String providerId) {
    routingContext.put(PROVIDER_ID, providerId);
  }

  public static String getIid(RoutingContext routingContext) {
    return routingContext.get(IID);
  }

  public static void setIid(RoutingContext routingContext, String iid) {
    routingContext.put(IID, iid);
  }

  public static void setItemMetaData(RoutingContext context, JsonObject result) {
    context.put(ITEM_META_DATA, result);
  }

  public static JsonObject getItemMetaData(RoutingContext event) {
    return event.get(ITEM_META_DATA);
  }

  public static String getToken(RoutingContext routingContext) {
    /* token would can be of the type : Bearer <JWT-Token> */
    /* Send Bearer <JWT-Token> if Authorization header is present */
    String token = routingContext.request().headers().get(AUTHORIZATION_KEY);
    boolean isValidBearerToken = token != null && token.trim().split(" ").length == 2;
    boolean isBearerAuthHeaderPresent =
        isValidBearerToken && (token.contains(HEADER_BEARER_AUTHORIZATION));
    String[] tokenWithoutBearer = new String[] {};
    if (isValidBearerToken) {
      if (isBearerAuthHeaderPresent) {
        tokenWithoutBearer = (token.split(HEADER_BEARER_AUTHORIZATION));
      }
      token = tokenWithoutBearer[1].replaceAll("\\s", "");
      return token;
    }
    throw new AuthenticationException(INVALID_TOKEN_URN.getMessage());
  }

  public static JsonObject getVerifyAuthInfo(RoutingContext routingContext) {
    return new JsonObject()
        .put(API_ENDPOINT, getRequestPath(routingContext))
        .put(HEADER_TOKEN, getVerifyToken(routingContext))
        .put(API_METHOD, getMethod(routingContext));
  }

  private static String getVerifyToken(RoutingContext routingContext) {
    String token = routingContext.request().headers().get(AUTHORIZATION_KEY);
    if (token.trim().split(" ").length == 2) {
      token = token.trim().split(" ")[1];
      return token;
    } else {
      throw new BaseDxException(
          HttpStatusCode.getByValue(401).getValue(), INVALID_TOKEN_URN.getMessage());
    }
  }

  public static String getMethod(RoutingContext routingContext) {
    return routingContext.request().method().toString();
  }

  public static String getRequestPath(RoutingContext routingContext) {
    return routingContext.request().path();
  }

  public static String getId(RoutingContext event) {
    return event.get(ID);
  }

  public static void setId(RoutingContext event, String id) {
    event.put(ID, id);
  }

  public static Long getResponseSize(RoutingContext event) {
    return (Long) event.data().get(RESPONSE_SIZE);
  }

  public static void setResponseSize(RoutingContext event, long responseSize) {
    event.data().put(RESPONSE_SIZE, responseSize);
  }

  public static Optional<List<AuditLog>> getAuditingLog(RoutingContext routingContext) {
    return Optional.ofNullable(routingContext.get(AUDITING_LOG));
  }

  public static Optional<List<ActivityAuditLogBuilder>> getAuditingLogNew(
      RoutingContext routingContext) {
    return Optional.ofNullable(routingContext.get(AUDITING_LOG));
  }

  public static void setAuditingLog(RoutingContext routingContext, AuditLog auditingLog) {
    List<AuditLog> logs = getAuditingLog(routingContext).orElseGet(ArrayList::new);
    logs.add(auditingLog);
    routingContext.put(AUDITING_LOG, logs);
  }

  public static void setAuditingLogNew(
      RoutingContext routingContext, ActivityAuditLogBuilder auditingLog) {
    List<ActivityAuditLogBuilder> logs =
        getAuditingLogNew(routingContext).orElseGet(ArrayList::new);
    logs.add(auditingLog);
    routingContext.put(AUDITING_LOG, logs);
  }

  // -------------------------------------------------
  //    * Auditing V2
  //    * -------------------------------------------------
  public static Optional<List<UserActivityAuditLogBuilder>> getAuditingLogV2(
      RoutingContext routingContext) {
    return Optional.ofNullable(routingContext.get(AUDITING_LOG));
  }

  public static void setAuditingLogV2(
      RoutingContext routingContext, UserActivityAuditLogBuilder auditingLog) {
    List<UserActivityAuditLogBuilder> logs =
        getAuditingLogV2(routingContext).orElseGet(ArrayList::new);
    logs.add(auditingLog);
    routingContext.put(AUDITING_LOG, logs);
  }

  public static DxUser fromPrincipal(RoutingContext ctx) {
    JsonObject principal = ctx.user().principal();

    // Handle nested JSON objects like realm_access
    List<String> roles =
        principal
            .getJsonObject("realm_access", new JsonObject())
            .getJsonArray("roles", new io.vertx.core.json.JsonArray())
            .getList();

    UUID userId;
    try {
      userId = UUID.fromString(principal.getString("sub"));
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new DxBadRequestException("Invalid or missing 'sub' UUID in token");
    }

    return new DxUser(
        roles,
        principal.getString("organisation_id", null),
        principal.getString("organisation_name", null), // assuming this is correct and intentional
        userId,
        principal.getBoolean("email_verified", false),
        principal.getBoolean("kyc_verified", false),
        principal.getString("name"),
        principal.getString("preferred_username"),
        principal.getString("given_name"),
        principal.getString("family_name"),
        principal.getString("email"),
        new ArrayList<>(),
        new JsonObject(),
        null,
        new JsonObject(),
        "",
        "",
        "",
        null,
        principal.getString("did", null),
        principal.getString("aud", null),
        new JsonArray());
  }
}
