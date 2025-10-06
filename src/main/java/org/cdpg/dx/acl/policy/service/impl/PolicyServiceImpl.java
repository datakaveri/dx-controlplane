package org.cdpg.dx.acl.policy.service.impl;

import static org.cdpg.dx.aaa.common.Constants.DETAIL;
import static org.cdpg.dx.aaa.common.Constants.ID;
import static org.cdpg.dx.aaa.common.Constants.ITEM_TYPE_RESOURCE_GROUP;
import static org.cdpg.dx.aaa.common.Constants.TITLE;
import static org.cdpg.dx.aaa.common.Constants.TYPE;
import static org.cdpg.dx.acl.accessRequest.config.Constants.STATUS_CODE;
import static org.cdpg.dx.auth.authorization.model.DxRole.CONSUMER;
import static org.cdpg.dx.auth.authorization.model.DxRole.CONSUMER_DELEGATE;
import static org.cdpg.dx.auth.authorization.model.DxRole.PROVIDER;
import static org.cdpg.dx.auth.authorization.model.DxRole.PROVIDER_DELEGATE;
import static org.cdpg.dx.common.HttpStatusCode.BAD_REQUEST;
import static org.cdpg.dx.common.HttpStatusCode.FORBIDDEN;
import static org.cdpg.dx.common.HttpStatusCode.SUCCESS;
import static org.cdpg.dx.common.ResponseUrn.BAD_REQUEST_URN;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.common.ItemType;
import org.cdpg.dx.acl.policy.dao.PolicyDao;
import org.cdpg.dx.acl.policy.dao.model.PolicyDto;
import org.cdpg.dx.acl.policy.service.PolicyService;
import org.cdpg.dx.acl.policy.service.model.CreatePolicyRequest;
import org.cdpg.dx.common.HttpStatusCode;
import org.cdpg.dx.common.ResponseUrn;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.database.postgres.models.QueryResult;

public class PolicyServiceImpl implements PolicyService {
  private static final Logger LOGGER = LogManager.getLogger(PolicyServiceImpl.class);
  private static final String FAILURE_MESSAGE = "Policy could not be deleted";
  private final PolicyDao policyDao;
  JsonObject config;


  public PolicyServiceImpl(PolicyDao policyDao, JsonObject config) {
    this.policyDao = policyDao;
    this.config = config;
  }


  @Override
  public Future<Void> createPolicy(List<CreatePolicyRequest> requests, DxUser caller) {
    // ownership checks -- caller must be owner or org delegate; simplified here
    UUID userId = caller.sub();

    Set<UUID> itemIds = requests.stream()
        .map(CreatePolicyRequest::getItemId).collect(Collectors.toSet());
    Set<String> itemTypes =
        requests.stream().map(req -> req.getItemType().getTypeValue()).collect(Collectors.toSet());

    // Fail fast if resource_group
    if (itemTypes.contains(ITEM_TYPE_RESOURCE_GROUP)) {
      LOGGER.debug("Contains resource group");
      return Future.failedFuture(
          generateErrorResponse(BAD_REQUEST, "Policy creation for resource group is restricted"));
    }

    LOGGER.debug("itemIds: " + itemIds);
    LOGGER.debug("itemTypes: " + itemTypes);
    // Step 1: Ownership check (DAO: checkForItemsInDb)
    return policyDao.checkForItemsInDb(itemIds, itemTypes, caller)
        .compose(providerIds -> {
          LOGGER.debug("no.of providerIds: " + providerIds.size());
          if (providerIds.size() == 1 && providerIds.contains(userId)) {
            // Step 2: Ensure no duplicate policy
            return policyDao.checkExistingPoliciesForId(requests, userId);
          } else {
            return Future.failedFuture(
                generateErrorResponse(FORBIDDEN, "Access Denied: Not owner of resource"));
          }
        })
        .compose(policyNotExists -> {
          // Step 3: Create policy in DB
          LOGGER.debug("came here... {}", policyNotExists.toString());
          return policyDao.insertPolicies(requests, userId);
        })
        .onSuccess(rowList -> {
          JsonArray response = createResponseArray(rowList);
          LOGGER.debug("Policy is created with info {}", response);
        }).mapEmpty();
  }

