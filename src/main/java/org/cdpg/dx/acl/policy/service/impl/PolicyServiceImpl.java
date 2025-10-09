package org.cdpg.dx.acl.policy.service.impl;

import static org.cdpg.dx.aaa.common.Constants.DETAIL;
import static org.cdpg.dx.aaa.common.Constants.ID;
import static org.cdpg.dx.aaa.common.Constants.ITEM_TYPE_AI_MODEL;
import static org.cdpg.dx.aaa.common.Constants.ITEM_TYPE_DATA_BANK;
import static org.cdpg.dx.aaa.common.Constants.ITEM_TYPE_RESOURCE_GROUP;
import static org.cdpg.dx.aaa.common.Constants.PROVIDER_USER_ID;
import static org.cdpg.dx.aaa.common.Constants.TITLE;
import static org.cdpg.dx.aaa.common.Constants.TYPE;
import static org.cdpg.dx.auth.authorization.model.DxRole.CONSUMER;
import static org.cdpg.dx.auth.authorization.model.DxRole.CONSUMER_DELEGATE;
import static org.cdpg.dx.auth.authorization.model.DxRole.PROVIDER;
import static org.cdpg.dx.auth.authorization.model.DxRole.PROVIDER_DELEGATE;
import static org.cdpg.dx.common.HttpStatusCode.BAD_REQUEST;
import static org.cdpg.dx.common.HttpStatusCode.CONFLICT;
import static org.cdpg.dx.common.HttpStatusCode.FORBIDDEN;
import static org.cdpg.dx.common.HttpStatusCode.INTERNAL_SERVER_ERROR;
import static org.cdpg.dx.database.elastic.util.Constants.APD_URL;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.item.util.GetItemRequest;
import org.cdpg.dx.acl.policy.dao.PolicyDao;
import org.cdpg.dx.acl.policy.dao.model.PolicyDto;
import org.cdpg.dx.acl.policy.dao.model.VerifyPolicyDto;
import org.cdpg.dx.acl.policy.service.PolicyService;
import org.cdpg.dx.acl.policy.service.model.CreatePolicyRequest;
import org.cdpg.dx.catalogueService.models.ItemType;
import org.cdpg.dx.common.HttpStatusCode;
import org.cdpg.dx.common.ResponseUrn;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.common.model.ResourceObj;
import org.cdpg.dx.database.postgres.models.QueryResult;

public class PolicyServiceImpl implements PolicyService {
  private static final Logger LOGGER = LogManager.getLogger(PolicyServiceImpl.class);
  private static final String FAILURE_MESSAGE = "Policy could not be deleted";
  private final ItemService itemService;
  private final PolicyDao policyDao;
  private final String apdUrl;


  public PolicyServiceImpl(ItemService itemService, PolicyDao policyDao, String apdUrl) {
    this.itemService = itemService;
    this.apdUrl = apdUrl;
    this.policyDao = policyDao;
  }


  @Override
  public Future<Void> createPolicy(List<CreatePolicyRequest> requests, DxUser caller) {
    // ownership checks -- caller must be owner or org delegate; simplified here
    UUID userId = caller.sub();

    Set<UUID> itemIds = requests.stream()
        .map(CreatePolicyRequest::getItemId)
        .collect(Collectors.toSet());
    Set<String> itemTypes = requests.stream()
        .map(req -> req.getItemType().getTypeValue())
        .collect(Collectors.toSet());

    // Fail fast if resource_group
    if (itemTypes.contains(ITEM_TYPE_RESOURCE_GROUP)) {
      LOGGER.debug("Contains resource group");
      return Future.failedFuture(
          generateErrorResponse(BAD_REQUEST, "Policy creation for resource group is restricted"));
    }

    LOGGER.debug("itemIds: {}", itemIds);
    LOGGER.debug("itemTypes: {}", itemTypes);

    //Ownership check
    return checkForItemsInDb(itemIds, itemTypes, caller)
        .compose(providerIds -> {
          LOGGER.debug("no.of providerIds: {}", providerIds.size());
          if (providerIds.size() == 1 && providerIds.contains(userId)) {
            // Step 2: Ensure no duplicate policy
            return policyDao.checkExistingPoliciesForIds(requests, userId);
          } else {
            return Future.failedFuture(
                generateErrorResponse(FORBIDDEN, "Access Denied: Not owner of resource"));
          }
        })
        .compose(queryResult -> {
          JsonArray existingPolicies = queryResult.getRows();
          if (existingPolicies != null && !existingPolicies.isEmpty()) {
            List<String> existingIds = existingPolicies.stream()
                .map(obj -> ((JsonObject) obj).getString("_id"))
                .collect(Collectors.toList());
            LOGGER.error("Policy already exists for items: {}", existingIds);
            return Future.failedFuture(
                generateErrorResponse(CONFLICT,
                    "Policy already exists for some of the requested items: " + existingIds));
          }

          // No duplicates found → insert new policies
          return policyDao.insertPolicies(requests, userId);
        })
        .onSuccess(rowList -> {
          JsonArray response = createResponseArray(rowList);
          LOGGER.debug("Policy created successfully with info: {}", response);
        })
        .onFailure(err -> {
          LOGGER.error("Failed to create policy: {}", err.getMessage());
        })
        .mapEmpty();
  }

