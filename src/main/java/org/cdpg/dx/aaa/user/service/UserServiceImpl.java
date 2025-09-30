package org.cdpg.dx.aaa.user.service;

import com.hazelcast.collection.ICollection;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;

import java.util.*;

import org.cdpg.dx.aaa.common.ResponseModel;
import org.cdpg.dx.aaa.credit.service.CreditService;
import org.cdpg.dx.aaa.organization.models.OrganizationCreateRequest;
import org.cdpg.dx.aaa.organization.models.OrganizationJoinRequest;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.aaa.user.models.UserInfo;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.database.elastic.model.ElasticsearchResponse;
import org.cdpg.dx.database.elastic.model.QueryModel;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;
import org.cdpg.dx.database.elastic.util.QueryType;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.cdpg.dx.aaa.common.Constants.FIELD;
import static org.cdpg.dx.aaa.common.Constants.VALUE;
import static org.cdpg.dx.database.elastic.util.Constants.ID_KEYWORD;

public class UserServiceImpl implements UserService {

  private static final Logger LOGGER = LoggerFactory.getLogger(UserServiceImpl.class);
  private final KeycloakUserService keycloakUserService;
  private final OrganizationService organizationService;
  private final CreditService creditService;
  private final ElasticsearchService elasticsearchService;
  private final String docUserIndex;


  public UserServiceImpl(
    KeycloakUserService keycloakUserService,
    OrganizationService organizationService,
    CreditService creditService,
    ElasticsearchService elasticsearchService,
    String docUserIndex
  ) {

    this.keycloakUserService = keycloakUserService;
    this.creditService = creditService;
    this.organizationService = organizationService;
    this.elasticsearchService = elasticsearchService;
    this.docUserIndex = docUserIndex;
  }

  private static JsonObject getOrgEntry(
    List<OrganizationCreateRequest> lastOrgCreateReq, List<OrganizationJoinRequest> joinReqList) {

    JsonObject orgJson = new JsonObject();
    if (lastOrgCreateReq != null && !lastOrgCreateReq.isEmpty()) {
      orgJson = lastOrgCreateReq.getFirst().toJsonForUsers();

      orgJson.put("request_type", "organisation_create");
    } else if (joinReqList != null && !joinReqList.isEmpty()) {

      orgJson = joinReqList.getFirst().toJsonForUsers();
      orgJson.put("request_type", "organisation_join");
    }
    LOGGER.debug(orgJson.encodePrettily());
    return orgJson;
  }

  @Override
  public Future<DxUser> getUserInfo(DxUser dxUser) {

    Future<Boolean> pendingProvider = Future.succeededFuture(false);

    UUID orgId = null;
    String orgIdStr = dxUser.organisationId();

    if (orgIdStr != null && !orgIdStr.isBlank()) {
      try {
        orgId = UUID.fromString(orgIdStr);
      } catch (IllegalArgumentException e) {
        LOGGER.error("Invalid UUID for organisationId: {}", orgIdStr);
      }
    }

    if (orgId != null) {
      pendingProvider = organizationService.hasPendingProviderRole(dxUser.sub(), orgId);
    }

    Future<Boolean> pendingCompute = creditService.hasPendingComputeRequest(dxUser.sub());

    Future<List<OrganizationJoinRequest>> joinRequests =
      organizationService.getOrganizationJoinRequestsByUser(dxUser.sub());

    Future<List<OrganizationCreateRequest>> createRequests =
      organizationService.getOrganizationCreateRequestsByUserId(dxUser.sub());

    return Future.all(pendingProvider, pendingCompute, joinRequests, createRequests)
      .map(
        cf -> {
          List<String> pendingRoles = new ArrayList<>();
          if (cf.resultAt(0)) pendingRoles.add("provider");
          if (cf.resultAt(1)) pendingRoles.add("compute");
          List<OrganizationJoinRequest> joinReqList = cf.resultAt(2);

          // TODO will consider no only one request will be available need to upda for multiple
          // requests

          List<OrganizationCreateRequest> createOrgReqList = cf.resultAt(3);
          JsonObject orgJson = getOrgEntry(createOrgReqList, joinReqList);

          return DxUser.withPendingRoles(dxUser, pendingRoles, orgJson);
        });
  }

  @Override
  public Future<DxUser> getUserInfoByID(UUID userId) {
    return keycloakUserService.getUserById(userId).compose(this::getUserInfo);
  }

  @Override
  public Future<Void> createUserInfo(UserInfo userInfo) {
    LOGGER.info("Creating User Info in UserServiceImpl");
    LOGGER.info("DocUserIndex:{}", docUserIndex);

    LOGGER.info("User info:{}", userInfo.toJson().encodePrettily());

    Promise<Void> promise = Promise.promise();

    QueryModel queryModel = new QueryModel();
    queryModel.createQueryModelFromDocument(userInfo.toJson());

    elasticsearchService
      .createDocuments(docUserIndex, Collections.singletonList(queryModel))
      .onSuccess(v -> {
        LOGGER.debug("User info stored successfully: {}", userInfo);
        promise.complete();
      })
      .onFailure(err -> {
        LOGGER.error("Failed to store user info", err);
        promise.fail(err);
      });

    return promise.future();
  }

  @Override
  public Future<UserInfo> getUserInfo(String userId) {
    LOGGER.info("Inside getUserInfo");

    QueryModel termQuery = new QueryModel(QueryType.TERM);
    termQuery.setQueryParameters(Map.of(FIELD, "userId.keyword", VALUE, userId));

    return elasticsearchService.getSingleDocument(docUserIndex, termQuery)
      .map(elResponse -> {
        if (elResponse == null || elResponse.getSource().isEmpty()) {
          return null;
        }
        return UserInfo.fromJson(elResponse.getSource());
      });
  }


  @Override
  public Future<UserInfo> patchUserInfo(String userId, JsonObject updates) {
    LOGGER.debug("Updating user info: {}", userId);
    Promise<UserInfo> promise = Promise.promise();

    if (userId == null || userId.isBlank()) {
      return Future.failedFuture("UserId not present in request");
    }

    QueryModel termQuery = new QueryModel(QueryType.TERM);
    termQuery.setQueryParameters(Map.of(
      "field", "userId.keyword",
      "value", userId
    ));

    elasticsearchService.getSingleDocument(docUserIndex, termQuery)
      .onSuccess(result -> {
        if (result == null || result.getTotalHits() < 1) {
          LOGGER.debug("User info with userId {} not found for update", userId);
          promise.fail(new DxNotFoundException("User info not found"));
          return;
        }

        LOGGER.debug("User info found: {}", result.getSource());
        String docId = result.getDocId();

        QueryModel patchQueryModel = new QueryModel();
        patchQueryModel.createQueryModelFromDocument(updates);

        elasticsearchService.updateDocument(docUserIndex, docId, patchQueryModel)
          .onSuccess(v -> {
            LOGGER.debug("User info with userId {} updated successfully", userId);
            UserInfo updatedUserInfo = UserInfo.fromJson(result.getSource().mergeIn(updates));
            promise.complete(updatedUserInfo);
          })
          .onFailure(failure -> {
            LOGGER.error("Failed to update user info with userId {}: {}", userId, failure.getMessage());
            promise.fail(new DxBadRequestException("Failed to update user info: " + failure.getMessage()));
          });
      })
      .onFailure(promise::fail);

    return promise.future();
  }


}




