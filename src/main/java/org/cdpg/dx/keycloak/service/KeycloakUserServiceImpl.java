package org.cdpg.dx.keycloak.service;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.ws.rs.ForbiddenException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.delegation.models.DelegationGrant;
import org.cdpg.dx.auth.authorization.registry.SystemRoleScopeMap;
import org.cdpg.dx.auth.model.DxRole;
import org.cdpg.dx.common.exception.BaseDxException;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.exception.KeycloakServiceException;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.common.model.UserInfo;
import org.cdpg.dx.common.util.BlockingExecutionUtil;
import org.cdpg.dx.keycloak.client.KeycloakClientProvider;
import org.cdpg.dx.keycloak.config.KeycloakConstants;
import org.cdpg.dx.keycloak.util.DxUserMapper;
import org.cdpg.dx.keycloak.util.UserInfoMapper;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

public class KeycloakUserServiceImpl implements KeycloakUserService {
  private final Keycloak keycloak;
  private final String realm;
  private final String clientId;

  private static final Logger LOGGER = LogManager.getLogger(KeycloakUserServiceImpl.class);

  public KeycloakUserServiceImpl(JsonObject config) {
    this.keycloak = KeycloakClientProvider.getInstance(config);
    this.realm = config.getString("keycloakRealm");
    this.clientId = config.getString("keycloakClientId");
  }

  private UsersResource usersResource() {
    return keycloak.realm(realm).users();
  }

  @Override
  public Future<Boolean> updateUserPassword(UUID userId, String newPassword) {
    return BlockingExecutionUtil.runBlocking(
        () -> {
          try {
            // Build the new password credential
            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(newPassword);
            credential.setTemporary(
                false); // set to 'true' if you want the user to reset on next login

            // Update password
            usersResource().get(userId.toString()).resetPassword(credential);
            LOGGER.info("Password updated successfully for user '{}'", userId);
            return true;
          } catch (Exception e) {
            LOGGER.warn("Failed to update password for user '{}': {}", userId, e.getMessage(), e);
            throw new KeycloakServiceException("Failed to update password for user: " + userId, e);
          }
        });
  }

  @Override
  public Future<Integer> getTotalCount() {
    return BlockingExecutionUtil.runBlocking(
        () -> {
          try {
            return usersResource().count();
          } catch (Exception e) {
            LOGGER.warn(
                "Failed to retrieve total user count from Keycloak: {}", e.getMessage(), e);
            throw new KeycloakServiceException("Failed to retrieve total user count", e);
          }
        });
  }

  public Future<Integer> getTotalCount(String searchTerm) {
    return BlockingExecutionUtil.runBlocking(
            () -> {
              try {
                UsersResource users = usersResource();
                if (searchTerm != null && !searchTerm.isBlank()) {
                  List<UserRepresentation> matchedUsers =
                      users.search(searchTerm, 0, Integer.MAX_VALUE);
                  return matchedUsers.size();
                }
                return users.count();
              } catch (ForbiddenException e) {
                throw new DxForbiddenException(
                    "User is forbidden to run: get total count of search term");
              }
            })
        .recover(
            err -> {
              BaseDxException dxEx = BaseDxException.from(err);
              if (dxEx instanceof DxForbiddenException) {
                return Future.failedFuture(new DxForbiddenException("User not found"));
              }
              return Future.failedFuture(dxEx);
            });
  }

  @Override
  public Future<List<DxUser>> getUsers(int page, int size, String name) {
    return BlockingExecutionUtil.runBlocking(
        () -> {
          try {
            List<UserRepresentation> reps =
                usersResource()
                    .search(
                        name, // search string
                        (page - 1) * size, // first (offset)
                        size, // max
                        true // filter by enabled/disabled
                        );
            return reps.stream()
                .map(
                    user -> {
                      UserRepresentation user_with_attr =
                          usersResource().get(user.getId()).toRepresentation();
                      List<RoleRepresentation> roles =
                          usersResource().get(user.getId()).roles().realmLevel().listEffective();
                      return DxUserMapper.fromUserRepresentation(user_with_attr, roles);
                    })
                .collect(Collectors.toList());
          } catch (Exception e) {
            LOGGER.warn("Keycloak failed to get users {} ", e.getMessage(), e);
            throw new KeycloakServiceException("Failed to retrieve users from Keycloak", e);
          }
        });
  }

