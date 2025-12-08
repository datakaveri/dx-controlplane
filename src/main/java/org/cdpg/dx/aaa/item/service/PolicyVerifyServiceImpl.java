package org.cdpg.dx.aaa.item.service;

import static org.cdpg.dx.acl.accessRequest.config.Constants.VERIFY_API_PATH;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.ACCESS;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.CONSTRAINTS;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.EMAIL;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.FIRST_NAME;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.LAST_NAME;
import static org.cdpg.dx.acl.accessRequest.dao.config.DbConstants.RESULT;
import static org.cdpg.dx.common.ResponseUrn.VERIFY_SUCCESS_URN;
import static org.cdpg.dx.database.elastic.util.Constants.AUTHORIZATION_KEY;
import static org.cdpg.dx.database.elastic.util.Constants.BEARER_KEY;
import static org.cdpg.dx.database.elastic.util.Constants.DETAIL;
import static org.cdpg.dx.database.elastic.util.Constants.HTTPS;
import static org.cdpg.dx.database.elastic.util.Constants.ID;
import static org.cdpg.dx.database.elastic.util.Constants.ITEM;
import static org.cdpg.dx.database.elastic.util.Constants.ITEM_ID;
import static org.cdpg.dx.database.elastic.util.Constants.ITEM_TYPE;
import static org.cdpg.dx.database.elastic.util.Constants.NAME;
import static org.cdpg.dx.database.elastic.util.Constants.OWNER;
import static org.cdpg.dx.database.elastic.util.Constants.TYPE;
import static org.cdpg.dx.database.elastic.util.Constants.USER;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.acl.policy.dao.model.VerifyPolicyDto;
import org.cdpg.dx.acl.policy.service.PolicyService;
import org.cdpg.dx.catalogueService.models.ItemType;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.model.DxUser;

public class PolicyVerifyServiceImpl implements PolicyVerifyService {
  private static final Logger LOGGER = LogManager.getLogger(PolicyVerifyServiceImpl.class);
  private final WebClient client;
  private final PolicyService policyService;
  private final String defaultApdUrl;

  public PolicyVerifyServiceImpl(PolicyService policyService, WebClient client,
                                 String defaultApdUrl) {
    this.policyService = policyService;
    this.client = client;
    this.defaultApdUrl = defaultApdUrl;
  }

  @Override
  public Future<VerifyPolicyDto> verify(String apdUrl, DxUser requester, DxUser owner,
                                        String itemId, ItemType itemType, String token) {
    if (apdUrl.equals(defaultApdUrl)) {
      // Internal default APD
      return policyService.initiateVerifyPolicy(owner.sub(), requester.email(),
              UUID.fromString(itemId), itemType, requester)
          .compose(policyDto -> {
            LOGGER.debug("Internal APD verify response: {}",
                policyDto.toJson().encodePrettily());
            String decision = policyDto.getType();
            if (VERIFY_SUCCESS_URN.getUrn().equalsIgnoreCase(decision)) {

              JsonObject constraints = policyDto.getConstraints();
              LOGGER.info("Policy verified successfully for user {}, constraints: {}",
                  requester.sub(), constraints.encode());
              return Future.succeededFuture(policyDto);
            } else {
              LOGGER.warn("Policy verify denied for user {} with decision {}", requester.sub(),
                  decision);
              return Future.failedFuture(
                  new DxForbiddenException("Access denied by policy verification"));
            }
          })
          .recover(failure -> {
            LOGGER.error("Error during default APD policy verify: {}", failure.getMessage());
            String raw = failure.getMessage();
            String detail = raw;   // fallback

            try {
              // Try to parse the message as JSON and extract "detail"
              JsonObject json = new JsonObject(raw);
              detail = json.getString(DETAIL, raw);
            } catch (Exception ignored) {
              // Not JSON → fallback to original message
            }
            return Future.failedFuture(new DxForbiddenException(detail));
          });
    } else {
      // External APD
      JsonObject payload = new JsonObject()
          .put(USER, buildUserBlock(requester))
          .put(OWNER, buildUserBlock(owner))
          .put(ITEM, new JsonObject()
              .put(ITEM_ID, itemId)
              .put(ITEM_TYPE, itemType.name()));
      LOGGER.debug("verify payload: {}", payload);

      return client
          .postAbs(HTTPS + apdUrl + VERIFY_API_PATH)
          .putHeader(AUTHORIZATION_KEY, BEARER_KEY + " " + token)
          .sendJsonObject(payload)
          .compose(httpResponse -> {

            int status = httpResponse.statusCode();

            if (status == 200) {
              JsonObject body = httpResponse.bodyAsJsonObject();
              JsonObject result = body.getJsonObject(RESULT);

              String decision = result.getString(TYPE);

              if (VERIFY_SUCCESS_URN.getUrn().equalsIgnoreCase(decision)) {
                JsonArray constr = result.getJsonObject(CONSTRAINTS).getJsonArray(ACCESS);
                LOGGER.info("APD allowed access for user {} with constraints {}", requester.sub(),
                    constr.encode());
                return Future.succeededFuture(new VerifyPolicyDto(result));
              }

              // Policy evaluated but denied
              LOGGER.warn("APD denied access: decision={}", decision);
              return Future.failedFuture(new DxForbiddenException("Access denied by APD decision"));
            }

            // Non-200 status handling
            LOGGER.error("APD verify failed: HTTP {} body={}", status, httpResponse.bodyAsString());

            return switch (status) {
              case 400 ->
                  Future.failedFuture(new DxForbiddenException("Invalid APD request (400)"));
              case 401 ->
                  Future.failedFuture(new DxForbiddenException("Unauthorized for APD (401)"));
              case 403 -> Future.failedFuture(new DxForbiddenException("Forbidden by APD (403)"));
              case 404 ->
                  Future.failedFuture(new DxForbiddenException("APD endpoint not found (404)"));
              case 500, 503 ->
                  Future.failedFuture(new DxForbiddenException("APD service unavailable"));
              default -> Future.failedFuture(
                  new DxForbiddenException("Unexpected APD error: HTTP " + status));
            };
          })
          .recover(failure -> {

            LOGGER.error("APD verification error: {}", failure.toString());

            if (failure instanceof TimeoutException) {
              return Future.failedFuture(
                  new DxForbiddenException("APD request timed out"));
            }
            if (failure.getCause() instanceof java.net.ConnectException) {
              return Future.failedFuture(new DxForbiddenException("APD connection refused"));
            }
            if (failure.getCause() instanceof java.net.UnknownHostException) {
              return Future.failedFuture(new DxForbiddenException("APD host unreachable"));
            }

            // default fallback
            return Future.failedFuture(new DxForbiddenException("APD verification failed: " +
                failure.getMessage()));
          });

    }
  }

  // Helper to convert DxUser → APD user block
  private JsonObject buildUserBlock(DxUser user) {
    return new JsonObject()
        .put(ID, user.sub().toString())
        .put(NAME, new JsonObject()
            .put(FIRST_NAME, user.givenName())
            .put(LAST_NAME, user.familyName()))
        .put(EMAIL, user.email());
  }
}