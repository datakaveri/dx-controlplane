

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
import org.cdpg.dx.aaa.delegation.util.DelegationRole;
import org.cdpg.dx.aaa.delegation.util.RoleScopeMapping;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.organization.models.Role;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.auth.authorization.model.DxRole;
import org.cdpg.dx.auth.authorization.model.DxScope;
import org.cdpg.dx.common.exception.*;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.util.DateTimeHelper;
import org.cdpg.dx.common.util.ServiceErrorHelper;
import org.cdpg.dx.database.postgres.models.PaginatedResult;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.cdpg.dx.aaa.appCredentials.util.Constants.*;
import static org.cdpg.dx.aaa.delegation.util.Constants.*;
import static org.cdpg.dx.aaa.delegation.util.Constants.ENTITY_ID;
import static org.cdpg.dx.common.util.DateTimeHelper.FORMATTER;
import static org.cdpg.dx.common.util.DateTimeHelper.parseDateTime;


public class DelegationServiceImpl implements DelegationService{

  private static final Logger LOGGER = LoggerFactory.getLogger(DelegationServiceImpl.class);

  private final DelegationGrantDAO delegationGrantDAO;
  private final DelegationRequestDAO delegationRequestDAO;
  private final ScopeConstraintDAO scopeConstraintDAO;
  private final OrganizationService organizationService;
  private final TokenDAO tokenDAO;
  private final KeycloakUserService keycloakUserService;
  private final DelegationValidator delegationValidator;
  private final ItemService itemService;

  public DelegationServiceImpl(DelegationDAOFactory factory, KeycloakUserService keycloakUserService, OrganizationService organizationService, ItemService itemService) {
    this.delegationGrantDAO = factory.delegationGrantDAO();
    this.delegationRequestDAO = factory.delegationRequestDAO();
    this.scopeConstraintDAO = factory.scopeConstraintDAO();
    this.tokenDAO = factory.tokenDAO();
    this.organizationService = organizationService;
    this.keycloakUserService = keycloakUserService;
    this.itemService = itemService;
    this.delegationValidator = new DelegationValidator(organizationService, itemService);
  }


  @Override
  public Future<JsonObject> createDelegationGrant(
    JsonObject delegationGrantBody,
    Set<String> delegatorRoles,
    JsonArray roleConstraints
  ) {
    LOGGER.info("Creating delegation grant: {}", delegationGrantBody);

    UUID delegatorId = UUID.fromString(delegationGrantBody.getString(DELEGATOR_ID));

    DelegationGrant delegationGrant = DelegationGrant.fromJson(delegationGrantBody);


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
      flow = delegationValidator.validateEntityOwnership(delegationGrantBody, delegatorRoles, roleConstraints)
          .compose(v -> delegationGrantDAO.create(delegationGrant))
          .compose(created ->
            insertScopeConstraints(created.delegationId(),roleConstraints,delegationGrant.expiryAt()).map(v -> created)
          );
    }

    return flow
      .compose(created ->
        keycloakUserService.addRoleToUser(created.delegateId(), DxRole.DELEGATE)
          .map(v -> created)
      )
      .compose(created ->
        publishScopesToKeycloak(created, roleConstraints,highestRole).map(DelegationGrant::toJson)
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
      .put("entity_id", "*")
      .put("entity_type", "*")
      .put("expiry_at", expiry != null ? expiry.format(FORMATTER) : null);

    DelegationScopeConstraint constraint =
      DelegationScopeConstraint.fromJson(row);

    return scopeConstraintDAO
      .create(constraint)
      .mapEmpty();
  }




  @Override
  public Future<JsonObject> getDelegationGrantById(String delegationIdStr) {

    UUID delegationId = UUID.fromString(delegationIdStr);
    return delegationGrantDAO.get(delegationId).map(DelegationGrant::toJson)
      .recover(ServiceErrorHelper.mapNotFound("No delegation grant found with id " + delegationId));
  }

  @Override
  public Future<List<JsonObject>>getDelegationScopeByEntityId(String entityIdStr) {


    Map<String,Object> filter = Map.of(ENTITY_ID,entityIdStr);
    return scopeConstraintDAO.getAllWithFilters(filter).map(v->v.stream().map(DelegationScopeConstraint::toJson).toList())
      .recover(ServiceErrorHelper.mapNotFound("No delegation grant found with id " + entityIdStr));
  }

  @Override
  public Future<List<JsonObject>> getAllDelegationScopeConstraints(String itemIdStr) {

    Map<String,Object> mp = Map.of("entity_id",itemIdStr);

    return scopeConstraintDAO
      .getAllWithFilters(mp).map(v->v.stream().map(DelegationScopeConstraint::toJson).toList())
      .compose(all -> {
        if (all == null || all.isEmpty()) {
          LOGGER.warn("No delegation scope constraints found");
          return Future.succeededFuture(Collections.emptyList());
        }
        return Future.succeededFuture(all);
      });
  }