  @Override
  public Future<List<UserInfo>> getUsersInfo(int page, int size, String name) {
    LOGGER.info("page and size,{},{}", page, size);
    return BlockingExecutionUtil.runBlocking(
        () -> {
          try {
            List<UserRepresentation> reps =
                usersResource()
                    .search(
                        name, // search string
                        (page - 1) * size, // first (offset)
                        size, // max
                        true // filter by enabled/disabled
                        );
            return reps.stream()
                .map(UserInfoMapper::fromUserRepresentation)
                .collect(Collectors.toList());
          } catch (Exception e) {
            throw new KeycloakServiceException("Failed to retrieve users from Keycloak", e);
          }
        });
  }

  @Override
  public Future<UserInfo> getUserByUserId(String userId) {
    return BlockingExecutionUtil.runBlocking(
        () -> {
          try {
            List<UserRepresentation> reps = usersResource().searchByUsername(userId, true);
            if (reps.isEmpty()) {
              throw new DxNotFoundException("User not found: " + userId);
            }
            return UserInfoMapper.fromUserRepresentation(reps.get(0));
          } catch (DxNotFoundException e) {
            throw e;
          } catch (Exception e) {
            throw new KeycloakServiceException("Failed to retrieve user by userId: " + userId, e);
          }
        });
  }

  @Override
  public Future<UserInfo> getUserByEmail(String email) {
    return BlockingExecutionUtil.runBlocking(
        () -> {
          try {
            List<UserRepresentation> reps = usersResource().searchByEmail(email, true);
            if (reps.isEmpty()) {
              throw new DxNotFoundException("User not found: " + email);
            }
            return UserInfoMapper.fromUserRepresentation(reps.get(0));
          } catch (DxNotFoundException e) {
            throw e;
          } catch (Exception e) {
            throw new KeycloakServiceException("Failed to retrieve user by email: " + email, e);
          }
        });
  }

  @Override
  public Future<DxUser> getUserById(UUID userId) {
    return BlockingExecutionUtil.runBlocking(
        () -> {
          try {
            UserRepresentation user = usersResource().get(userId.toString()).toRepresentation();
            List<RoleRepresentation> roles =
                usersResource().get(userId.toString()).roles().realmLevel().listEffective();
            return DxUserMapper.fromUserRepresentation(user, roles);
          } catch (Exception e) {
            LOGGER.warn("Error retrieving user with ID {}: {}", userId, e.getMessage(), e);
            throw new KeycloakServiceException("Failed to retrieve user with ID: " + userId, e);
          }
        });
  }

  @Override
  public Future<Boolean> addRoleToUser(UUID userId, DxRole dxRole) {
    return BlockingExecutionUtil.runBlocking(
        () -> {
          try {
            RealmResource realmResource = keycloak.realm(realm);
            UsersResource usersResource = realmResource.users();
            RoleRepresentation role = realmResource.roles().get(dxRole.value()).toRepresentation();

            if (role == null) {
              LOGGER.warn("Role '{}' not found in realm '{}'", dxRole.value(), realm);
              throw new KeycloakServiceException(dxRole.value() + " not available in KC");
            }

            usersResource
                .get(userId.toString())
                .roles()
                .realmLevel()
                .add(Collections.singletonList(role));
            LOGGER.info("Assigned role '{}' to user '{}'", dxRole.value(), userId);
            return true;
          } catch (Exception e) {
            LOGGER.error(
                "Failed to assign role '{}' to user '{}': {}",
                dxRole.value(),
                userId,
                e.getMessage(),
                e);
            throw new KeycloakServiceException("Failed to assign role to user", e);
          }
        });
  }

