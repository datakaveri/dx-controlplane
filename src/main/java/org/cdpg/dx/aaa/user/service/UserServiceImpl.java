package org.cdpg.dx.aaa.user.service;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.*;
import java.util.stream.Collectors;

import org.cdpg.dx.aaa.credit.service.CreditService;
import org.cdpg.dx.aaa.organization.models.OrganizationCreateRequest;
import org.cdpg.dx.aaa.organization.models.OrganizationJoinRequest;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.aaa.user.dao.CustomRoleDAO;
import org.cdpg.dx.aaa.user.models.CustomRole;
import org.cdpg.dx.aaa.user.models.UserInfo;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.exception.DxInternalServerErrorException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.database.elastic.model.QueryModel;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;
import org.cdpg.dx.database.elastic.util.QueryType;
import org.cdpg.dx.database.postgres.models.PaginatedResult;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.cdpg.dx.aaa.common.Constants.*;
import static org.cdpg.dx.aaa.user.util.constants.*;

public class UserServiceImpl implements UserService {

  private static final Logger LOGGER = LoggerFactory.getLogger(UserServiceImpl.class);
  private final KeycloakUserService keycloakUserService;
  private final OrganizationService organizationService;
  private final CreditService creditService;
  private final ElasticsearchService elasticsearchService;
  private final String docUserIndex;
  private final CustomRoleDAO customRoleDAO;


