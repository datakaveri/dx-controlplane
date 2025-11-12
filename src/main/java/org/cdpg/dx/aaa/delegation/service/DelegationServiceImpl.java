package org.cdpg.dx.aaa.delegation.service;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.delegation.DelegationValidator;
import org.cdpg.dx.aaa.delegation.dao.*;
import org.cdpg.dx.aaa.delegation.models.DelegationGrant;
import org.cdpg.dx.aaa.delegation.models.DelegationScopeConstraint;
import org.cdpg.dx.aaa.delegation.models.DelegationUpdateRequest;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.auth.authorization.model.DxRole;
import org.cdpg.dx.auth.authorization.model.DxScope;
import org.cdpg.dx.common.exception.*;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;

import static org.cdpg.dx.common.util.DateTimeHelper.parseDateTime;


public class DelegationServiceImpl implements DelegationService{

  private static final Logger LOGGER = LoggerFactory.getLogger(DelegationServiceImpl.class);

  private final DelegationGrantDAO delegationGrantDAO;
  private final DelegationRequestDAO delegationRequestDAO;
  private final ScopeConstraintDAO delegationScopeConstraintDAO;
  private final ScopeConstraintDAO scopeConstraintDAO;
  private final OrganizationService organizationService;
  private final TokenDAO tokenDAO;
  private final KeycloakUserService keycloakUserService;
  private final DelegationValidator delegationValidator;
  private final ItemService itemService;

  public DelegationServiceImpl(DelegationDAOFactory factory, KeycloakUserService keycloakUserService, OrganizationService organizationService,ItemService itemService) {
    this.delegationGrantDAO = factory.delegationGrantDAO();
    this.delegationRequestDAO = factory.delegationRequestDAO();
    this.scopeConstraintDAO = factory.scopeConstraintDAO();
    this.tokenDAO = factory.tokenDAO();
    this.delegationScopeConstraintDAO = factory.scopeConstraintDAO();
    this.organizationService = organizationService;
    this.keycloakUserService = keycloakUserService;
    this.itemService = itemService;
    this.delegationValidator = new DelegationValidator(organizationService,itemService);
  }


  @Override
  public Future<DelegationGrant> createDelegationGrant(DelegationGrant delegationGrant, Set<String>userRoles,List<JsonObject> constraintsJson,JsonArray scopes) {

    LOGGER.info("ServiceImplementation of createDelegationGrant");

    JsonObject delegationGrantBody = delegationGrant.toJson();


    if (constraintsJson.isEmpty()) {
      return Future.failedFuture(new DxBadRequestException("No constraints provided for delegation grant"));
    }

    LocalDateTime globalExpiry = parseDateTime(delegationGrantBody.getString("expiry_at"));

    return delegationValidator.validateAllConstraints(userRoles, constraintsJson)
      .compose(v -> delegationValidator.validateEntityOwnership(delegationGrant.delegatorId(), userRoles, constraintsJson))
      .compose(map -> delegationGrantDAO.create(delegationGrant))
      .compose(createdGrant -> insertScopeConstraints(createdGrant.delegationId(), constraintsJson).map(v -> createdGrant))
      .compose(createdGrant ->
        keycloakUserService.addRoleToUser(createdGrant.delegateId(), DxRole.DELEGATE)
          .map(v -> {
            LOGGER.info("Delegate role added to user: {}", createdGrant.delegateId());
            return createdGrant;
          }))
      .compose(createdGrant -> {
        List<String> scopesList = scopes.getList();

        // Chain addition of all scope roles sequentially
        Future<Void> addAllScopesFuture = Future.succeededFuture();

        for (String scope : scopesList) {
          addAllScopesFuture = addAllScopesFuture.compose(v ->
            keycloakUserService.addScopeToUser(createdGrant.delegateId(), DxScope.fromString(scope))
              .onSuccess(x -> LOGGER.info("Scope {} added to user {}", scope, createdGrant.delegateId()))
              .mapEmpty()
          );
        }

        return addAllScopesFuture
          .map(v -> {
            LOGGER.info("All scopes {} added to user {}", scopesList, createdGrant.delegateId());
            return createdGrant;
          })
          .recover(err -> Future.failedFuture(BaseDxException.from(err)));
      });
  }


