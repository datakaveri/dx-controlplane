package org.cdpg.dx.aaa.delegation.service;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import org.cdpg.dx.aaa.delegation.DelegationValidator;
import org.cdpg.dx.aaa.delegation.dao.*;
import org.cdpg.dx.aaa.delegation.models.DelegationGrant;
import org.cdpg.dx.aaa.delegation.models.DelegationScopeConstraint;
import org.cdpg.dx.aaa.delegation.models.DelegationUpdateRequest;
import org.cdpg.dx.aaa.delegation.util.RoleScopeMapping;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.organization.models.Role;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.auth.authorization.model.DxRole;
import org.cdpg.dx.auth.authorization.model.DxScope;
import org.cdpg.dx.common.exception.*;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.util.DateTimeHelper;
import org.cdpg.dx.database.postgres.models.PaginatedResult;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.cdpg.dx.aaa.appCredentials.util.Constants.*;
import static org.cdpg.dx.aaa.delegation.util.Constants.*;
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
  public Future<DelegationGrant> createDelegationGrant(
    DelegationGrant delegationGrant,
    Set<String> delegatorRoles,
    JsonArray roleConstraints
  ) {
    LOGGER.info("ServiceImplementation of createDelegationGrant");

    JsonObject body = delegationGrant.toJson();
    LOGGER.info("Delegation Grant body is {}",body);

    UUID delegatorId = UUID.fromString(body.getString(DELEGATOR_ID));


    boolean isWildcardDelegation = (roleConstraints == null || roleConstraints.isEmpty());
    LOGGER.info("roles is {}",roleConstraints);
    String highestRole = getHighestRole(delegatorRoles);


    Future<DelegationGrant> flow;

    if (isWildcardDelegation) {
      flow = delegationGrantDAO.create(delegationGrant)
        .compose(created ->
          insertWildcardConstraint(created.delegationId(), highestRole, delegationGrant.expiryAt())
            .map(v -> created)
        );

    } else {
      flow =
        delegationValidator.validateAllConstraints(body, delegatorRoles)
          .compose(v -> delegationValidator.validateEntityOwnership(body, delegatorRoles))
          .compose(v -> delegationGrantDAO.create(delegationGrant))
          .compose(created ->
            insertScopeConstraints(created.delegationId(),roleConstraints).map(v -> created)
          );
    }

    return flow
      .compose(created ->
        keycloakUserService.addRoleToUser(created.delegateId(), DxRole.DELEGATE)
          .map(v -> created)
      )
      .compose(created ->
        publishScopesToKeycloak(created, roleConstraints,highestRole)
      )
      .recover(err -> Future.failedFuture(BaseDxException.from(err)));
  }

  private Future<Void> insertWildcardConstraint(
    UUID delegationId,
    String role,
    LocalDateTime expiry
  ) {

    LOGGER.info("Inside wildcardConstraint");
    JsonObject row = new JsonObject()
      .put("delegation_id", delegationId.toString())
      .put("role", role)
      .put("scope", "*")
      .put("entity_id", null)
      .put("entity_type", null)
      .put("expiry_at", expiry);

    DelegationScopeConstraint constraint =
      DelegationScopeConstraint.fromJson(row);

    return delegationScopeConstraintDAO
      .create(constraint)
      .mapEmpty();
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
  public Future<List<DelegationScopeConstraint>>getDelegationScopeByEntityId(UUID entityId) {

    Map<String,Object> filter = Map.of(ENTITY_ID,entityId.toString());
    return delegationScopeConstraintDAO.getAllWithFilters(filter)
      .recover(err -> {
        BaseDxException dxEx = BaseDxException.from(err);
        if (dxEx instanceof DxNotFoundException) {
          return Future.failedFuture(new DxNotFoundException("No delegation grant found with id " + entityId, dxEx));
        }
        return Future.failedFuture(dxEx);
      });
  }

//  @Override
//  public Future<DelegationUpdateRequest> createDelegationRequest(DelegationUpdateRequest delegationRequest,Set<String>userRoles,List<JsonObject> constraintsJson,UUID delegatorRgId) {
//    JsonObject delegationRequestBody = delegationRequest.toJson();
//
//    return getDelegationGrantById(delegationRequest.delegationId())
//      .compose(ar->
//      {
//        UUID delegatorDgId = ar.delegatorId();
//        LocalDateTime globalExpiryTime = ar.expiryAt();
//
//        LOGGER.info("delegator id , reviewer id:{}",delegatorDgId,delegatorRgId);
//
////        if(delegatorId!=delegationRequest.delegatorId())
////        {
////          throw new DxBadRequestException("The reviewer/delegator id for the delegation id dont match!");
////        }
//
//        if(delegationRequest.requestedExpiry().isAfter(globalExpiryTime))
//        {
//          throw new DxBadRequestException("Pls make sure expiry time is lesser than the global expiry time");
//        }
//
//        return Future.succeededFuture();
//
//      })
//      .compose(ar->delegationValidator.validateAllConstraints(userRoles,delegationRequestBody))
//      .compose(v -> delegationValidator.validateEntityOwnership(delegatorRgId , userRoles, constraintsJson))
//      .compose(map-> delegationRequestDAO.create(delegationRequest))
//      .recover(err -> Future.failedFuture(BaseDxException.from(err)));
//  }

//  @Override
//  public Future<DelegationUpdateRequest> updateDelegationRequestStatus(UUID requestId, String status, UUID delegatorId) {
//
//    LOGGER.info("Updating delegation request status for requestId: {}", requestId);
//
//    return delegationRequestDAO.get(requestId)
//      .compose(existingRequest -> {
//        if (existingRequest == null) {
//          return Future.failedFuture(new DxNotFoundException("Delegation request not found"));
//        }
//
//
//        Map<String, Object> conditionMap = Map.of("request_id", requestId.toString());
//        Map<String, Object> updateMap = Map.of(
//          "status", status,
//          "reviewed_at", LocalDateTime.now().toString()
//        );
//
//        return delegationRequestDAO.update(conditionMap, updateMap)
//          .compose(updatedRequest -> {
//            if ("approved".equalsIgnoreCase(status) && existingRequest.requestedScopes() != null) {
//              List<JsonObject> constraintsJson = existingRequest.requestedScopes()
//                .stream()
//                .map(o -> (JsonObject) o)
//                .toList();
//              return insertScopeConstraints(existingRequest.delegationId(), constraintsJson)
//                .map(v -> updatedRequest);
//            }
//            return Future.succeededFuture(updatedRequest);
//          });
//      });
//  }


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
  public Future<List<DelegationGrant>> getAllDelegationsByDelegator(UUID userId) {

    Map<String,Object> conditionMap = Map.of(DELEGATOR_ID,userId.toString());

    return delegationGrantDAO.getAllWithFilters(conditionMap)
      .recover(err -> {
        BaseDxException dxEx = BaseDxException.from(err);
        if (dxEx instanceof DxNotFoundException) {
          return Future.failedFuture(new DxNotFoundException("No delegation found with userId as delegator " + userId, dxEx));
        }
        return Future.failedFuture(dxEx);
      });

  }

  @Override
  public Future<List<DelegationGrant>> getAllDelegationsOfDelegate(UUID userId) {

    Map<String,Object> conditionMap = Map.of(DELEGATE_ID, userId.toString());

    return delegationGrantDAO.getAllWithFilters(conditionMap)
      .recover(err -> {
        BaseDxException dxEx = BaseDxException.from(err);
        if (dxEx instanceof DxNotFoundException) {
          return Future.failedFuture(new DxNotFoundException("No delegation found with userId as delegate " + userId, dxEx));
        }
        return Future.failedFuture(dxEx);
      });

  }


  @Override
  public Future<Boolean> deleteDelegation(UUID delegationId, UUID userId) {

    return delegationGrantDAO.get(delegationId).compose(delegationGrant -> {

      if (!delegationGrant.delegatorId().equals(userId)) {
        return Future.failedFuture(
          new DxForbiddenException(
            "This user cannot delete the delegation as it is not the delegator"
          )
        );
      }

      Map<String, Object> filter =
        Map.of(DELEGATION_ID, delegationId.toString());

      return delegationScopeConstraintDAO.getAllWithFilters(filter)
        .compose(constraints -> {

          Set<String> scopesToRemove = new HashSet<>();

          for (DelegationScopeConstraint c : constraints) {
            if (c.scope() != null && !c.scope().isBlank()) {
              if ("*".equals(c.scope())) {
                RoleScopeMapping mapping =
                  RoleScopeMapping.fromString(c.role().toString());
                scopesToRemove.addAll(mapping.getAllowedScopes());
              } else {
                scopesToRemove.add(c.scope());
              }
            }
          }

          LOGGER.info(
            "Removing scopes {} for delegation {}",
            scopesToRemove,
            delegationId
          );

          return keycloakUserService.clearDelegationScopes(
            delegationGrant.delegateId(),
            delegationGrant.delegatorId(),
            scopesToRemove
          );
        })
        .compose(v -> delegationGrantDAO.delete(delegationId))
        .onSuccess(v ->
          LOGGER.info(
            "Delegation {} deleted successfully by user {}",
            delegationId,
            userId
          )
        )
        .map(v -> true);
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


  private Future<Void> insertScopeConstraints(
    UUID delegationId,
    JsonArray roles
  ) {

    if (roles == null || roles.isEmpty()) {
      return Future.succeededFuture();
    }

    List<Future> insertFutures = new ArrayList<>();

    for (Object roleObj : roles) {
      JsonObject roleJson = (JsonObject) roleObj;

      String role = roleJson.getString("role");
      JsonArray constraints =
        roleJson.getJsonArray("constraints", new JsonArray());

      for (Object constraintObj : constraints) {
        JsonObject constraint = (JsonObject) constraintObj;

        JsonObject dbRow = new JsonObject()
          .put("delegation_id", delegationId.toString())
          .put("role", role)                         // ✅ correct key
          .put("scope", constraint.getString("scope"))
          .put("expiry_at", constraint.getString("expiry_at"))
          .put(
            "entity_id",
            constraint.getString("entity_id") != null
              ? constraint.getString("entity_id")
              : null
          )
          .put( "entity_type",
            constraint.getString("entity_type") != null
              ? constraint.getString("entity_type")
              : null
          );

        DelegationScopeConstraint delegationScopeConstraint =
          DelegationScopeConstraint.fromJson(dbRow);

        insertFutures.add(
          delegationScopeConstraintDAO.create(delegationScopeConstraint)
        );
      }
    }

    return CompositeFuture.all(insertFutures).mapEmpty();
  }


//  public Set<String> extractRoles(User user) {
//    Set<String> roles = new HashSet<>();
//    JsonObject principal = user.principal();
//    if (principal.containsKey("realm_access")) {
//      JsonObject realmAccess = principal.getJsonObject("realm_access");
//      if (realmAccess.containsKey("roles")) {
//        roles.addAll(realmAccess.getJsonArray("roles").getList());
//      }
//    }
//    return roles;
//  }

  private String getHighestRole(Set<String> roles) {
    if (roles.contains("cos_admin")) return "cos_admin";
    if (roles.contains("org_admin")) return "org_admin";
    if (roles.contains("provider")) return "provider";
    return "consumer";
  }

  private Future<DelegationGrant> publishScopesToKeycloak(
    DelegationGrant created,
    JsonArray roles,
    String highestRole
  ) {

    // -------------------- WILDCARD --------------------
    if (roles == null || roles.isEmpty()) {

      RoleScopeMapping roleMapping =
        RoleScopeMapping.fromString(highestRole);

      LOGGER.info(
        "Wildcard delegation detected, expanding scopes for role: {} and scopes: {}",
        roleMapping.getRole(),
        roleMapping.getAllowedScopes()
      );

      List<String> scopes =
        roleMapping.getAllowedScopes()
          .stream()
          .toList();

      return keycloakUserService
        .setDelegationScopes(
          created.delegateId(),
          scopes,
          created.delegatorId()
        )
        .map(v -> created);
    }

    // -------------------- EXPLICIT SCOPES --------------------
    List<String> scopes = new ArrayList<>();

    for (Object r : roles) {
      JsonObject roleObj = (JsonObject) r;
      JsonArray constraints =
        roleObj.getJsonArray("constraints", new JsonArray());

      for (Object c : constraints) {
        String scope = ((JsonObject) c).getString("scope");
        scopes.add(scope);
      }
    }

    return keycloakUserService
      .setDelegationScopes(
        created.delegateId(),
        scopes,
        created.delegatorId()
      )
      .map(v -> created);
  }





}