  @Override
  public Future<List<PolicyDto>> getPolicy(DxUser user) {
    List<String> role = user.roles();
    Future<QueryResult> daoFuture;

    //TODO: Fetch rsurls from user token if needed as an extension
//    List<Object> resourceServerUrls = new ArrayList<>();
//    resourceServerUrls.add("rs.forestdx.iudx.io");
//    resourceServerUrls.add("file.forestdx.iudx.io");
    if (role.contains(PROVIDER.getRole()) || role.contains(PROVIDER_DELEGATE.getRole())) {
      daoFuture =
          policyDao.getPoliciesByProvider(user.sub().toString());
    } else if (role.contains(CONSUMER.getRole()) || role.contains(CONSUMER_DELEGATE.getRole())) {
      daoFuture =
          policyDao.getPoliciesByConsumer(user.email());
    } else {
      JsonObject error = new JsonObject()
          .put(TYPE, HttpStatusCode.BAD_REQUEST.getValue())
          .put(TITLE, ResponseUrn.BAD_REQUEST_URN.getUrn())
          .put(DETAIL, "Invalid role");
      return Future.failedFuture(error.encode());
    }

    return daoFuture.map(queryResult -> {
      JsonArray rows = queryResult.getRows();

      if (rows.isEmpty()) {
        JsonObject error = new JsonObject()
            .put(TYPE, HttpStatusCode.NOT_FOUND.getValue())
            .put(TITLE, ResponseUrn.RESOURCE_NOT_FOUND_URN.getUrn())
            .put(DETAIL, "Policy not found");
        throw new RuntimeException(error.encode());
      }

      return rows.stream()
          .map(obj -> {
            JsonObject row = (JsonObject) obj;

            // Log each row from DB
            LOGGER.info("Policy Row: {}", row.encodePrettily());

            // Enrich row before passing to DTO
            if (role.contains(PROVIDER.getRole()) || role.contains(PROVIDER_DELEGATE.getRole())) {
              row.mergeIn(getProviderInfo(row));
            } else {
              row.mergeIn(getConsumerInfo(row));
            }

            return new PolicyDto(row);
          })
          .collect(Collectors.toList());
    });
  }

  private JsonObject getConsumerInfo(JsonObject row) {
    return new JsonObject().put("consumer", new JsonObject()
        .put("id", row.getString("consumerId"))
        .put("email", row.getString("consumerEmailId"))
        .put("name", new JsonObject()
            .put("firstName", row.getString("consumerFirstName"))
            .put("lastName", row.getString("consumerLastName"))
        )
    );
  }

  private JsonObject getProviderInfo(JsonObject row) {
    return new JsonObject().put("provider", new JsonObject()
        .put("id", row.getString("ownerId"))
        .put("email", row.getString("ownerEmailId"))
        .put("name", new JsonObject()
            .put("firstName", row.getString("ownerFirstName"))
            .put("lastName", row.getString("ownerLastName"))
        )
    );
  }

  @Override
  public Future<Void> deletePolicy(JsonObject policy, DxUser user) {
    UUID policyId = UUID.fromString(policy.getString(ID));

    return policyDao.verifyPolicy(policyId).compose(result -> {
      if (result.getRows().isEmpty()) {
        return Future.failedFuture(
            new JsonObject()
                .put(TYPE, HttpStatusCode.NOT_FOUND.getValue())
                .put(TITLE, ResponseUrn.RESOURCE_NOT_FOUND_URN.getUrn())
                .put(DETAIL, FAILURE_MESSAGE + ", as it doesn't exist")
                .encode());
      }
      JsonObject row = result.getRows().getJsonObject(0);
      LOGGER.debug("Row: {}", row);
      String ownerId = row.getString("owner_id");

      JsonArray rsJsonArray = row.getJsonArray("resource_server_urls");

      List<String> rsUrl = rsJsonArray.stream()
          .map(Object::toString)
          .collect(Collectors.toList());

      LOGGER.debug("rsUrl: {}", rsUrl);

      String status = row.getString("status");

      /* does the policy belong to the owner who is requesting */
      if (ownerId.equals(user.sub().toString())) {
        /* is policy in ACTIVE status */
        if (!"ACTIVE".equalsIgnoreCase(status)) {
          LOGGER.error("Failure : policy is not active");
          return Future.failedFuture(
              getFailureResponse(
                  new JsonObject(), FAILURE_MESSAGE + ", as policy is not ACTIVE"));
        }
      } else {
        LOGGER.error("Failure : policy does not belong to the user");
        return Future.failedFuture(
            new JsonObject()
                .put("type", HttpStatusCode.FORBIDDEN.getValue())
                .put("title", ResponseUrn.FORBIDDEN_URN.getUrn())
                .put(DETAIL, FAILURE_MESSAGE + ", as policy doesn't belong to the user")
                .encode());
      }

      // Passed all checks → proceed to delete
      Promise<Void> promise = Promise.promise();
      policyDao.deletePolicy(policyId)
          .onFailure(err -> {
            LOGGER.debug("query failed: {}", err.getLocalizedMessage());
            promise.fail(
                getFailureResponse(new JsonObject(), FAILURE_MESSAGE + ", update query failed"));
          })
          .onSuccess(delResult -> {
            if (delResult.getRows().isEmpty()) {
              promise.fail(
                  getFailureResponse(
                      new JsonObject(), FAILURE_MESSAGE + " , as policy is expired"));
            } else {
              LOGGER.info("query succeeded");
              JsonObject responseJson = delResult.getRows().getJsonObject(0);
              LOGGER.debug("Delete policy succeeded: {}", responseJson);
              promise.complete();
            }
          });
      return promise.future();
    });
  }