  @Override
  public Future<Boolean> setDelegationScopes(UUID userId, List<String> scopes, UUID delegatorId) {

    UserRepresentation user = usersResource().get(userId.toString()).toRepresentation();

    Map<String, List<String>> attrs =
        Optional.ofNullable(user.getAttributes()).orElse(new HashMap<>());

    // Get existing scopes, default to empty array
    String existing = attrs.getOrDefault(KeycloakConstants.SCOPES, List.of("[]")).get(0);

    // Set delegator ID
    attrs.put(KeycloakConstants.DID, List.of(delegatorId.toString()));

    // Create clean scopes set to avoid duplicates
    Set<String> cleanScopes = new LinkedHashSet<>();

    // Parse existing and flatten any nested structures
    try {
      JsonArray existingArray = new JsonArray(existing);

      for (int i = 0; i < existingArray.size(); i++) {
        Object item = existingArray.getValue(i);

        if (item instanceof String) {
          String itemStr = ((String) item).trim();

          // Check if this string is itself a JSON array
          if (itemStr.startsWith("[")) {
            try {
              // It's a nested JSON array, parse and extract
              JsonArray nested = new JsonArray(itemStr);
              for (int j = 0; j < nested.size(); j++) {
                Object nestedItem = nested.getValue(j);
                if (nestedItem instanceof String) {
                  cleanScopes.add((String) nestedItem);
                }
              }
            } catch (Exception e) {
              // If parsing fails, skip this corrupted item
              LOGGER.warn("Skipping corrupted nested array: {}", itemStr);
            }
          } else if (!itemStr.startsWith("{")) {
            // It's a plain string scope, add it
            cleanScopes.add(itemStr);
          }
        }
      }
    } catch (Exception e) {
      LOGGER.warn(
          "Error parsing existing scopes for user {}, starting fresh: {}", userId, e.getMessage());
      cleanScopes.clear();
    }

    // Add new scopes
    cleanScopes.addAll(scopes);

    // Build final clean array
    JsonArray scopesArray = new JsonArray();
    cleanScopes.forEach(scopesArray::add);

    LOGGER.info("Saving scopes: {}", scopesArray.encode());

    // Save back to attributes
    attrs.put(KeycloakConstants.SCOPES, List.of(scopesArray.encode()));

    user.setAttributes(attrs);
    usersResource().get(userId.toString()).update(user);

    LOGGER.info("Final delegation scopes saved for user {} → {}", userId, scopesArray.encode());

    return Future.succeededFuture(true);
  }

  @Override
  public Future<Boolean> removeRoleFromUser(UUID userId, DxRole dxRole) {
    return BlockingExecutionUtil.runBlocking(
        () -> {
          try {
            RealmResource realmResource = keycloak.realm(realm);
            UsersResource usersResource = realmResource.users();
            RoleRepresentation role = realmResource.roles().get(dxRole.value()).toRepresentation();

            if (role == null) {
              throw new KeycloakServiceException("Given role not available in KC");
            }

            usersResource
                .get(userId.toString())
                .roles()
                .realmLevel()
                .remove(Collections.singletonList(role));
            LOGGER.info("Removed role '{}' from user '{}'", dxRole.value(), userId);
            return true;
          } catch (Exception e) {
            LOGGER.error(
                "Failed to remove role '{}' from user '{}': {}",
                dxRole.value(),
                userId,
                e.getMessage(),
                e);
            throw new KeycloakServiceException("Failed to remove role from user", e);
          }
        });
  }

  @Override
  public Future<Boolean> updateUserAttributes(UUID userId, Map<String, String> attributes) {
    return BlockingExecutionUtil.runBlocking(
        () -> {
          try {
            UserRepresentation user = usersResource().get(userId.toString()).toRepresentation();
            Map<String, List<String>> existingAttrs =
                Optional.ofNullable(user.getAttributes()).orElse(new HashMap<>());
            attributes.forEach((k, v) -> existingAttrs.put(k, List.of(v)));
            user.setAttributes(existingAttrs);
            LOGGER.info("user attributes: {}", user.getAttributes());
            usersResource().get(userId.toString()).update(user);
            return true;
          } catch (Exception e) {
            throw new KeycloakServiceException(
                "Failed to update attributes for user with ID: " + userId, e);
          }
        });
  }