  public Future<Set<UUID>> checkForItemsInDb(
      Set<UUID> itemIdList, Set<String> itemTypeRequest, DxUser user) {

    if (itemIdList.isEmpty()) {
      LOGGER.warn("item id list is empty...");
      return Future.succeededFuture(Set.of());
    }

    // Fetch items from catalogue directly
    return fetchResourcesFromCatalogue(itemIdList)
        .compose(resourceObjs -> {
          Set<UUID> providerIdSet = new HashSet<>();

          for (ResourceObj obj : resourceObjs) {
            // Validate item types
            if (!itemTypeRequest.contains(obj.getItemType().name())) {
              return Future.failedFuture(
                  generateErrorResponse(BAD_REQUEST,
                      "Invalid item type for ID: " + obj.getItemId()));
            }

            // Optionally: validate user has access via resource server URLs
            // if (obj.getResourceServerUrls().stream().noneMatch(url -> url.equals(user.getResourceServerUrls()))) {
            //     return Future.failedFuture(generateErrorResponse(FORBIDDEN,
            //             "Access denied: user does not have rights for resource ID " + obj.getItemId()));
            // }

            providerIdSet.add(obj.getProviderId());
          }

          return Future.succeededFuture(providerIdSet);
        })
        .recover(failure -> {
          String failureMessage = failure.getMessage();
          if (failureMessage.contains(TYPE) && failureMessage.contains(TITLE)) {
            return Future.failedFuture(failureMessage);
          } else {
            return Future.failedFuture(generateErrorResponse(BAD_REQUEST, failureMessage));
          }
        });
  }

  public Future<List<ResourceObj>> fetchResourcesFromCatalogue(Set<UUID> ids) {
    List<Future> futures = ids.stream()
        .map(this::fetchAndValidateResource) // fetch each UUID
        .collect(Collectors.toList());

    // Combine all futures
    return CompositeFuture.all(futures)
        .map(composite -> futures.stream()
            .map(f -> ((Future<ResourceObj>) f).result())
            .collect(Collectors.toList()));
  }