  public UserServiceImpl(
    KeycloakUserService keycloakUserService,
    OrganizationService organizationService,
    CreditService creditService,
    ElasticsearchService elasticsearchService,
    CustomRoleDAO customRoleDAO,
    String docUserIndex
  ) {

    this.keycloakUserService = keycloakUserService;
    this.creditService = creditService;
    this.organizationService = organizationService;
    this.elasticsearchService = elasticsearchService;
    this.customRoleDAO = customRoleDAO;
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
  public Future<DxUser> updateUserInfo(UUID userId, Boolean accountEnabled) {
    return keycloakUserService.getUserById(userId)
      .compose(existingUser ->
        keycloakUserService
          .updateUserAttributes(
            userId,
            Map.of("account_enabled", String.valueOf(accountEnabled))
          )
          .map(v -> existingUser)
      );
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
        if (result == null || result.getDocId() == null) {
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

  @Override
  public Future<CustomRole> addCustomRoleAndScope(JsonObject body) {

    CustomRole cr = CustomRole.fromJson(body);

    return customRoleDAO.create(cr)
      .compose(createdRole -> {

        UUID userId = createdRole.userId();
        JsonArray scopes = createdRole.scope();

        return keycloakUserService
          .addCustomRoleToUser(userId, "custom_role")
          .compose(roleAdded -> {

            if (!roleAdded) {
              return Future.failedFuture(
                new DxInternalServerErrorException("Failed to assign role to user")
              );
            }

            // Scope is optional
            if (scopes == null || scopes.isEmpty()) {
              return Future.succeededFuture(createdRole);
            }

            List<String> scopeList = scopes.stream()
              .map(Object::toString)
              .toList();

            return keycloakUserService
              .setCustomScopeToUser(userId, scopeList)
              .compose(scopeAdded -> {
                if (!scopeAdded) {
                  return Future.failedFuture(
                    new DxInternalServerErrorException("Failed to assign scopes to user")
                  );
                }
                return Future.succeededFuture(createdRole);
              });
          });
      });
  }


  @Override
 public Future<PaginatedResult<CustomRole>> getAllCustomRoles(PaginatedRequest request)
  {
    return customRoleDAO.getAllWithFilters(request);
  }


  @Override
  public Future<List<CustomRole>> getCustomRoleRequestByRequester(UUID userId) {

    Map<String, Object> filterMap =
      Map.of(REQUESTED_BY, userId.toString());

    return customRoleDAO.getAllWithFilters(filterMap)
      .compose(customRoles -> {
        if (customRoles == null || customRoles.isEmpty()) {
          return Future.failedFuture(
            new DxNotFoundException(
              "No custom role request found for requesterId: " + userId
            )
          );
        }
        return Future.succeededFuture(customRoles);
      });
  }

//  @Override
//  public Future<CustomRole> getCustomRoleRequestByCustomRoleId(UUID customRoleId) {
//
//    return customRoleDAO.get(customRoleId)
//      .compose(customRole -> {
//        if (customRole == null) {
//          return Future.failedFuture(
//            new DxNotFoundException(
//              "No custom role request found for customRoleId: " + customRoleId
//            )
//          );
//        }
//        return Future.succeededFuture(customRole);
//      });
//  }

  @Override
  public Future<CustomRole> getCustomRoleByUserAndRequester(UUID targetUserId, UUID requesterId) {

    Map<String,Object> filter = Map.of(USER_ID,targetUserId.toString(),REQUESTED_BY,requesterId.toString());
    return customRoleDAO.getAllWithFilters(filter)
      .compose(customRole -> {
        if (customRole == null ||  customRole.isEmpty()) {
          return Future.failedFuture(
            new DxNotFoundException(
              "No custom role request found"
            )
          );
        }
        return Future.succeededFuture(customRole.getFirst());
      });
  }



  @Override
  public Future<CustomRole> updateCustomScope(UUID requestId, JsonArray scopes) {

    return customRoleDAO.get(requestId)
      .compose(existingRole -> {

        if (existingRole == null) {
          return Future.failedFuture(
            new DxNotFoundException(
              "Custom role request not found for id: " + requestId
            )
          );
        }

        Map<String,Object> conditionfilter = Map.of(CUSTOM_ROLE_ID,requestId.toString());
        Map<String,Object> updateFilter = Map.of(SCOPE,scopes);


        return customRoleDAO
          .update(conditionfilter,updateFilter)
          .compose(updatedRole -> {

            // Scope is optional
            if (scopes.isEmpty()) {
              return Future.succeededFuture(updatedRole);
            }

            List<String> scopeList = scopes.stream()
              .map(Object::toString)
              .toList();

            return keycloakUserService
              .setCustomScopeToUser(updatedRole.userId(), scopeList)
              .compose(scopeUpdated -> {

                if (!scopeUpdated) {
                  return Future.failedFuture(
                    new DxInternalServerErrorException(
                      "Failed to update scopes in Keycloak"
                    )
                  );
                }

                return Future.succeededFuture(updatedRole);
              });
          });
      });
  }

  @Override
  public Future<Boolean> deleteScope(UUID requestedBy, JsonObject body) {

    UUID requestId = body.getString("requestId") != null
      ? UUID.fromString(body.getString("requestId"))
      : null;

    UUID userId = body.getString("userId") != null
      ? UUID.fromString(body.getString("userId"))
      : null;

    String scopeToDelete = body.getString("scope");

    // ---------- Validation ----------
    if (requestId == null && (userId == null || scopeToDelete == null)) {
      return Future.failedFuture(
        "Either requestId or (userId and scope) is required"
      );
    }

    // =====================================================
    // CASE 1: requestId based delete
    // =====================================================
    if (requestId != null) {

      return customRoleDAO
        .get(requestId)
        .compose(customRole -> {

          if (customRole == null) {
            return Future.failedFuture("Request not found");
          }

          if (!customRole.requestedBy().equals(requestedBy)) {
            return Future.failedFuture("Unauthorized requester");
          }

          Set<String> scopesToRemove = customRole.scope()
            .stream()
            .map(Object::toString)
            .collect(Collectors.toSet());

          return keycloakUserService
            .clearDelegationScopes(customRole.userId(), scopesToRemove)
            .compose(v -> customRoleDAO.delete(requestId));
        });
    }

    // =====================================================
    // CASE 2: userId + scope based delete
    // =====================================================
    return getCustomRoleByUserAndRequester(userId, requestedBy)
      .compose(customRole -> {

        if (customRole == null) {
          return Future.failedFuture("No custom role found");
        }

        JsonArray existingScopes = customRole.scope();

        if (existingScopes == null || !existingScopes.contains(scopeToDelete)) {
          return Future.failedFuture("Scope not found");
        }

        // Remove scope locally
        JsonArray updatedScopes = new JsonArray();
        existingScopes.forEach(s -> {
          if (!scopeToDelete.equals(s)) {
            updatedScopes.add(s);
          }
        });

        return keycloakUserService
          .clearDelegationScopes(
            customRole.userId(),
            Set.of(scopeToDelete)
          )
          .compose(v -> {

            // No scopes left → delete row
            if (updatedScopes.isEmpty()) {
              return customRoleDAO.delete(customRole.id());
            }

            // Update remaining scopes
            return updateCustomScope(customRole.id(), updatedScopes).map(res->true);
          });
      });
  }




}