  @Override
  public Future<DelegationGrant> getDelegationGrantById(UUID delegationId) {

    return delegationGrantDAO.get(delegationId)
      .recover(err -> {
        BaseDxException dxEx = BaseDxException.from(err);
        if (dxEx instanceof DxNotFoundException) {
          return Future.failedFuture(new DxNotFoundException("No delegation grant found with id " + delegationId, dxEx));
        }
        return Future.failedFuture(dxEx);
      });
  }

  @Override
  public Future<DelegationUpdateRequest> createDelegationRequest(DelegationUpdateRequest delegationRequest,Set<String>userRoles,List<JsonObject> constraintsJson,UUID delegatorRgId) {
    JsonObject delegationRequestBody = delegationRequest.toJson();

    return getDelegationGrantById(delegationRequest.delegationId())
      .compose(ar->
      {
        UUID delegatorDgId = ar.delegatorId();
        LocalDateTime globalExpiryTime = ar.expiryAt();

        LOGGER.info("delegator id , reviewer id:{}",delegatorDgId,delegatorRgId);

//        if(delegatorId!=delegationRequest.delegatorId())
//        {
//          throw new DxBadRequestException("The reviewer/delegator id for the delegation id dont match!");
//        }

        if(delegationRequest.requestedExpiry().isAfter(globalExpiryTime))
        {
          throw new DxBadRequestException("Pls make sure expiry time is lesser than the global expiry time");
        }

        return Future.succeededFuture();

      })
      .compose(ar->delegationValidator.validateAllConstraints(userRoles,constraintsJson))
      .compose(v -> delegationValidator.validateEntityOwnership(delegatorRgId , userRoles, constraintsJson))
      .compose(map-> delegationRequestDAO.create(delegationRequest))
      .recover(err -> Future.failedFuture(BaseDxException.from(err)));
  }

  @Override
  public Future<DelegationUpdateRequest> updateDelegationRequestStatus(UUID requestId, String status, UUID delegatorId) {

    LOGGER.info("Updating delegation request status for requestId: {}", requestId);

    return delegationRequestDAO.get(requestId)
      .compose(existingRequest -> {
        if (existingRequest == null) {
          return Future.failedFuture(new DxNotFoundException("Delegation request not found"));
        }


        Map<String, Object> conditionMap = Map.of("request_id", requestId.toString());
        Map<String, Object> updateMap = Map.of(
          "status", status,
          "reviewed_at", LocalDateTime.now().toString()
        );

        return delegationRequestDAO.update(conditionMap, updateMap)
          .compose(updatedRequest -> {
            if ("approved".equalsIgnoreCase(status) && existingRequest.requestedScopes() != null) {
              List<JsonObject> constraintsJson = existingRequest.requestedScopes()
                .stream()
                .map(o -> (JsonObject) o)
                .toList();
              return insertScopeConstraints(existingRequest.delegationId(), constraintsJson)
                .map(v -> updatedRequest);
            }
            return Future.succeededFuture(updatedRequest);
          });
      });
  }

  @Override
  public Future<List<DelegationScopeConstraint>> getAllDelegationScopeConstraints(UUID itemId) {

    Map<String,Object> mp = Map.of("entity_id",itemId.toString());

    return delegationScopeConstraintDAO
      .getAllWithFilters(mp)
      .compose(all -> {
        if (all == null || all.isEmpty()) {
          LOGGER.warn("No delegation scope constraints found");
          return Future.succeededFuture(Collections.emptyList());
        }
        return Future.succeededFuture(all);
      });
  }