  @Override
  public Future<Boolean> updateUserAttributes(
      UUID userId, Map<String, String> attributes, String firstName, String lastName) {
    return BlockingExecutionUtil.runBlocking(
        () -> {
          try {
            UserRepresentation user = usersResource().get(userId.toString()).toRepresentation();
            Map<String, List<String>> existingAttrs =
                Optional.ofNullable(user.getAttributes()).orElse(new HashMap<>());
            attributes.forEach((k, v) -> existingAttrs.put(k, List.of(v)));
            user.setAttributes(existingAttrs);

            if (firstName != null && !firstName.isEmpty()) {
              user.setFirstName(firstName);
            }
            if (lastName != null && !lastName.isEmpty()) {
              user.setLastName(lastName);
            }
            usersResource().get(userId.toString()).update(user);
            return true;
          } catch (Exception e) {
            throw new KeycloakServiceException(
                "Failed to update attributes for user with ID: " + userId, e);
          }
        });
  }

  @Override
  public Future<Boolean> deleteUser(UUID userId) {
    return BlockingExecutionUtil.runBlocking(
        () -> {
          try {
            usersResource().get(userId.toString()).remove();
            return true;
          } catch (Exception e) {
            throw new KeycloakServiceException("Failed to delete user with ID: " + userId, e);
          }
        });
  }

  @Override
  public Future<Boolean> enableUser(UUID userId) {
    return setUserEnabled(userId, true);
  }

  @Override
  public Future<Boolean> disableUser(UUID userId) {
    return setUserEnabled(userId, false);
  }

  @Override
  public Future<Boolean> setOrganisationDetails(UUID userId, UUID orgId, String orgName) {
    Map<String, String> attributes = new HashMap<>();
    attributes.put(KeycloakConstants.ORGANISATION_ID, orgId.toString());
    attributes.put(KeycloakConstants.ORGANISATION_NAME, orgName);
    return updateUserAttributes(userId, attributes);
  }