  /**
   * Fetch a single resource using ItemService and apply all catalogue validations
   */
  private Future<ResourceObj> fetchAndValidateResource(UUID id) {
    Promise<ResourceObj> promise = Promise.promise();

    GetItemRequest request = new GetItemRequest(id.toString(), "");
    itemService.getItem(request)
        .onFailure(
            ar -> {
              LOGGER.error("fetchItem error : " + ar.getMessage());
              promise.fail(INTERNAL_SERVER_ERROR.getDescription());
            })
        .onSuccess(
            catSuccessResponse -> {
              // Filter out null Elasticsearch responses
              List<JsonObject> validResponses = catSuccessResponse.getElasticsearchResponses()
                  .stream()
                  .filter(Objects::nonNull)
                  .toList();

              if (!validResponses.isEmpty()) {
                JsonObject resultJson = validResponses.getFirst();
                LOGGER.info(resultJson.encodePrettily());
                List<String> resServerUrls = null;
                String apdUrlOfResource = "";

                // Validate type
                String type = resultJson.getJsonArray(TYPE).getString(0);
                String idFromResponse = resultJson.getString(ID);
                /* check if the id being sent is of valid type*/
                if (!ITEM_TYPE_DATA_BANK.equalsIgnoreCase(type) &&
                    !ITEM_TYPE_AI_MODEL.equalsIgnoreCase(type)) {
                  LOGGER.error("Invalid item type: {}", type);
                  promise.fail(generateFailureMessage(BAD_REQUEST, ResponseUrn.BAD_REQUEST_URN,
                      "Given id is invalid - only DataBank or AiModel items are supported, but " +
                          "got: " + type));
                  return;
                }

                // Extract provider
                UUID provider = UUID.fromString(resultJson.getString(PROVIDER_USER_ID));
                if (provider == null) {
                  promise.fail(generateFailureMessage(INTERNAL_SERVER_ERROR,
                      ResponseUrn.INTERNAL_SERVER_ERROR,
                      "Provider ID missing in catalogue response"));
                  return;
                }

                // Extract resource servers
                resServerUrls = resultJson.getJsonArray("resourceServer")
                    .stream()
                    .map(obj -> ((JsonObject) obj).getString("url"))
                    .filter(Objects::nonNull)
                    .toList();

                if (resServerUrls.isEmpty()) {
                  promise.fail(generateFailureMessage(INTERNAL_SERVER_ERROR,
                      ResponseUrn.INTERNAL_SERVER_ERROR,
                      "Resource server URLs missing in catalogue response"));
                  return;
                }
                apdUrlOfResource = resultJson.getString(APD_URL);
                if (!apdUrl.equals(apdUrlOfResource)) {
                  /* if the resource has an APD URL that is not equal to the current APD URL*/
                  String failureMessage =
                      generateFailureMessage(
                          FORBIDDEN,
                          ResponseUrn.FORBIDDEN_URN,
                          "Resource is forbidden to access, as the APD URL for the resource : "
                              + apdUrlOfResource
                              + " is different than the current APD : "
                              + apdUrl);
                  promise.fail(failureMessage);
                } else {
                  ItemType itemType = null;
                  if (type.equalsIgnoreCase(ITEM_TYPE_DATA_BANK)) {
                    itemType = ItemType.DATABANK;
                  } else if (type.equalsIgnoreCase(ITEM_TYPE_AI_MODEL)) {
                    itemType = ItemType.AIMODEL;
                  }
                  ResourceObj resourceObj =
                      new ResourceObj(id, provider, resServerUrls, itemType);
                  promise.complete(resourceObj);
                }
              } else {
                String message = "Item not found for ID: " + id;
                LOGGER.error(message);
                promise.fail(new DxForbiddenException(message));
              }
            });

    return promise.future();
  }


  /**
   * Generate failure JSON string (same as CatalogueClient)
   */
  private String generateFailureMessage(HttpStatusCode httpStatusCode, ResponseUrn responseUrn,
                                        String detail) {
    return new JsonObject()
        .put(TYPE, httpStatusCode.getValue())
        .put(TITLE, responseUrn.getUrn())
        .put(DETAIL, detail)
        .encode();
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
   * Verify if an ACTIVE policy exists for a given user/item pair.
   */
  @Override
  public Future<VerifyPolicyDto> initiateVerifyPolicy(UUID ownerId, String userEmail, UUID itemId,
                                                      ItemType itemType, DxUser user) {
    Promise<VerifyPolicyDto> promise = Promise.promise();

    policyDao.checkExistingPoliciesForIds(itemId, ownerId, userEmail)
        .onSuccess(queryResult -> {
          JsonArray rows = queryResult.getRows();
          if (rows != null && !rows.isEmpty()) {
            JsonObject row = rows.getJsonObject(0);
            UUID policyId = UUID.fromString(row.getString("_id"));
            JsonObject constraints = row.getJsonObject("constraints");

            // Fetch full policy constraints (optional deep validation)
            policyDao.verifyPolicy(policyId)
                .onSuccess(verifiedPolicy -> {
                  VerifyPolicyDto verifyPolicyDto = new VerifyPolicyDto(
                      ResponseUrn.VERIFY_SUCCESS_URN.getUrn(),
                      constraints
                  );
                  promise.complete(verifyPolicyDto);
                })
                .onFailure(promise::fail);

          } else {
            promise.fail(generateErrorResponse(HttpStatusCode.FORBIDDEN,
                "No ACTIVE policy exists for this user/item"));
          }
        })
        .onFailure(err -> {
          LOGGER.error("Error during initiateVerifyPolicy: {}", err.getMessage());
          promise.fail(generateErrorResponse(INTERNAL_SERVER_ERROR, err.getMessage()));
        });

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