  @Override
  public Future<List<DelegationUpdateRequest>> getDelegationRequestsByUser(UUID userId) {

    Map<String,Object> conditionMap = Map.of("delegator_id",userId.toString());

    return delegationRequestDAO.getAllWithFilters(conditionMap)
      .recover(err -> {
        BaseDxException dxEx = BaseDxException.from(err);
        if (dxEx instanceof DxNotFoundException) {
          return Future.failedFuture(new DxNotFoundException("No delegation request found with userId as reviewer " + userId, dxEx));
        }
        return Future.failedFuture(dxEx);
      });

  }

  @Override
  public Future<List<DelegationUpdateRequest>> getDelegationRequestsByDelegationId(UUID delegationId) {

    Map<String,Object> conditionMap = Map.of("delegation_id",delegationId.toString());

    return delegationRequestDAO.getAllWithFilters(conditionMap)
      .recover(err -> {
        BaseDxException dxEx = BaseDxException.from(err);
        if (dxEx instanceof DxNotFoundException) {
          return Future.failedFuture(new DxNotFoundException("No delegation request found for delegationId" + delegationId, dxEx));
        }
        return Future.failedFuture(dxEx);
      });

  }

  @Override
  public Future<List<DelegationScopeConstraint>> getDelegationScopeConstraints(UUID delegationId) {
    Map<String,Object> conditionMap = Map.of("delegation_id",delegationId.toString());

    return delegationScopeConstraintDAO.getAllWithFilters(conditionMap)
      .recover(err -> {
        BaseDxException dxEx = BaseDxException.from(err);
        if (dxEx instanceof DxNotFoundException) {
          return Future.failedFuture(new DxNotFoundException("No delegation request found for delegationId" + delegationId, dxEx));
        }
        return Future.failedFuture(dxEx);
      });
  }


  private Future<Void> insertScopeConstraints(UUID delegationId, List<JsonObject> constraints) {
    List<Future> insertionFutures = new ArrayList<>();

    for (JsonObject constraint : constraints) {
      String scope = constraint.getString("scope");
      JsonArray entityArray = constraint.getJsonArray("entity_id", new JsonArray());
      String expiryAt = constraint.getString("expiry_at");

      // Case 1: No entity IDs (like cos_admin_access)
      if (entityArray.isEmpty()) {
        JsonObject dbEntry = new JsonObject()
          .put("delegation_id", delegationId.toString())
          .put("scope", scope)
          .put("expiry_at", expiryAt);

        DelegationScopeConstraint delegationScopeConstraint = DelegationScopeConstraint.fromJson(dbEntry);

        insertionFutures.add(
          delegationScopeConstraintDAO.create(delegationScopeConstraint)
            .recover(err -> {
              LOGGER.error("Failed to insert constraint for scope {} with NULL entity_id", scope, err);
              return Future.failedFuture(new DxPgException("Failed to insert delegation constraint", err));
            })
        );

      } else {
        // Case 2: One or more entity IDs
        List<String> entityIds = entityArray.stream()
          .map(Object::toString)
          .toList();

        for (String entityId : entityIds) {
          JsonObject dbEntry = new JsonObject()
            .put("delegation_id", delegationId.toString())
            .put("scope", scope)
            .put("entity_id", entityId)
            .put("expiry_at", expiryAt);

          DelegationScopeConstraint delegationScopeConstraint = DelegationScopeConstraint.fromJson(dbEntry);

          insertionFutures.add(
            delegationScopeConstraintDAO.create(delegationScopeConstraint)
              .recover(err -> {
                LOGGER.error("Failed to insert constraint for scope {} and entity {}", scope, entityId, err);
                return Future.failedFuture(new DxPgException("Failed to insert delegation constraint", err));
              })
          );
        }
      }
    }

    return CompositeFuture.all(insertionFutures).mapEmpty();
  }



}
