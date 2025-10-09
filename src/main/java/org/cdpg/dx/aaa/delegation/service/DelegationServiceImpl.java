package org.cdpg.dx.aaa.delegation.service;

import com.hazelcast.internal.networking.HandlerStatus;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.credit.dao.*;
import org.cdpg.dx.aaa.delegation.dao.*;
import org.cdpg.dx.aaa.delegation.dao.impl.ScopeConstraintDAOImpl;
import org.cdpg.dx.aaa.delegation.models.DelegationGrant;
import org.cdpg.dx.aaa.delegation.models.DelegationScopeConstraint;
import org.cdpg.dx.aaa.delegation.models.DelegationUpdateRequest;
import org.cdpg.dx.aaa.delegation.util.RoleScopeMapping;
import org.cdpg.dx.aaa.organization.models.OrganizationUser;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.common.exception.*;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;

import static org.cdpg.dx.aaa.delegation.util.Constants.REQUEST_ID;
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

  public DelegationServiceImpl(DelegationDAOFactory factory, KeycloakUserService keycloakUserService, OrganizationService organizationService) {
    this.delegationGrantDAO = factory.delegationGrantDAO();
    this.delegationRequestDAO = factory.delegationRequestDAO();
    this.scopeConstraintDAO = factory.scopeConstraintDAO();
    this.tokenDAO = factory.tokenDAO();
    this.delegationScopeConstraintDAO = factory.scopeConstraintDAO();
    this.organizationService = organizationService;
  }


  @Override
  public Future<DelegationGrant> createDelegationGrant(DelegationGrant delegationGrant, Set<String>userRoles,List<JsonObject> constraintsJson) {

    LOGGER.info("ServiceImplementation of createDelegationGrant");

    JsonObject delegationGrantBody = delegationGrant.toJson();



    if (constraintsJson.isEmpty()) {
      return Future.failedFuture(new DxBadRequestException("No constraints provided for delegation grant"));
    }

    LocalDateTime globalExpiry = parseDateTime(delegationGrantBody.getString("expiry_at"));

    return validateAllConstraints(userRoles,constraintsJson)
        .compose(map-> delegationGrantDAO.create(delegationGrant))
        .compose(createdGrant -> insertScopeConstraints(createdGrant.delegationId(), constraintsJson).map(v->createdGrant))
        .recover(err -> Future.failedFuture(BaseDxException.from(err)));

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
  public Future<DelegationUpdateRequest> createDelegationRequest(DelegationUpdateRequest delegationRequest,Set<String>userRoles,List<JsonObject> constraintsJson) {
    JsonObject delegationRequestBody = delegationRequest.toJson();



    return getDelegationGrantById(delegationRequest.delegationId())
      .compose(ar->
      {
        UUID delegatorId = ar.delegatorId();
        LocalDateTime globalExpiryTime = ar.expiryAt();

        if(delegatorId!=delegationRequest.reviewerId())
        {
          throw new DxBadRequestException("The reviewer/delegator id for the delegation id dont match!");
        }

        if(delegationRequest.requestedExpiry().isAfter(globalExpiryTime))
        {
          throw new DxBadRequestException("Pls make sure expiry time is lesser than the global expiry time");
        }

        return Future.succeededFuture();

      })
      .compose(ar->validateAllConstraints(userRoles,constraintsJson))
      .compose(map-> delegationRequestDAO.create(delegationRequest))
      .compose(createdRequest -> insertScopeConstraints(createdRequest.delegationId(), constraintsJson).map(v->createdRequest))
      .recover(err -> Future.failedFuture(BaseDxException.from(err)));
  }

  @Override
  public Future<DelegationUpdateRequest> updateDelegationRequestStatus(UUID requestId, String status, UUID delegatorId) {
    return delegationRequestDAO.get(requestId)
      .compose(existingRequest -> {
        if (existingRequest == null) {
          return Future.failedFuture(new DxNotFoundException("Delegation request not found"));
        }

        if (!existingRequest.reviewerId().equals(delegatorId)) {
          return Future.failedFuture(new DxUnauthorizedException("Reviewer not authorized to approve/reject this request"));
        }

        Map<String, Object> conditionMap = Map.of(REQUEST_ID, requestId.toString());
        Map<String, Object> updateMap = Map.of(
          STATUS, status,
          REVIEWED_AT, LocalDateTime.now().toString()
        );

        return delegationRequestDAO.update(conditionMap, updateMap)
          .compose(updatedDelegateRequest -> {
            if (status.equalsIgnoreCase("APPROVED")) {

              JsonObject delegateRequestBody = updatedDelegateRequest.toJson();
              List<JsonObject> constraintsJson = delegateRequestBody.getJsonArray("requested_scopes", new JsonArray())
                .stream()
                .map(o -> (JsonObject) o)
                .toList();

              return insertScopeConstraints(existingRequest.delegationId(), constraintsJson)
                .map(v -> updatedDelegateRequest);
            }
            return Future.succeededFuture(updatedDelegateRequest);
          });
      })
      .recover(dxEx -> {
        if (dxEx instanceof NoRowFoundException) {
          return Future.failedFuture(
            new DxNotFoundException("No matching requestId found in delegation_request table", dxEx)
          );
        } else if (dxEx instanceof DxUnauthorizedException) {
          return Future.failedFuture(
            new DxUnauthorizedException("Reviewer not authorized to update this request", dxEx)
          );
        } else if (dxEx.getMessage().contains("duplicate key")) {
          return Future.failedFuture(
            new DxConflictException("Duplicate record insertion attempt", dxEx)
          );
        } else if (dxEx.getMessage().contains("invalid input syntax")) {
          return Future.failedFuture(
            new DxBadRequestException("Invalid data format for request fields", dxEx)
          );
        } else {
          LOGGER.error("Unexpected error while updating delegation request");
          return Future.failedFuture(dxEx);
        }
      });
  }



  @Override
  public Future<DelegationUpdateRequest> getDelegationRequestsByUser(UUID userId) {


    return delegationRequestDAO.get(userId)
      .recover(err -> {
        BaseDxException dxEx = BaseDxException.from(err);
        if (dxEx instanceof DxNotFoundException) {
          return Future.failedFuture(new DxNotFoundException("No delegation request found with userId as reviewer " + userId, dxEx));
        }
        return Future.failedFuture(dxEx);
      });

  }

  private Future<Void> validateAllConstraints(
    Set<String> delegatorRoles,
    List<JsonObject> constraintsJson) {

    // Step 1: COS_ADMIN can delegate anything
    if (delegatorRoles.contains("cos_admin")) {
      return Future.succeededFuture();
    }

    // Step 2: Determine the delegator's highest role
    String delegatorRole = getHighestRole(delegatorRoles);

    // Step 3: Extract all requested scopes from constraints
    List<String> requestedScopes = constraintsJson.stream()
      .map(c -> c.getString("scope"))
      .map(String::toLowerCase)
      .toList();

    // Step 4: Get delegator’s allowed scopes
    RoleScopeMapping delegatorMapping = RoleScopeMapping.fromString(delegatorRole);
    List<String> allowedScopes = delegatorMapping.getAllowedScopes();

    // Step 5: Validate that all requested scopes are within allowed set
    for (String scope : requestedScopes) {
      if (!allowedScopes.contains(scope)) {
        return Future.failedFuture(
          new DxForbiddenException(
            String.format(
              "Delegator with role '%s' cannot delegate unauthorized or higher scope '%s'",
              delegatorRole, scope)
          )
        );
      }
    }

    // Step 6: All validations passed
    return Future.succeededFuture();
  }


  private Future<Void> insertScopeConstraints(UUID delegationId, List<JsonObject> constraints) {
    List<Future> insertionFutures = new ArrayList<>();

    for (JsonObject constraint : constraints) {
      String scope = constraint.getString("scope");
      List<String> entityIds = constraint.getJsonArray("entity_id")
        .stream()
        .map(Object::toString)
        .toList();

      String expiryAt = constraint.getString("expiry_at");

      for (String entityId : entityIds) {
        JsonObject dbEntry = new JsonObject()
          .put("delegation_id", delegationId.toString())
          .put("scope", scope)
          .put("resource_id", entityId)
          .put("expiry_at", expiryAt);

        DelegationScopeConstraint delegationScopeConstraint = DelegationScopeConstraint.fromJson(dbEntry);

        insertionFutures.add(delegationScopeConstraintDAO.create(delegationScopeConstraint)
          .recover(err -> {
            LOGGER.error("Failed to insert constraint for scope {} and entity {}", scope, entityId, err);
            return Future.failedFuture(new DxPgException("Failed to insert delegation constraint", err));
          })
        );
      }
    }

    return CompositeFuture.all(insertionFutures)
      .mapEmpty();
  }

  private String getHighestRole(Set<String> roles) {
    return roles.stream()
      .max(Comparator.comparingInt(RoleScopeMapping::getRoleRank))
      .orElseThrow(() -> new DxBadRequestException("No valid role found for user"));
  }




}