  @Override
  public Future<Boolean> setKycVerifiedTrueWithData(UUID userId, String userName, String txn) {
    Map<String, String> attributes = new HashMap<>();
    attributes.put(KeycloakConstants.KYC_VERIFIED, "true");
    JsonObject aadhaarJson = new JsonObject();

    aadhaarJson.put("kycVerifiedUserName", userName);
    aadhaarJson.put("kycAuthenticationMethod", "DigiLocker");
    aadhaarJson.put("kycVerifiedDate", DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
    aadhaarJson.put("kycStatus", "Active");
    aadhaarJson.put("txn", txn);

    attributes.put(KeycloakConstants.AADHAAR_KYC_DATA, aadhaarJson.encode());
    LOGGER.debug("Setting KYC attributes for user: {}", attributes);
    return updateUserAttributes(userId, attributes);
  }

  @Override
  public Future<Boolean> setKycVerifiedFalse(UUID userId) {
    Map<String, String> attributes = new HashMap<>();
    attributes.put(KeycloakConstants.KYC_VERIFIED, "false");
    attributes.put(KeycloakConstants.AADHAAR_KYC_DATA, "");
    return updateUserAttributes(userId, attributes);
  }

  private Future<Boolean> setUserEnabled(UUID userId, boolean enabled) {
    return BlockingExecutionUtil.runBlocking(
        () -> {
          try {
            UserRepresentation user = usersResource().get(userId.toString()).toRepresentation();
            user.setEnabled(enabled);
            usersResource().get(userId.toString()).update(user);
            return true;
          } catch (Exception e) {
            throw new KeycloakServiceException(
                "Failed to " + (enabled ? "enable" : "disable") + " user with ID: " + userId, e);
          }
        });
  }

  @Override
  public Future<Boolean> clearDelegationScopes(UUID userId, Set<String> scopesToRemove) {

    UserRepresentation user = usersResource().get(userId.toString()).toRepresentation();

    Map<String, List<String>> attrs =
        Optional.ofNullable(user.getAttributes()).orElse(new HashMap<>());

    // ---------- READ EXISTING SCOPES ----------
    JsonArray existingScopes = new JsonArray();

    if (attrs.containsKey(KeycloakConstants.SCOPES)
        && !attrs.get(KeycloakConstants.SCOPES).isEmpty()) {

      String raw = attrs.get(KeycloakConstants.SCOPES).get(0);
      if (raw != null && !raw.isBlank()) {
        existingScopes = new JsonArray(raw);
      }
    }

    LOGGER.info("Existing scopes before deletion: {}", existingScopes.encode());

    // ---------- FILTER SCOPES ----------
    JsonArray updatedScopes = new JsonArray();

    for (Object s : existingScopes) {
      String scope = s.toString();
      if (!scopesToRemove.contains(scope)) {
        updatedScopes.add(scope);
      }
    }

    LOGGER.info("Scopes after deletion: {}", updatedScopes.encode());

    // ---------- UPDATE ATTRIBUTES ----------
    attrs.put(KeycloakConstants.SCOPES, List.of(updatedScopes.encode()));

    if (updatedScopes.isEmpty()) {
      attrs.remove(KeycloakConstants.DID);
    }

    user.setAttributes(attrs);
    usersResource().get(userId.toString()).update(user);

    // ---------- REMOVE DELEGATE ROLE ----------
    if (updatedScopes.isEmpty()) {
      return removeDelegateRole(userId).map(true);
    }

    return Future.succeededFuture(true);
  }

  @Override
  public Future<DelegationGrant> publishScopesAndRolesToKeycloak(
      DelegationGrant created, JsonArray roles, String highestRole, List<String> delegatorRoles) {

    // Fetch user ONCE
    UserRepresentation user =
        usersResource().get(created.delegateId().toString()).toRepresentation();
    Map<String, List<String>> attrs = user.getAttributes();
    if (attrs == null) {
      attrs = new HashMap<>();
    }

    // -------------------- SCOPES --------------------
    List<String> newScopes = new ArrayList<>();

    if (roles == null || roles.isEmpty()) {
      newScopes =
          DxRole.fromString(highestRole)
              .map(dxRole -> new ArrayList<>(SystemRoleScopeMap.getScopes(dxRole)))
              .orElse(new ArrayList<>());
      LOGGER.info(
          "Wildcard delegation detected, expanding scopes for role: {} and scopes: {}",
          highestRole,
          newScopes);

    } else {
      for (Object r : roles) {
        JsonObject roleObj = (JsonObject) r;
        String role = roleObj.getString("role");
        JsonArray constraints = roleObj.getJsonArray("constraints");

        if (constraints != null) {
          for (Object c : constraints) {
            newScopes.add(((JsonObject) c).getString("scope"));
          }
        } else {
          List<String> roleScopes =
              DxRole.fromString(role)
                  .map(dxRole -> new ArrayList<>(SystemRoleScopeMap.getScopes(dxRole)))
                  .orElse(new ArrayList<>());
          LOGGER.info(
              "No subset constraint found, expanding scopes for role: {} and scopes: {}",
              role,
              roleScopes);
          newScopes = roleScopes;
        }
      }
    }

    // Merge with existing scopes — no duplicates
    Set<String> mergedScopes = new LinkedHashSet<>();
    String existingScopes = attrs.getOrDefault(KeycloakConstants.SCOPES, List.of("[]")).get(0);
    try {
      new JsonArray(existingScopes).forEach(s -> mergedScopes.add(s.toString()));
    } catch (Exception e) {
      LOGGER.warn("Failed to parse existing scopes, starting fresh: {}", e.getMessage());
    }
    mergedScopes.addAll(newScopes);

    JsonArray scopesArray = new JsonArray();
    mergedScopes.forEach(scopesArray::add);
    attrs.put(KeycloakConstants.SCOPES, List.of(scopesArray.encode()));
    attrs.put(KeycloakConstants.DID, List.of(created.delegatorId().toString()));

    // -------------------- ROLES --------------------
    // Merge with existing roles — no duplicates
    Set<String> mergedRoles = new LinkedHashSet<>();
    String existingRoles =
        attrs.getOrDefault(KeycloakConstants.DELEGATION_ROLES, List.of("[]")).get(0);
    try {
      new JsonArray(existingRoles).forEach(r -> mergedRoles.add(r.toString()));
    } catch (Exception e) {
      LOGGER.warn("Failed to parse existing roles, starting fresh: {}", e.getMessage());
    }
    mergedRoles.addAll(delegatorRoles);

    JsonArray rolesArray = new JsonArray();
    mergedRoles.forEach(rolesArray::add);
    attrs.put(KeycloakConstants.DELEGATION_ROLES, List.of(rolesArray.encode()));

    // Single update
    user.setAttributes(attrs);
    usersResource().get(created.delegateId().toString()).update(user);

    LOGGER.info("Scopes saved: {}", scopesArray.encode());
    LOGGER.info("Roles saved: {}", rolesArray.encode());
    LOGGER.info(
        "Readback: {}",
        usersResource().get(created.delegateId().toString()).toRepresentation().getAttributes());

    return Future.succeededFuture(created);
  }

  private Future<Boolean> removeDelegateRole(UUID userId) {

    return BlockingExecutionUtil.runBlocking(
        () -> {
          RealmResource realmResource = keycloak.realm(realm);

          RoleRepresentation delegateRole =
              realmResource
                  .roles()
                  .get("delegate") // "delegate"
                  .toRepresentation();

          realmResource
              .users()
              .get(userId.toString())
              .roles()
              .realmLevel()
              .remove(List.of(delegateRole));

          LOGGER.info("Removed delegate role from user {}", userId);
          return true;
        });
  }

  @Override
  public Future<Boolean> addCustomRoleToUser(UUID userId, String role_user) {
    return BlockingExecutionUtil.runBlocking(
        () -> {
          try {
            RealmResource realmResource = keycloak.realm(realm);
            UsersResource usersResource = realmResource.users();
            RoleRepresentation role = realmResource.roles().get(role_user).toRepresentation();

            if (role == null) {
              LOGGER.warn("Role '{}' not found in realm '{}'", role_user, realm);
              throw new KeycloakServiceException(role + " not available in KC");
            }

            usersResource
                .get(userId.toString())
                .roles()
                .realmLevel()
                .add(Collections.singletonList(role));
            LOGGER.info("Assigned role '{}' to user '{}'", role_user, userId);
            return true;
          } catch (Exception e) {
            LOGGER.error(
                "Failed to assign role '{}' to user '{}': {}",
                role_user,
                userId,
                e.getMessage(),
                e);
            throw new KeycloakServiceException("Failed to assign role to user", e);
          }
        });
  }

  @Override
  public Future<Boolean> setCustomScopeToUser(UUID userId, List<String> scope) {

    try {
      UserRepresentation user = usersResource().get(userId.toString()).toRepresentation();

      Map<String, List<String>> attrs =
          Optional.ofNullable(user.getAttributes()).orElse(new HashMap<>());

      // Deduplicate + preserve order
      Set<String> cleanScopes = new LinkedHashSet<>(scope);

      // Build final JSON array
      JsonArray scopesArray = new JsonArray();
      cleanScopes.forEach(scopesArray::add);

      LOGGER.info("Saving scopes for user {} : {}", userId, scopesArray.encode());

      // Save back to attributes
      attrs.put(KeycloakConstants.USER_SCOPE, List.of(scopesArray.encode()));
      user.setAttributes(attrs);

      LOGGER.info("Keycloak client: {}", keycloak.tokenManager().getAccessTokenString());

      usersResource().get(userId.toString()).update(user);

      return Future.succeededFuture(true);

    } catch (Exception e) {
      LOGGER.error("Failed to set custom scope for user {}", userId, e);
      return Future.failedFuture(e);
    }
  }
}
