package org.cdpg.dx.aaa.item.service;

import static org.cdpg.dx.database.elastic.util.Constants.AUTHORIZATION_KEY;
import static org.cdpg.dx.database.elastic.util.Constants.BEARER_KEY;
import static org.cdpg.dx.database.elastic.util.Constants.HTTPS;
import static org.cdpg.dx.database.elastic.util.Constants.ITEM;
import static org.cdpg.dx.database.elastic.util.Constants.ITEM_ID;
import static org.cdpg.dx.database.elastic.util.Constants.ITEM_TYPE;
import static org.cdpg.dx.database.elastic.util.Constants.OWNER;
import static org.cdpg.dx.database.elastic.util.Constants.USER;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.acl.policy.service.PolicyService;
import org.cdpg.dx.catalogueService.models.ItemType;
import org.cdpg.dx.common.ResponseUrn;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.model.DxUser;

public class PolicyVerifyServiceImpl implements PolicyVerifyService {
  private static final Logger LOGGER = LogManager.getLogger(PolicyVerifyServiceImpl.class);
  private final WebClient client;
  private final PolicyService policyService;
  private final String defaultApdUrl;

  public PolicyVerifyServiceImpl(PolicyService policyService, WebClient client, String defaultApdUrl) {
    this.policyService = policyService;
    this.client = client;
    this.defaultApdUrl = defaultApdUrl;
  }

  @Override
  public Future<JsonObject> verify(String apdUrl, DxUser requester, DxUser owner,
                                   String itemId, ItemType itemType, String token) {
    if (apdUrl.equals(defaultApdUrl)) {
      // Internal default APD
      return policyService.initiateVerifyPolicy(owner.sub(), requester.email(), UUID.fromString(itemId), itemType, requester)
          .compose(policyResult -> {
            LOGGER.debug("Internal APD verify response: {}",
                policyResult.toJson().encodePrettily());
            String decision = policyResult.getType();
            if (ResponseUrn.VERIFY_SUCCESS_URN.getUrn().equalsIgnoreCase(decision)) {

              JsonObject apdConstraints = policyResult.getApdConstraints();
              LOGGER.info("Policy verified successfully for user {}, constraints: {}",
                  requester.sub(), apdConstraints.encode());
              return Future.succeededFuture(apdConstraints);
            } else {
              LOGGER.warn("Policy verify denied for user {} with decision {}", requester.sub(), decision);
              return Future.failedFuture(new DxForbiddenException("Access denied by policy verification"));
            }
          })
          .recover(failure -> {
            LOGGER.error("Error during default APD policy verify: {}", failure.getMessage());
            return Future.failedFuture(failure);
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

      return client.postAbs(HTTPS + apdUrl + "/iudx/acl/apd/v2/verify")
          .putHeader(AUTHORIZATION_KEY, BEARER_KEY + " " + token)
          .sendJsonObject(payload)
          .compose(httpResponse -> {
            if (httpResponse.statusCode() == 200) {
              JsonObject body = httpResponse.bodyAsJsonObject();
              JsonObject result = body.getJsonObject("result");
              String decision = result.getString("type");

              if ("urn:apd:Allow".equalsIgnoreCase(decision)) {
                JsonArray accessConstraints = result
                    .getJsonObject("apdConstraints")
                    .getJsonArray("access");

                LOGGER.info("APD allowed access for user {} with constraints {}",
                    requester.sub(), accessConstraints.encode());
                return Future.succeededFuture(result.getJsonObject("apdConstraints", new JsonObject()));
              } else {
                LOGGER.warn("APD denied access for user {} with decision {}",
                    requester.sub(), decision);
                return Future.failedFuture(new DxForbiddenException("Access denied by APD"));
              }
            } else {
              LOGGER.error("APD verify call failed: status {}, body {}",
                  httpResponse.statusCode(), httpResponse.bodyAsString());
              return Future.failedFuture(new DxForbiddenException("APD verification failed"));
            }
          })
          .recover(failure -> {
            LOGGER.error("Error during APD verification: {}", failure.getMessage());
            return Future.failedFuture(new DxForbiddenException("APD verification failed"));
          });
    }
  }

  // Helper to convert DxUser → APD user block
  private JsonObject buildUserBlock(DxUser user) {
    return new JsonObject()
        .put("id", user.sub().toString())
        .put("name", new JsonObject()
            .put("firstName", user.givenName())
            .put("lastName", user.familyName()))
        .put("email", user.email());
  }
}