  @Override
  public Future<List<JsonObject>> getAllDelegationsByDelegator(String userIdStr) {

    Map<String, Object> conditionMap = Map.of(DELEGATOR_ID, userIdStr);

    return delegationGrantDAO.getAllWithFilters(conditionMap)
      .compose(delegations -> {
        List<Future<JsonObject>> enrichedFutures = delegations.stream()
          .map(delegation -> {
            UUID delegationId = delegation.delegationId();
            Map<String, Object> scopeCondition = Map.of("delegation_id", delegationId.toString());

            return scopeConstraintDAO.getAllWithFilters(scopeCondition)
              .map(constraints -> {
                JsonObject delegationJson = delegation.toJson();
                JsonArray constraintsArray = new JsonArray(
                  constraints.stream()
                    .map(c -> {
                      JsonObject json = c.toJson();
                      json.remove("id");
                      json.remove("delegation_id");
                      return json;
                    })
                    .toList()
                );
                delegationJson.put("constraints", constraintsArray);
                return delegationJson;
              })
              .recover(err -> {
                JsonObject delegationJson = delegation.toJson();
                delegationJson.put("constraints", new JsonArray());
                return Future.succeededFuture(delegationJson);
              });
          })
          .toList();

        return Future.all(enrichedFutures)
          .map(cf -> cf.<JsonObject>list());
      })
      .recover(err -> {
        BaseDxException dxEx = BaseDxException.from(err);
        if (dxEx instanceof DxNotFoundException) {
          return Future.succeededFuture(List.of());
        }
        return Future.failedFuture(dxEx);
      });
  }

  @Override
  public Future<List<JsonObject>> getAllDelegationsOfDelegate(String userIdStr) {

    Map<String, Object> conditionMap = Map.of(DELEGATE_ID, userIdStr);

    return delegationGrantDAO.getAllWithFilters(conditionMap)
      .compose(delegations -> {
        List<Future<JsonObject>> enrichedFutures = delegations.stream()
          .map(delegation -> {
            UUID delegationId = delegation.delegationId();
            Map<String, Object> scopeCondition = Map.of("delegation_id", delegationId.toString());

            return scopeConstraintDAO.getAllWithFilters(scopeCondition)
              .map(constraints -> {
                JsonObject delegationJson = delegation.toJson();
                JsonArray constraintsArray = new JsonArray(
                  constraints.stream()
                    .map(c -> {
                      JsonObject json = c.toJson();
                      json.remove("id");
                      json.remove("delegation_id");
                      return json;
                    })
                    .toList()
                );
                delegationJson.put("constraints", constraintsArray);
                return delegationJson;
              })
              .recover(err -> {
                // If no constraints found, return delegation without constraints
                JsonObject delegationJson = delegation.toJson();
                delegationJson.put("constraints", new JsonArray());
                return Future.succeededFuture(delegationJson);
              });
          })
          .toList();

        return Future.all(enrichedFutures)
          .map(cf -> cf.<JsonObject>list());
      })
      .recover(err -> {
        BaseDxException dxEx = BaseDxException.from(err);
        if (dxEx instanceof DxNotFoundException) {
          // Return empty list instead of failing
          return Future.succeededFuture(List.of());
        }
        return Future.failedFuture(dxEx);
      });
  }


