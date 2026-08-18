package org.cdpg.dx.aaa.delegation.service;

import static org.cdpg.dx.aaa.appCredentials.util.Constants.*;
import static org.cdpg.dx.aaa.delegation.util.Constants.*;
import static org.cdpg.dx.aaa.delegation.util.Constants.ENTITY_ID;
import static org.cdpg.dx.common.util.DateTimeHelper.FORMATTER;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.time.LocalDateTime;
import java.util.*;
import org.cdpg.dx.aaa.delegation.DelegationValidator;
import org.cdpg.dx.aaa.delegation.UpdatedGrantResponse;
import org.cdpg.dx.aaa.delegation.dao.*;
import org.cdpg.dx.aaa.delegation.models.DelegationGrant;
import org.cdpg.dx.aaa.delegation.models.DelegationScopeConstraint;
import org.cdpg.dx.aaa.delegation.models.DelegationUpdateRequest;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.auth.authorization.registry.SystemRoleScopeMap;
import org.cdpg.dx.auth.model.DxRole;
import org.cdpg.dx.common.exception.*;
import org.cdpg.dx.common.util.ServiceErrorHelper;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DelegationServiceImpl implements DelegationService {

  private static final Logger LOGGER = LoggerFactory.getLogger(DelegationServiceImpl.class);

  private final DelegationGrantDAO delegationGrantDAO;
  private final DelegationRequestDAO delegationRequestDAO;
  private final ScopeConstraintDAO scopeConstraintDAO;
  private final OrganizationService organizationService;
  private final TokenDAO tokenDAO;
  private final KeycloakUserService keycloakUserService;
  private final DelegationValidator delegationValidator;
  private final ItemService itemService;

  public DelegationServiceImpl(
      DelegationDAOFactory factory,
      KeycloakUserService keycloakUserService,
      OrganizationService organizationService,
      ItemService itemService) {
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
  public Future<JsonObject> findActiveDelegation(String delegatorId, String delegateeId) {

    return delegationGrantDAO
        .findActiveDelegationWithScopes(delegatorId, delegateeId)
        .compose(
            rows -> {
              if (rows == null || rows.isEmpty()) {
                return Future.failedFuture(
                    new DxNotFoundException(
                        "No active delegation found for delegator "
                            + delegatorId
                            + " and delegatee "
                            + delegateeId));
              }
              LOGGER.debug("rows = {}", rows);
              JsonObject firstRow = rows.getJsonObject(0);
              JsonObject delegation =
                  new JsonObject()
                      .put("delegation_id", firstRow.getString("delegation_id"))
                      .put("delegator_id", firstRow.getString("delegator_id"))
                      .put("delegate_id", firstRow.getString("delegate_id"))
                      .put("justification", firstRow.getString("justification"))
                      .put("expiry_at", firstRow.getString("delegation_expiry_at"))
                      .put("status", firstRow.getString("status"))
                      .put("created_at", firstRow.getString("created_at"));

              JsonArray constraints = new JsonArray();
              Set<String> explicitScopes = new LinkedHashSet<>();
              boolean hasWildcard = false;

              for (int i = 0; i < rows.size(); i++) {
                JsonObject row = rows.getJsonObject(i);
                String role = row.getString("role");
                String scope = row.getString("scope");

                JsonObject constraint = new JsonObject();
                if (role != null) constraint.put("role", role);
                if (scope != null) {
                  constraint.put("scope", scope);
                  if ("*".equals(scope)) {
                    hasWildcard = true;
                  } else {
                    explicitScopes.add(scope);
                  }
                }
                if (row.getString("entity_id") != null)
                  constraint.put("entityId", row.getString("entity_id"));
                if (row.getString("entity_type") != null)
                  constraint.put("entityType", row.getString("entity_type"));
                if (row.getString("constraint_expiry_at") != null)
                  constraint.put("expiryAt", row.getString("constraint_expiry_at"));
                constraints.add(constraint);
              }

              delegation.put("constraints", constraints);
              final boolean wildcardPresent = hasWildcard;

              return keycloakUserService
                  .getUserById(UUID.fromString(delegatorId))
                  .map(
                      dxUser -> {
                        Set<String> cappedScopes = new LinkedHashSet<>(explicitScopes);

                        if (wildcardPresent && dxUser.roles() != null) {
                          // Wildcard: expand ALL of the delegator's actual roles (including default
                          // consumer)
                          for (String r : dxUser.roles()) {
                            DxRole.fromString(r)
                                .ifPresent(
                                    role ->
                                        cappedScopes.addAll(SystemRoleScopeMap.getScopes(role)));
                          }
                        }

                        JsonObject delegatorJson = dxUser.toJson();
                        delegatorJson.put("scopes", new JsonArray(new ArrayList<>(cappedScopes)));
                        delegation.put("delegator", delegatorJson);
                        return delegation;
                      });
            })
        .recover(err -> Future.failedFuture(BaseDxException.from(err)));
  }

  @Override
  public Future<JsonObject> createDelegationGrant(
      JsonObject delegationGrantBody,
      List<String> delegatorRoles,
      JsonArray roleConstraints,
      String orgId) {
    LOGGER.info("Creating delegation grant: {}", delegationGrantBody);

    UUID delegatorId = UUID.fromString(delegationGrantBody.getString("delegatorId"));
    UUID delegateId = UUID.fromString(delegationGrantBody.getString("delegateId"));

    DelegationGrant delegationGrant = DelegationGrant.fromRequestJson(delegationGrantBody);

    boolean isWildcardDelegation = (roleConstraints == null || roleConstraints.isEmpty());

    LOGGER.info("roles is {}", roleConstraints);
    String highestRole = getHighestRole(delegatorRoles);

    Future<DelegationGrant> flow;

    if (isWildcardDelegation) {
      flow =
          delegationGrantDAO
              .create(delegationGrant)
              .compose(
                  created ->
                      insertWildcardConstraint(
                              created.delegationId(), highestRole, delegationGrant.expiryAt())
                          .map(v -> created));

    } else {
      flow =
          delegationValidator
              .validateEntityOwnership(delegationGrantBody, UUID.fromString(orgId), roleConstraints)
              .compose(v -> delegationGrantDAO.create(delegationGrant))
              .compose(
                  created ->
                      insertScopeConstraints(
                              created.delegationId(), roleConstraints, delegationGrant.expiryAt())
                          .map(v -> created));
    }

    return flow.compose(
            created ->
                getDelegationScopeConstraints(created.delegationId().toString())
                    .map(
                        constraints ->
                            new UpdatedGrantResponse(created.toJson(), null, null, constraints)))
        .map(UpdatedGrantResponse::toJson)
        .recover(err -> Future.failedFuture(BaseDxException.from(err)));
  }

  private Future<Void> insertWildcardConstraint(
      UUID delegationId, String role, LocalDateTime expiry) {

    LOGGER.info("Inside wildcardConstraint");
    JsonObject row =
        new JsonObject()
            .put("delegation_id", delegationId.toString())
            .put("role", role)
            .put("scope", "*")
            .put("entity_id", "*")
            .put("entity_type", "*")
            .put("expiry_at", expiry != null ? expiry.format(FORMATTER) : null);

    DelegationScopeConstraint constraint = DelegationScopeConstraint.fromJson(row);

    return scopeConstraintDAO.create(constraint).mapEmpty();
  }

  @Override
  public Future<JsonObject> getDelegationGrantById(String delegationIdStr) {

    UUID delegationId = UUID.fromString(delegationIdStr);
    return delegationGrantDAO
        .get(delegationId)
        .map(DelegationGrant::toJson)
        .recover(
            ServiceErrorHelper.mapNotFound("No delegation grant found with id " + delegationId));
  }

  @Override
  public Future<List<JsonObject>> getDelegationScopeByEntityId(String entityIdStr) {

    Map<String, Object> filter = Map.of(ENTITY_ID, entityIdStr);
    return scopeConstraintDAO
        .getAllWithFilters(filter)
        .map(v -> v.stream().map(DelegationScopeConstraint::toJson).toList())
        .recover(
            ServiceErrorHelper.mapNotFound("No delegation grant found with id " + entityIdStr));
  }

  @Override
  public Future<List<JsonObject>> getAllDelegationScopeConstraints(String itemIdStr) {

    Map<String, Object> mp = Map.of("entity_id", itemIdStr);

    return scopeConstraintDAO
        .getAllWithFilters(mp)
        .map(v -> v.stream().map(DelegationScopeConstraint::toJson).toList())
        .compose(
            all -> {
              if (all == null || all.isEmpty()) {
                LOGGER.warn("No delegation scope constraints found");
                return Future.succeededFuture(Collections.emptyList());
              }
              return Future.succeededFuture(all);
            });
  }

  @Override
  public Future<List<JsonObject>> getAllDelegationsByDelegator(String userIdStr) {
    return getDelegationsWithConstraints(Map.of(DELEGATOR_ID, userIdStr), true);
  }

  @Override
  public Future<List<JsonObject>> getAllDelegationsOfDelegate(String userIdStr) {
    return getDelegationsWithConstraints(Map.of(DELEGATE_ID, userIdStr), false);
  }

  /**
   * Shared helper that fetches delegation grants by filter, enriches each with its scope
   * constraints, and returns a list of enriched JSON objects. Returns an empty list instead of
   * failing when no delegations are found.
   */
  private Future<List<JsonObject>> getDelegationsWithConstraints(
      Map<String, Object> filterMap, boolean includeDelegateInfo) {
    return delegationGrantDAO
        .getAllWithFilters(filterMap)
        .compose(this::enrichDelegationsWithConstraints)
        .compose(delegations -> enrichWithUserInfo(delegations, includeDelegateInfo))
        .recover(
            err -> {
              BaseDxException dxEx = BaseDxException.from(err);
              if (dxEx instanceof DxNotFoundException) {
                return Future.succeededFuture(List.of());
              }
              return Future.failedFuture(dxEx);
            });
  }

  /**
   * Enriches each delegation grant with user information retrieved from Keycloak.
   *
   * <p>When {@code includeDelegateInfo} is {@code true}, the delegate's user information is fetched
   * using the {@code delegateId} and added to the delegation grant. Otherwise, the delegator's user
   * information is fetched using the {@code delegatorId} and added to the delegation grant.
   *
   * @param delegations list of delegation grants to enrich
   * @param includeDelegateInfo whether to include delegate information; when {@code false},
   *     delegator information is included
   * @return a {@link Future} containing the enriched delegation grants
   */
  private Future<List<JsonObject>> enrichWithUserInfo(
      List<JsonObject> delegations, boolean includeDelegateInfo) {

    List<Future<JsonObject>> futures =
        delegations.stream()
            .map(
                delegation -> {
                  String userId =
                      includeDelegateInfo
                          ? delegation.getString("delegateId")
                          : delegation.getString("delegatorId");

                  return keycloakUserService
                      .getUserById(UUID.fromString(userId))
                      .map(
                          user -> {
                            JsonObject userInfo = new JsonObject();

                            if (includeDelegateInfo) {
                              userInfo
                                  .put("delegateId", user.sub().toString())
                                  .put("delegateFirstName", user.givenName())
                                  .put("delegateLastName", user.familyName())
                                  .put("delegateEmail", user.email())
                                  .put("delegateOrganization", user.organisationName());
                            } else {
                              userInfo
                                  .put("delegatorId", user.sub().toString())
                                  .put("delegatorFirstName", user.givenName())
                                  .put("delegatorLastName", user.familyName())
                                  .put("delegatorEmail", user.email())
                                  .put("delegatorOrganization", user.organisationName());
                            }

                            delegation.put(
                                includeDelegateInfo ? "delegate" : "delegator", userInfo);

                            return delegation;
                          })
                      .recover(
                          err -> {
                            LOGGER.warn(
                                "Failed to fetch {} user {} from Keycloak: {}",
                                includeDelegateInfo ? "delegate" : "delegator",
                                userId,
                                err.getMessage());

                            JsonObject userInfo = new JsonObject();

                            if (includeDelegateInfo) {
                              userInfo
                                  .put("delegateId", userId)
                                  .putNull("delegateFirstName")
                                  .putNull("delegateLastName")
                                  .putNull("delegateEmail")
                                  .putNull("delegateOrganization");
                            } else {
                              userInfo
                                  .put("delegatorId", userId)
                                  .putNull("delegatorFirstName")
                                  .putNull("delegatorLastName")
                                  .putNull("delegatorEmail")
                                  .putNull("delegatorOrganization");
                            }

                            delegation.put(
                                includeDelegateInfo ? "delegate" : "delegator", userInfo);

                            return Future.succeededFuture(delegation);
                          });
                })
            .toList();

    return Future.all(futures)
        .map(
            composite -> {
              List<JsonObject> result = new ArrayList<>();

              for (int i = 0; i < futures.size(); i++) {
                result.add(composite.resultAt(i));
              }

              return result;
            });
  }

  /**
   * Enriches a list of delegation grants with their scope constraints. For each delegation, the
   * constraints are fetched and merged into the delegation JSON. If constraint lookup fails for any
   * delegation, an empty constraints array is used as a safe default.
   */
  private Future<List<JsonObject>> enrichDelegationsWithConstraints(
      List<DelegationGrant> delegations) {

    List<Future<JsonObject>> enrichedFutures =
        delegations.stream()
            .map(
                delegation -> {
                  UUID delegationId = delegation.delegationId();
                  Map<String, Object> scopeCondition =
                      Map.of("delegation_id", delegationId.toString());

                  return scopeConstraintDAO
                      .getAllWithFilters(scopeCondition)
                      .map(
                          constraints -> {
                            JsonObject delegationJson = delegation.toJson();
                            JsonArray constraintsArray =
                                new JsonArray(
                                    constraints.stream()
                                        .map(
                                            c -> {
                                              JsonObject json = c.toJson();
                                              json.remove("id");
                                              json.remove("delegationId");
                                              return json;
                                            })
                                        .toList());
                            delegationJson.put("constraints", constraintsArray);
                            return delegationJson;
                          })
                      .recover(
                          err -> {
                            JsonObject delegationJson = delegation.toJson();
                            delegationJson.put("constraints", new JsonArray());
                            return Future.succeededFuture(delegationJson);
                          });
                })
            .toList();

    return Future.all(enrichedFutures).map(cf -> cf.<JsonObject>list());
  }

  @Override
  public Future<Boolean> deleteDelegation(String delegationIdStr, String userIdStr) {

    UUID delegationId = UUID.fromString(delegationIdStr);
    UUID userId = UUID.fromString(userIdStr);

    return delegationGrantDAO
        .get(delegationId)
        .compose(
            delegationGrant -> {
              if (!delegationGrant.delegatorId().equals(userId)) {
                return Future.failedFuture(
                    new DxForbiddenException(
                        "This user cannot delete the delegation as it is not the delegator"));
              }

              return delegationGrantDAO
                  .delete(delegationId)
                  .onSuccess(
                      v ->
                          LOGGER.info(
                              "Delegation {} deleted successfully by user {}",
                              delegationId,
                              userId))
                  .map(v -> true);
            });
  }

  @Override
  public Future<List<JsonObject>> getDelegationRequestsByDelegationId(String delegationIdStr) {

    Map<String, Object> conditionMap = Map.of("delegation_id", delegationIdStr);

    return delegationRequestDAO
        .getAllWithFilters(conditionMap)
        .map(v -> v.stream().map(DelegationUpdateRequest::toJson).toList())
        .recover(
            ServiceErrorHelper.mapNotFound(
                "No delegation request found for delegationId " + delegationIdStr));
  }

  @Override
  public Future<List<JsonObject>> getDelegationScopeConstraints(String delegationIdStr) {
    Map<String, Object> conditionMap = Map.of("delegation_id", delegationIdStr);

    return scopeConstraintDAO
        .getAllWithFilters(conditionMap)
        .map(v -> v.stream().map(DelegationScopeConstraint::toJson).toList())
        .recover(
            ServiceErrorHelper.mapNotFound(
                "No delegation scope constraints found for delegationId " + delegationIdStr));
  }

  private Future<Void> insertScopeConstraints(
      UUID delegationId, JsonArray roles, LocalDateTime expiryAt) {

    if (roles == null || roles.isEmpty()) {
      return Future.succeededFuture();
    }

    List<Future<Void>> insertFutures = new ArrayList<>();

    for (Object roleObj : roles) {
      JsonObject roleJson = (JsonObject) roleObj;

      String role = roleJson.getString("role");
      JsonArray constraints = roleJson.getJsonArray("constraints", new JsonArray());

      if (constraints.isEmpty()) {
        insertFutures.add(createScopes(delegationId, role, expiryAt));
        continue;
      }

      for (Object constraintObj : constraints) {
        JsonObject constraint = (JsonObject) constraintObj;
        String scope = constraint.getString("scope");
        LOGGER.info("constraints in delseviceImpl: {}", constraints.encode());

        JsonArray entityIds = constraint.getJsonArray("entityId");

        // skipping cos-admin-access and compute-management because no entity check is needed for
        // them
        if (entityIds != null && !entityIds.isEmpty()) {
          for (Object entity : entityIds) {
            insertFutures.add(createScopeConstraint(delegationId, role, constraint, entity));
          }
        } else {
          // entity_id == null means entity_type is already null (validated)
          insertFutures.add(createScopeConstraint(delegationId, role, constraint, null));
        }
      }
    }

    return Future.all(insertFutures).mapEmpty();
  }

  private Future<Void> appendScopeConstraints(
      UUID delegationId, JsonArray roles, LocalDateTime delegationExpiry) {

    List<Future<Void>> futures = new ArrayList<>();

    for (Object roleObj : roles) {

      JsonObject roleJson = (JsonObject) roleObj;
      String role = roleJson.getString("role");

      JsonArray constraints = roleJson.getJsonArray("constraints");

      if (constraints == null || constraints.isEmpty()) {
        return Future.failedFuture(
            new DxBadRequestException(
                "constraints are required when appending delegation constraints for role " + role));
      }

      for (Object constraintObj : constraints) {

        JsonObject constraint = (JsonObject) constraintObj;

        JsonArray entityIds = constraint.getJsonArray("entityId");

        if (entityIds != null && !entityIds.isEmpty()) {

          for (Object entityId : entityIds) {
            futures.add(createScopeConstraint(delegationId, role, constraint, entityId));
          }

        } else {

          futures.add(createScopeConstraint(delegationId, role, constraint, null));
        }
      }
    }

    return Future.all(futures).mapEmpty();
  }

  private Future<Void> createScopeConstraint(
      UUID delegationId, String role, JsonObject constraint, Object entityId) {

    JsonObject dbRow =
        new JsonObject()
            .put("delegation_id", delegationId.toString())
            .put("role", role)
            .put(
                "scope",
                constraint.getString("scope") != null ? constraint.getString("scope") : "*")
            .put("expiry_at", constraint.getString("expiryAt"))
            .put("entity_id", entityId != null ? entityId : "*")
            .put(
                "entity_type",
                constraint.getString("entityType") != null
                    ? constraint.getString("entityType")
                    : "*");

    DelegationScopeConstraint delegationScopeConstraint = DelegationScopeConstraint.fromJson(dbRow);

    return scopeConstraintDAO.create(delegationScopeConstraint).mapEmpty();
  }

  private Future<Void> createScopes(UUID delegationId, String role, LocalDateTime expiry) {

    JsonObject dbRow =
        new JsonObject()
            .put("delegation_id", delegationId.toString())
            .put("role", role)
            .put("scope", "*")
            .put("expiry_at", expiry != null ? expiry.format(FORMATTER) : null)
            .put("entity_id", "*")
            .put("entity_type", "*");

    DelegationScopeConstraint delegationScopeConstraint = DelegationScopeConstraint.fromJson(dbRow);

    return scopeConstraintDAO.create(delegationScopeConstraint).mapEmpty();
  }

  private String getHighestRole(List<String> roles) {
    if (roles.contains("cos_admin")) return "cos_admin";
    if (roles.contains("org_admin")) return "org_admin";
    if (roles.contains("provider")) return "provider";
    return "consumer";
  }

  public Future<JsonObject> getDelegatorRoles(String userId, String delegatorId) {

    Map<String, Object> filters =
        Map.of(
            DELEGATOR_ID, delegatorId,
            DELEGATE_ID, userId);

    return delegationGrantDAO
        .getAllWithFilters(filters)
        .compose(
            grants -> {
              if (grants.isEmpty()) {
                return Future.failedFuture(
                    new DxBadRequestException(
                        "Delegation doesn't exist for delegator and delegate"));
              }

              // Fetch DELEGATOR's DxUser
              return keycloakUserService.getUserById(UUID.fromString(delegatorId));
            })
        .compose(
            dxUser -> {
              List<String> roles = dxUser.roles() != null ? dxUser.roles() : List.of();

              JsonObject result =
                  new JsonObject()
                      .put(
                          "delegation_access", new JsonObject().put("roles", new JsonArray(roles)));

              LOGGER.info(
                  "Delegator {} roles for delegate {} → {}", delegatorId, userId, result.encode());

              return Future.succeededFuture(result);
            });
  }

  @Override
  public Future<JsonObject> checkItemAccess(String delegatorId, String delegateId) {

    Map<String, Object> filters =
        Map.of(
            DELEGATOR_ID, delegatorId,
            DELEGATE_ID, delegateId);

    return delegationGrantDAO
        .getAllWithFilters(filters)
        .compose(
            grants -> {
              if (grants.isEmpty()) {
                return Future.failedFuture("Delegation doesn't exist for delegator and delegate");
              }

              List<Future<List<DelegationScopeConstraint>>> scopeFutures =
                  grants.stream()
                      .map(
                          grant -> {
                            Map<String, Object> scopeFilter =
                                Map.of(DELEGATION_ID, grant.delegationId().toString());
                            return scopeConstraintDAO.getAllWithFilters(scopeFilter);
                          })
                      .toList();

              return Future.all(new ArrayList<>(scopeFutures))
                  .map(
                      cf -> {
                        Set<String> allowedItems = new HashSet<>();

                        for (int i = 0; i < cf.size(); i++) {
                          List<DelegationScopeConstraint> scopes = cf.resultAt(i);

                          for (DelegationScopeConstraint scope : scopes) {

                            if ("*".equals(scope.scope())) {
                              return fullAccessResponse();
                            }

                            if ("data-access".equals(scope.scope())) {

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
    return new JsonObject().put("title", "Success").put("result", List.of("*"));
  }

  private Future<UpdatedGrantResponse> addKeycloakUserInfo(
      JsonObject grant, List<JsonObject> constraints) {

    UUID delegatorId = UUID.fromString(grant.getString("delegatorId"));
    UUID delegateId = UUID.fromString(grant.getString("delegateId"));

    return keycloakUserService
        .getUserById(delegatorId)
        .compose(
            delegatorUser ->
                keycloakUserService
                    .getUserById(delegateId)
                    .map(
                        delegateUser -> {
                          JsonObject delegator =
                              new JsonObject()
                                  .put("delegatorId", delegatorUser.sub().toString())
                                  .put("delegatorFirstName", delegatorUser.givenName())
                                  .put("delegatorLastName", delegatorUser.familyName())
                                  .put("delegatorEmail", delegatorUser.email())
                                  .put("delegatorOrganization", delegatorUser.organisationName());

                          JsonObject delegate =
                              new JsonObject()
                                  .put("delegateId", delegateUser.sub().toString())
                                  .put("delegateFirstName", delegateUser.givenName())
                                  .put("delegateLastName", delegateUser.familyName())
                                  .put("delegateEmail", delegateUser.email())
                                  .put("delegateOrganization", delegateUser.organisationName());

                          return new UpdatedGrantResponse(grant, delegator, delegate, constraints);
                        }));
  }

  @Override
  public Future<JsonObject> appendDelegationConstraints(
      String delegationId, String userId, JsonArray roles, String orgId) {

    if (roles == null || roles.isEmpty()) {
      return Future.failedFuture(new DxBadRequestException("roles must not be empty"));
    }

    return delegationGrantDAO
        .get(UUID.fromString(delegationId))
        .compose(
            grant -> {

              // Only delegator can modify the delegation.
              if (!grant.delegatorId().toString().equals(userId)) {
                return Future.failedFuture(
                    new DxForbiddenException(
                        "Only the delegator can append delegation constraints"));
              }

              // Do not allow modifications to expired/deleted grants.
              if (grant.expiryAt() != null && grant.expiryAt().isBefore(LocalDateTime.now())) {
                return Future.failedFuture(
                    new DxBadRequestException("Cannot modify an expired delegation"));
              }

              JsonObject validationBody =
                  new JsonObject()
                      .put("delegatorId", userId)
                      .put("delegateId", grant.delegateId().toString())
                      .put("roles", roles)
                      .put("expiryAt", grant.expiryAt());

              return delegationValidator
                  .validateEntityOwnership(validationBody, UUID.fromString(orgId), roles)
                  .compose(
                      ignored ->
                          appendScopeConstraints(
                              UUID.fromString(delegationId), roles, grant.expiryAt()))
                  .compose(ignored -> getDelegationScopeConstraints(delegationId))
                  .map(
                      constraints ->
                          new JsonObject()
                              .put("delegationId", delegationId)
                              .put("status", "updated")
                              .put("constraints", new JsonArray(constraints)));
            })
        .recover(err -> Future.failedFuture(BaseDxException.from(err)));
  }

  @Override
  public Future<JsonObject> removeDelegationConstraints(
      String delegationId, String userId, JsonArray roles) {

    if (roles == null || roles.isEmpty()) {
      return Future.failedFuture(new DxBadRequestException("roles must not be empty"));
    }

    return delegationGrantDAO
        .get(UUID.fromString(delegationId))
        .compose(
            grant -> {
              if (!grant.delegatorId().toString().equals(userId)) {
                return Future.failedFuture(
                    new DxForbiddenException(
                        "Only the delegator can remove delegation constraints"));
              }

              if (grant.expiryAt() != null && grant.expiryAt().isBefore(LocalDateTime.now())) {
                return Future.failedFuture(
                    new DxBadRequestException("Cannot modify an expired delegation"));
              }

              return removeScopeConstraints(UUID.fromString(delegationId), roles)
                  .compose(ignored -> getDelegationScopeConstraints(delegationId))
                  .map(
                      constraints ->
                          new JsonObject()
                              .put("delegationId", delegationId)
                              .put("status", "updated")
                              .put("constraints", new JsonArray(constraints)));
            })
        .recover(err -> Future.failedFuture(BaseDxException.from(err)));
  }

  private Future<Void> removeScopeConstraints(UUID delegationId, JsonArray roles) {

    List<Future<Integer>> deleteFutures = new ArrayList<>();

    for (Object roleObj : roles) {

      JsonObject roleJson = (JsonObject) roleObj;

      String role = roleJson.getString("role");

      JsonArray constraints = roleJson.getJsonArray("constraints");

      if (constraints == null || constraints.isEmpty()) {
        return Future.failedFuture(
            new DxBadRequestException(
                "constraints are required when removing delegation constraints"));
      }

      for (Object constraintObj : constraints) {

        JsonObject constraint = (JsonObject) constraintObj;

        String scope = constraint.getString("scope");

        String entityType = constraint.getString("entityType", "*");

        JsonArray entityIds = constraint.getJsonArray("entityId");

        if (entityIds != null && !entityIds.isEmpty()) {

          for (Object entityId : entityIds) {

            deleteFutures.add(
                scopeConstraintDAO.deleteByConstraint(
                    delegationId, role, scope, entityId.toString(), entityType));
          }

        } else {

          deleteFutures.add(
              scopeConstraintDAO.deleteByConstraint(delegationId, role, scope, "*", entityType));
        }
      }
    }

    return Future.all(deleteFutures).mapEmpty();
  }
}