  /**
   * to verify if a policy exists for a user/item and is ACTIVE
   */
  public Future<JsonObject> initiateVerifyPolicy(UUID ownerId, String userEmail, UUID itemId,
                                                 ItemType itemType, DxUser user) {
    Promise<JsonObject> promise = Promise.promise();

    try {
      // Step 1: Validate the item exists and is accessible
      Set<UUID> itemSet = new HashSet<>();
      itemSet.add(itemId);

      policyDao.checkForItemsInDb(itemSet, Set.of(itemType.getTypeValue()), user)
          .compose(existingItemIds -> {
            if (existingItemIds.isEmpty()) {
              return Future.failedFuture(generateErrorResponse(
                  HttpStatusCode.FORBIDDEN,
                  "Item not found or access denied"
              ));
            }

            // Step 2: Check if ACTIVE policy exists for user/item
            return policyDao.checkExistingPoliciesForId(itemId, ownerId, userEmail);
          })
          .onSuccess(rsPolicy -> {
            // Policy exists → fetch constraints
            if (rsPolicy.containsKey(ID)) {
              // Optionally, fetch policy constraints using verifyPolicy(UUID)
              policyDao.verifyPolicy(UUID.fromString(rsPolicy.getString(ID)))
                  .onSuccess(result -> {
                    JsonObject responseJson = new JsonObject()
                        .put("type", ResponseUrn.VERIFY_SUCCESS_URN.getUrn())
                        .put("apdConstraints", rsPolicy.getJsonObject("constraints"));
                    promise.complete(responseJson);
                  })
                  .onFailure(promise::fail);
            } else {
              promise.fail(generateErrorResponse(HttpStatusCode.FORBIDDEN,
                  "No ACTIVE policy exists for this user/item"));
            }
          })
          .onFailure(promise::fail);

    } catch (Exception e) {
      LOGGER.error("Error in verifyPolicy: {}", e.getMessage());
      promise.fail(generateErrorResponse(HttpStatusCode.INTERNAL_SERVER_ERROR,
          "Failed to verify policy"));
    }

    return promise.future();
  }

  private String generateErrorResponse(HttpStatusCode httpStatusCode, String errorMessage) {
    return new JsonObject()
        .put(TYPE, httpStatusCode.getValue())
        .put(TITLE, httpStatusCode.getPath())
        .put(DETAIL, errorMessage)
        .encode();
  }

  private JsonArray createResponseArray(List<QueryResult> queryResults) {
    JsonArray response = new JsonArray();
    final JsonObject[] ownerJsonObject = {null};

    for (QueryResult queryResult : queryResults) {
      for (int i = 0; i < queryResult.getRows().size(); i++) {
        JsonObject row = queryResult.getRows().getJsonObject(i);

        JsonObject jsonObject = new JsonObject()
            .put("policyId", row.getString("_id"))
            .put("userEmailId", row.getString("user_emailid"))
            .put("itemId", row.getString("item_id"))
            .put("expiryAt", row.getString("expiry_at"));

        if (ownerJsonObject[0] == null) {
          ownerJsonObject[0] = new JsonObject()
              .put("ownerId", row.getValue("owner_id").toString());
        }
        response.add(jsonObject);
      }
    }

    response.add(ownerJsonObject[0]);
    return response;
  }

  private String getFailureResponse(JsonObject response, String detail) {
    return response
        .put(TYPE, BAD_REQUEST.getValue())
        .put(TITLE, BAD_REQUEST.getPath())
        .put(DETAIL, detail)
        .encode();
  }
}