  @Override
  public Future<Boolean> deleteDelegation(String delegationIdStr, String userIdStr) {

  UUID delegationId = UUID.fromString(delegationIdStr);
  UUID userId = UUID.fromString(userIdStr);


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

      return scopeConstraintDAO.getAllWithFilters(filter)
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
  public Future<List<JsonObject>> getDelegationRequestsByDelegationId(String delegationIdStr) {

    Map<String,Object> conditionMap = Map.of("delegation_id",delegationIdStr);

    return delegationRequestDAO.getAllWithFilters(conditionMap).map(v->v.stream().map(DelegationUpdateRequest::toJson).toList())
      .recover(ServiceErrorHelper.mapNotFound("No delegation request found for delegationId " + delegationIdStr));

  }

  @Override
  public Future<List<JsonObject>> getDelegationScopeConstraints(String delegationIdStr) {
    Map<String,Object> conditionMap = Map.of("delegation_id",delegationIdStr);

    return scopeConstraintDAO.getAllWithFilters(conditionMap).map(v->v.stream().map(DelegationScopeConstraint::toJson).toList())
      .recover(ServiceErrorHelper.mapNotFound("No delegation scope constraints found for delegationId " + delegationIdStr));
  }


  private Future<Void> insertScopeConstraints(
    UUID delegationId,
    JsonArray roles,
    LocalDateTime expiryAt
  ) {

    if (roles == null || roles.isEmpty()) {
      return Future.succeededFuture();
    }

    List<Future> insertFutures = new ArrayList<>();

    for (Object roleObj : roles) {
      JsonObject roleJson = (JsonObject) roleObj;

      String role = roleJson.getString("role");
      JsonArray constraints =
        roleJson.getJsonArray("constraints",new JsonArray());

      if(constraints.isEmpty())
      {
        insertFutures.add(createScopes(delegationId, role,expiryAt));
        continue;
      }

      for (Object constraintObj : constraints) {
        JsonObject constraint = (JsonObject) constraintObj;
        String scope = constraint.getString("scope");
        LOGGER.info("constraints in delseviceImpl: {}" ,constraints.encode());

        JsonArray entityIds = constraint.getJsonArray("entity_id");

        //skipping cos_admin_access and compute_management because no entity check is needed for them
        if (entityIds != null && !entityIds.isEmpty()) {
          for (Object entity : entityIds) {
            insertFutures.add(createScopeConstraint(delegationId, role, constraint, entity)
            );
          }
        } else{
          // entity_id == null means entity_type is already null (validated)
          insertFutures.add(createScopeConstraint(delegationId, role, constraint, null)
          );
        }
      }
    }

    return CompositeFuture.all(insertFutures).mapEmpty();
  }

  private Future<Void> createScopeConstraint(
    UUID delegationId,
    String role,
    JsonObject constraint,
    Object entityId
  ) {

    JsonObject dbRow = new JsonObject()
      .put("delegation_id", delegationId.toString())
      .put("role", role)
      .put("scope", constraint.getString("scope")!=null
        ?constraint.getString("scope"):"*")
      .put("expiry_at", constraint.getString("expiry_at"))
      .put(
        "entity_id", entityId !=null ?
           entityId
          : "*")
      .put(
        "entity_type",
        constraint.getString("entity_type") != null
          ? constraint.getString("entity_type")
          : "*"
      );

    DelegationScopeConstraint delegationScopeConstraint =
      DelegationScopeConstraint.fromJson(dbRow);

    return scopeConstraintDAO
      .create(delegationScopeConstraint)
      .mapEmpty();
  }

  private Future<Void> createScopes(
    UUID delegationId,
    String role,
    LocalDateTime expiry
  ) {

    JsonObject dbRow = new JsonObject()
      .put("delegation_id", delegationId.toString())
      .put("role", role)
      .put("scope", "*")
      .put("expiry_at", expiry != null ? expiry.format(FORMATTER) : null)
      .put(
        "entity_id", "*")
      .put(
        "entity_type", "*"
      );

    DelegationScopeConstraint delegationScopeConstraint =
      DelegationScopeConstraint.fromJson(dbRow);

    return scopeConstraintDAO
      .create(delegationScopeConstraint)
      .mapEmpty();
  }

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
      String role = roleObj.getString("role");

      JsonArray constraints =
        roleObj.getJsonArray("constraints");

      if(constraints!=null)
      {
      for (Object c : constraints) {
        String scope = ((JsonObject) c).getString("scope");
        scopes.add(scope);
      }
      }
      else
        {
          RoleScopeMapping roleMapping =
            RoleScopeMapping.fromString(role);

         LOGGER.info(
            "No subset constraint found, expanding scopes for role: {} and scopes: {}",
            roleMapping.getRole(),
            roleMapping.getAllowedScopes()
          );

         scopes =
            roleMapping.getAllowedScopes()
              .stream()
              .toList();

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

  @Override
  public Future<JsonObject> checkItemAccess(String delegatorId, String delegateId) {

    Map<String, Object> filters = Map.of(
      DELEGATOR_ID, delegatorId,
      DELEGATE_ID, delegateId
    );

    return delegationGrantDAO.getAllWithFilters(filters)
      .compose(grants -> {

        if (grants.isEmpty()) {
          return Future.failedFuture(
            "Delegation doesn't exist for delegator and delegate"
          );
        }

        List<Future<List<DelegationScopeConstraint>>> scopeFutures =
          grants.stream()
            .map(grant -> {
              Map<String, Object> scopeFilter = Map.of(
                DELEGATION_ID, grant.delegationId().toString()
              );
              return scopeConstraintDAO.getAllWithFilters(scopeFilter);
            })
            .toList();

        return CompositeFuture.all(new ArrayList<>(scopeFutures))
          .map(cf -> {

            Set<String> allowedItems = new HashSet<>();

            for (int i = 0; i < cf.size(); i++) {
              List<DelegationScopeConstraint> scopes =
                cf.resultAt(i);

              for (DelegationScopeConstraint scope : scopes) {

                if ("*".equals(scope.scope())) {
                  return fullAccessResponse();
                }

                if ("data_access".equals(scope.scope())) {

                  if ("*".equals(scope.entityId())) {
                    return fullAccessResponse();
                  }

                  if ("item".equals(scope.entityType())) {
                    allowedItems.add(scope.entityId());
                  }
                }
              }
            }

            JsonObject response = new JsonObject();
            response.put("title", "Success");
            response.put("result", new ArrayList<>(allowedItems));
            return response;
          });
      });
  }


  private JsonObject fullAccessResponse() {
    return new JsonObject()
      .put("title", "Success")
      .put("result", List.of("*"));
  }


}
