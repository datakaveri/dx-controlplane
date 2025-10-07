package org.cdpg.dx.acl.policy.dao.impl;

import static org.cdpg.dx.aaa.common.Constants.ACTIVE;
import static org.cdpg.dx.aaa.common.Constants.DETAIL;
import static org.cdpg.dx.aaa.common.Constants.ID;
import static org.cdpg.dx.aaa.common.Constants.ITEM_TYPE_AI_MODEL;
import static org.cdpg.dx.aaa.common.Constants.ITEM_TYPE_DATA_BANK;
import static org.cdpg.dx.aaa.common.Constants.PROVIDER_USER_ID;
import static org.cdpg.dx.aaa.common.Constants.TITLE;
import static org.cdpg.dx.aaa.common.Constants.TYPE;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
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
import org.cdpg.dx.acl.policy.service.model.CreatePolicyRequest;
import org.cdpg.dx.catalogueService.models.ItemType;
import org.cdpg.dx.common.HttpStatusCode;
import org.cdpg.dx.common.ResponseUrn;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.common.model.ResourceObj;
import org.cdpg.dx.database.postgres.models.Condition;
import org.cdpg.dx.database.postgres.models.InsertQuery;
import org.cdpg.dx.database.postgres.models.Join;
import org.cdpg.dx.database.postgres.models.OrderBy;
import org.cdpg.dx.database.postgres.models.QueryResult;
import org.cdpg.dx.database.postgres.models.SelectQuery;
import org.cdpg.dx.database.postgres.models.UpdateQuery;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class PolicyDaoImpl implements PolicyDao {
  private static final Logger LOGGER = LogManager.getLogger(PolicyDaoImpl.class);
  private static final String POLICY_TABLE = "policy";
  private static final String USER_TABLE = "user_table";
  private static final String RESOURCE_TABLE = "resource_entity";
  private final PostgresService postgresService;
  private final ItemService itemService;
  private final String apdUrl;


  public PolicyDaoImpl(PostgresService postgresService, ItemService itemService,
                       JsonObject options) {
    this.apdUrl = options.getString(APD_URL);
    this.postgresService = postgresService;
    this.itemService = itemService;
  }

  @Override
  public Future<Set<UUID>> checkForItemsInDb(
      Set<UUID> itemIdList, Set<String> itemTypeRequest, DxUser user) {
    Promise<Set<UUID>> promise = Promise.promise();

    if (itemIdList.isEmpty()) {
      LOGGER.warn("item id list is empty...");
      promise.complete(Set.of());
      return promise.future();
    }

    List<String> uuidStrings = itemIdList.stream()
        .map(UUID::toString)
        .toList();

    List<Object> uuidList = new ArrayList<>(uuidStrings);

    // DB query to fetch existing items
    SelectQuery selectQuery = new SelectQuery()
        .setTable(RESOURCE_TABLE)
        .setColumns(List.of("_id", "provider_id", "item_type", "resource_server_urls"))
        .setCondition(new Condition("_id", Condition.Operator.IN, uuidList));

    postgresService
        .select(selectQuery, false)
        .onFailure(
            existingIdFailureHandler -> {
              LOGGER.error(
                  "checkForItemsInDb db fail {}",
                  existingIdFailureHandler.getLocalizedMessage());
              promise.fail(
                  generateErrorResponse(
                      INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR.getDescription()));
            })
        .onSuccess(
            existingIdSuccessHandler -> {
              Set<UUID> providerIdSet = new HashSet<>();
              Set<UUID> existingItemIds = new HashSet<>();
              Set<String> itemTypeDb = new HashSet<>();
              Set<String> rsServerUrlSetDb;
              if (!existingIdSuccessHandler.getRows().isEmpty()) {
                for (Object object : existingIdSuccessHandler.getRows()) {
                  JsonObject row = (JsonObject) object;
                  providerIdSet.add(UUID.fromString(row.getString("provider_id")));
                  existingItemIds.add(UUID.fromString(row.getString("_id")));
                  itemTypeDb.add(row.getString("item_type"));
                  JsonArray rsJsonArray = row.getJsonArray("resource_server_urls", new JsonArray());
                  rsServerUrlSetDb = rsJsonArray.stream()
                      .map(Object::toString)
                      .collect(Collectors.toSet());
                  LOGGER.debug("rsServerUrlSetDb: {}", rsServerUrlSetDb);
                }
                itemIdList.removeAll(existingItemIds);
              }
              if (!itemIdList.isEmpty()) {
                Future<List<ResourceObj>> resourceObjList = fetchResourcesFromCatalogue(itemIdList);
                Future<Set<UUID>> providerIdsFromCat =
                    resourceObjList
                        .compose(
                            res -> {
                              // Validate item types
                              List<ResourceObj> resourceObjs = res;
                              for (ResourceObj obj : resourceObjs) {
                                if (!itemTypeRequest.contains(obj.getItemType().name())) {
                                  return Future.failedFuture(generateErrorResponse(BAD_REQUEST,
                                      "Invalid item type for ID: " + obj.getItemId()));
                                }

                                // Validate user's resource server URL
                                //TODO: Confirm if the user will have the resource
                                // urls in the token
//                                if (obj.getResourceServerUrls().stream().noneMatch(url -> url.equals(user.email()))) {
//                                  return Future.failedFuture(generateErrorResponse(FORBIDDEN,
//                                      "Access denied: user does not have rights for resource ID " + obj.getItemId()));
//                                }
                              }
                              return insertItemsIntoDb(res);
                            })
                        .onFailure(
                            failureHandler -> {
                              String failureMessage = failureHandler.getMessage();
                              if (failureMessage.contains(TYPE)
                                  && failureMessage.contains(TITLE)) {
                                promise.fail(failureHandler.getMessage());
                              } else {
                                promise.fail(
                                    generateErrorResponse(BAD_REQUEST, failureMessage));
                              }
                            });
                providerIdsFromCat
                    .onSuccess(
                        insertItemsSuccessHandler -> {
                          providerIdSet.addAll(insertItemsSuccessHandler);
                          promise.complete(providerIdSet);
                        })
                    .onFailure(
                        insertItemsFailureHandler -> {
                          LOGGER.error(
                              "insertItemInDbFail "
                                  + insertItemsFailureHandler.getLocalizedMessage());

                          promise.tryFail(
                              insertItemsFailureHandler
                                  .getLocalizedMessage()
                                  .equalsIgnoreCase(
                                      "Access Denied: You do not have "
                                          + "ownership rights for this resource.")
                                  ? generateErrorResponse(
                                  FORBIDDEN,
                                  insertItemsFailureHandler.getLocalizedMessage())
                                  : generateErrorResponse(
                                  BAD_REQUEST,
                                  insertItemsFailureHandler.getLocalizedMessage()));
                        });
              } else {
                LOGGER.debug("itemTypeDb: {}", String.join(",", itemTypeDb.stream()
                    .map(Object::toString)
                    .toList()));
                LOGGER.debug("itemTypeRequest: " + itemTypeRequest.toString());
                LOGGER.debug("providerIdSet: {}", String.join(",", providerIdSet.stream()
                    .map(Object::toString)
                    .toList()));
                if (!itemTypeDb.containsAll(itemTypeRequest)) {
                  LOGGER.debug("item type issue...");
                  promise.fail(
                      generateErrorResponse(BAD_REQUEST, "Invalid item type."));
                } else if (!providerIdSet.contains(user.sub())) {
                  //else if (!rsServerUrlSetDb.contains("user.getResourceServerUrl()")) {
                  LOGGER.debug("rsurl issue...");
                  promise.fail(
                      generateErrorResponse(
                          FORBIDDEN,
                          "Access Denied: You do not have ownership rights for this resource."));
                } else {
                  LOGGER.debug("completing successfully...");
                  promise.complete(providerIdSet);
                }
              }
            });
    return promise.future();
  }

  @Override
  public Future<Boolean> checkExistingPoliciesForId(
      List<CreatePolicyRequest> createPolicyRequestList, UUID providerId) {
    Promise<Boolean> promise = Promise.promise();

    // Convert itemIds and emails into List<Object>
    List<Object> itemIds = createPolicyRequestList.stream()
        .map(req -> req.getItemId().toString())
        .collect(Collectors.toList()); // still List<String>
    List<Object> itemIdsObj = new ArrayList<>(itemIds); // convert to List<Object>

    List<Object> userEmails = createPolicyRequestList.stream()
        .map(CreatePolicyRequest::getUserEmail)
        .collect(Collectors.toList()); // List<String> → List<Object>

    Condition itemIdCond = new Condition("item_id", Condition.Operator.EQUALS, itemIdsObj);
    Condition ownerIdCond =
        new Condition("owner_id", Condition.Operator.EQUALS, List.of(providerId.toString()));
    Condition statusCond = new Condition("status", Condition.Operator.EQUALS, List.of(ACTIVE));
    Condition emailCond = new Condition("user_emailid", Condition.Operator.EQUALS, userEmails);
    // expiry_at > now() is a special case → no param
    Condition expiryCond = new Condition("expiry_at", Condition.Operator.GREATER, List.of(
        LocalDateTime.now().toString()));
    Condition allConditions = new Condition(
        List.of(itemIdCond, ownerIdCond, statusCond, emailCond, expiryCond),
        Condition.LogicalOperator.AND
    );

    SelectQuery selectQuery = new SelectQuery()
        .setTable("policy")
        .setColumns(List.of("_id", "constraints"))
        .setCondition(allConditions);

    postgresService
        .select(selectQuery, false)
        .onFailure(
            failureHandler -> {
              LOGGER.error(
                  "isPolicyForIdExist fail :: " + failureHandler.getLocalizedMessage());
              promise.fail(
                  generateErrorResponse(
                      INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR.getDescription()));
            })
        .onSuccess(
            policyExists -> {
              if (policyExists.getRows() != null && !policyExists.getRows().isEmpty()) {
                List<UUID> responseArray = new ArrayList<>();
                for (int i = 0; i < policyExists.getRows().size(); i++) {
                  JsonObject row = policyExists.getRows().getJsonObject(i);
                  responseArray.add(UUID.fromString(row.getString("_id")));
                }
                LOGGER.error("Policy already Exist.");
                promise.fail(
                    generateErrorResponse(
                        CONFLICT,
                        "Policy already exist for some of the request objects "
                            + responseArray));
              } else {
                promise.complete(false);
              }
            });

    return promise.future();
  }

  @Override
  public Future<JsonObject> checkExistingPoliciesForId(UUID itemId, UUID ownerId,
                                                       String userEmailId) {
    Promise<JsonObject> promise = Promise.promise();

    Condition itemIdCond = new Condition("item_id", Condition.Operator.EQUALS,
        List.of(itemId.toString()));
    Condition ownerIdCond =
        new Condition("owner_id", Condition.Operator.EQUALS, List.of(ownerId.toString()));
    Condition statusCond = new Condition("status", Condition.Operator.EQUALS, List.of(ACTIVE));
    Condition emailCond =
        new Condition("user_emailid", Condition.Operator.EQUALS, List.of(userEmailId));
    // expiry_at > now() is a special case → no param
    Condition expiryCond = new Condition("expiry_at", Condition.Operator.GREATER,
        List.of(LocalDateTime.now().toString()));
    Condition allConditions = new Condition(
        List.of(itemIdCond, ownerIdCond, statusCond, emailCond, expiryCond),
        Condition.LogicalOperator.AND
    );

    SelectQuery selectQuery = new SelectQuery()
        .setTable("policy")
        .setColumns(List.of("_id", "constraints"))
        .setCondition(allConditions);

    postgresService
        .select(selectQuery, false)
        .onFailure(
            failureHandler -> {
              LOGGER.error(
                  "isPolicyForIdExist fail :: " + failureHandler.getLocalizedMessage());
              promise.fail(
                  generateErrorResponse(
                      INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR.getDescription()));
            })
        .onSuccess(
            policyExists -> {
              LOGGER.debug("policyExists check success...");
              LOGGER.debug(policyExists.toJson());
              if (policyExists.getRows() != null && !policyExists.getRows().isEmpty()) {
                JsonObject policyConstraints = new JsonObject();
                LOGGER.debug("size: {}", policyExists.getRows().size());
                for (int i = 0; i < policyExists.getRows().size(); i++) {
                  JsonObject row = policyExists.getRows().getJsonObject(i);
                  LOGGER.debug("row: {}", row);
                  LOGGER.debug("const: {}", row.getJsonObject("constraints"));
                  LOGGER.debug("id: {}", row.getValue("_id"));
                  policyConstraints.put(
                      "constraints", row.getJsonObject("constraints"));
                  policyConstraints.put("id", row.getValue("_id"));
                }
                LOGGER.debug("returning policyConstraints...");
                promise.complete(policyConstraints);
              } else {
                LOGGER.trace("No policy found");
                promise.complete(new JsonObject());
              }
            });

    return promise.future();
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

  @Override
  public Future<Set<UUID>> insertItemsIntoDb(List<ResourceObj> resourceObjList) {
    Promise<Set<UUID>> promise = Promise.promise();

    // Prepare providerIdSet to return later
    Set<UUID> providerIdSet =
        resourceObjList.stream().map(ResourceObj::getProviderId).collect(Collectors.toSet());

    // Loop through each ResourceObj and insert using InsertQuery
    List<Future<QueryResult>> insertFutures = new ArrayList<>();
    for (ResourceObj resourceObj : resourceObjList) {
      List<Object> resourceServerUrls = new ArrayList<> (resourceObj.getResourceServerUrls());
      List<Object> values = new ArrayList<>();
      values.add(resourceObj.getItemId().toString());
      values.add(resourceObj.getProviderId().toString());
      values.add(resourceObj.getItemType().getTypeValue());

      // Convert List<String> to plain Object
      values.add(resourceServerUrls);

      InsertQuery insertQuery = new InsertQuery()
          .setTable("resource_entity")
          .setColumns(List.of("_id", "provider_id", "item_type", "resource_server_urls"))
          .setValues(values);

      LOGGER.debug("Insert Query : {}", insertQuery.toSQL());

      // Call your DAO method that executes InsertQuery
      insertFutures.add(postgresService.insert(insertQuery));
    }

    // Compose all insert futures sequentially
    Future<Void> batchInsertFuture = Future.succeededFuture();
    for (Future<QueryResult> f : insertFutures) {
      batchInsertFuture = batchInsertFuture.compose(v -> f.mapEmpty());
    }

    // Complete the main promise
    batchInsertFuture.onComplete(ar -> {
      if (ar.succeeded()) {
        LOGGER.info("All resource_entity rows inserted successfully");
        promise.complete(providerIdSet);
      } else {
        LOGGER.error("insertItemsIntoDb failed: {}", ar.cause().getMessage());
        promise.fail(ar.cause());
      }
    });
    return promise.future();
  }

  @Override
  public Future<List<QueryResult>> insertPolicies(List<CreatePolicyRequest> createPolicyRequestList,
                                            UUID userId) {
    // Compose futures for each policy insert
    List<Future<QueryResult>> insertFutures = createPolicyRequestList.stream()
        .map(req -> {
          List<Object> values = List.of(
              req.getUserEmail(),                     // String
              req.getItemId().toString(),                        // UUID
              userId.toString(),                                 // UUID
              req.getExpiryTime().toString(),                     // LocalDateTime
              req.getConstraints(),                    // JsonObject or Map<String, Object>
              ACTIVE,                                 // String
              req.getAdditionalInfo(),                 // String or nullable
              req.getProviderComment(),               // String or nullable
              req.getFeedbackToConsumer()             // String or nullable
          );

          InsertQuery insertQuery = new InsertQuery()
              .setTable("policy")
              .setColumns(List.of(
                  "user_emailid",
                  "item_id",
                  "owner_id",
                  "expiry_at",
                  "constraints",
                  "status",
                  "additional_info",
                  "provider_comment",
                  "feedback_to_consumer"
              ))
              .setValues(values);

          LOGGER.debug("Insert Policy Query: {}", insertQuery.toSQL());
          return postgresService.insert(insertQuery);
        })
        .toList();

    // Combine all futures and collect results
    return Future.all(new ArrayList<>(insertFutures))
        .map(cf -> insertFutures.stream()
            .map(Future::result)
            .collect(Collectors.toList())
        )
        .onSuccess(results -> LOGGER.info("All policies inserted successfully"))
        .onFailure(err -> LOGGER.error("createPolicy fail :: " + err.getLocalizedMessage()));
  }

  @Override
  public Future<QueryResult> getPoliciesByConsumer(String emailId) {
//    Condition condition = new Condition()
//        .setGroup(true)
//        .setLogicalOperator(Condition.LogicalOperator.AND)
//        .setConditions(List.of(
//            new Condition()
//                .setColumn("P.user_emailid")
//                .setValues(List.of(emailId))
//                .setOperator(Condition.Operator.EQUALS),
//            new Condition()
//                .setColumn("RE.resource_server_urls")
//                .setValues(List.of(resourceServerUrls))
//                .setOperator(Condition.Operator.EQUALS)
//        ));

    SelectQuery selectQuery = new SelectQuery()
        .setTable(POLICY_TABLE)
        .setTableAlias("P")
        .setColumns(List.of(
            "P._id AS \"policyId\"",
            "P.item_id AS \"itemId\"",
            "RE.item_type AS \"itemType\"",
            "RE.resource_server_urls AS \"resourceServerUrl\"",
            "P.user_emailid AS \"consumerEmailId\"",
            "U.first_name AS \"consumerFirstName\"",
            "U.last_name AS \"consumerLastName\"",
            "U._id AS \"consumerId\"",
            "P.status AS \"status\"",
            "P.additional_info AS \"additionalInfo\"",
            "P.provider_comment AS \"providerComment\"",
            "P.feedback_to_consumer AS \"feedbackToConsumer\"",
            "P.expiry_at AS \"expiryAt\"",
            "P.constraints AS \"constraints\"",
            "P.updated_at AS \"updatedAt\"",
            "P.created_at AS \"createdAt\""
        ))
        .setCondition( new Condition()
            .setColumn("P.user_emailid")
            .setValues(List.of(emailId))
            .setOperator(Condition.Operator.EQUALS))
        .setJoins(List.of(
            new Join(Join.JoinType.LEFT, USER_TABLE, "U", "P.user_emailid", "email_id"),
            new Join(Join.JoinType.INNER, RESOURCE_TABLE, "RE", "p.item_id", "_id")
        ))
        .setOrderBy(List.of(new OrderBy("P.updated_at", OrderBy.Direction.DESC)));

    return postgresService.select(selectQuery, false).map(result -> result);
  }

  @Override
  public Future<QueryResult> getPoliciesByProvider(String ownerId) {
//    Condition condition = new Condition()
//        .setGroup(true)
//        .setLogicalOperator(Condition.LogicalOperator.AND)
//        .setConditions(List.of(
//            new Condition().setColumn("P.owner_id").setValues(List.of(ownerId))
//                .setOperator(Condition.Operator.EQUALS),
//            new Condition().setColumn("RE.resource_server_urls")
//                .setValues(List.of(resourceServerUrls)).setOperator(Condition.Operator.EQUALS)
//        ));

    SelectQuery selectQuery = new SelectQuery()
        .setTable(POLICY_TABLE)
        .setTableAlias("P")
        .setColumns(List.of(
            "P._id AS \"policyId\"",
            "P.item_id AS \"itemId\"",
            "RE.item_type AS \"itemType\"",
            "RE.resource_server_urls AS \"resourceServerUrls\"",
            "P.owner_id AS \"ownerId\"",
            "U.first_name AS \"ownerFirstName\"",
            "U.last_name AS \"ownerLastName\"",
            "U.email_id AS \"ownerEmailId\"",
            "U._id AS \"ownerId\"",
            "P.status AS \"status\"",
            "P.additional_info AS \"additionalInfo\"",
            "P.provider_comment AS \"providerComment\"",
            "P.feedback_to_consumer AS \"feedbackToConsumer\"",
            "P.expiry_at AS \"expiryAt\"",
            "P.constraints AS \"constraints\"",
            "P.updated_at AS \"updatedAt\"",
            "P.created_at AS \"createdAt\""
        ))
        .setJoins(List.of(
            new Join(Join.JoinType.INNER, "user_table", "U", "P.owner_id", "_id"),
            new Join(Join.JoinType.INNER, "resource_entity", "RE",  "P.item_id", "_id")
        ))
        .setCondition(new Condition().setColumn("P.owner_id").setValues(List.of(ownerId))
            .setOperator(Condition.Operator.EQUALS))
        .setOrderBy(List.of(new OrderBy("P.updated_at", OrderBy.Direction.DESC)));

    return postgresService.select(selectQuery, false).map(result -> result);
  }

  /**
   * Verify if a policy exists, belongs to correct user, and is ACTIVE
   */
  @Override
  public Future<QueryResult> verifyPolicy(UUID policyId) {
    Condition condition = new Condition()
        .setColumn("p._id")
        .setOperator(Condition.Operator.EQUALS)
        .setValues(List.of(policyId.toString()));
    SelectQuery query = new SelectQuery()
        .setTable(POLICY_TABLE)
        .setTableAlias("p")
        .setColumns(List.of("p.owner_id", "p.status", "r.resource_server_urls"))
        .setCondition(condition)
        .setJoins(List.of(
            new Join(Join.JoinType.INNER, RESOURCE_TABLE, "r", "p.item_id", "_id")
        ));

    return postgresService.select(query, false);
  }

  /**
   * Update policy status to DELETED if not expired
   */
  @Override
  public Future<QueryResult> deletePolicy(UUID policyId) {
    Condition condition = new Condition()
        .setGroup(true)
        .setLogicalOperator(Condition.LogicalOperator.AND)
        .setConditions(List.of(
            new Condition().setColumn("_id").setValues(List.of(policyId.toString()))
                .setOperator(Condition.Operator.EQUALS),
            new Condition().setColumn("expiry_at").setValues(List.of(LocalDateTime.now().toString()))
                .setOperator(Condition.Operator.GREATER)
        ));
    UpdateQuery query = new UpdateQuery()
        .setTable(POLICY_TABLE)
        .setColumns(List.of("status"))
        .setValues(List.of("DELETED"))
        .setCondition(condition);

    return postgresService.update(query);
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

  private String generateErrorResponse(HttpStatusCode httpStatusCode, String errorMessage) {
    return new JsonObject()
        .put(TYPE, httpStatusCode.getValue())
        .put(TITLE, httpStatusCode.getPath())
        .put(DETAIL, errorMessage)
        .encode();
  }
}