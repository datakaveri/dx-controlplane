package org.cdpg.dx.keycloak.service;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.cdpg.dx.auth.authorization.model.DxRole;
import org.cdpg.dx.common.exception.KeycloakServiceException;
import org.cdpg.dx.common.util.BlockingExecutionUtil;
import org.cdpg.dx.keycloak.client.KeycloakClientProvider;
import org.cdpg.dx.keycloak.config.KeycloakConstants;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.keycloak.util.DxUserMapper;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.apache.logging.log4j.Logger;
import org.keycloak.representations.idm.CredentialRepresentation;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class KeycloakUserServiceImpl implements KeycloakUserService {
    private final Keycloak keycloak;
    private final String realm;

    private final static Logger LOGGER = LogManager.getLogger(KeycloakUserServiceImpl.class);

    public KeycloakUserServiceImpl(JsonObject config) {
        this.keycloak = KeycloakClientProvider.getInstance(config);
        this.realm = config.getString("keycloakRealm");
    }

    private UsersResource usersResource() {
        return keycloak.realm(realm).users();

    }


    @Override
    public Future<Boolean> updateUserPassword(UUID userId, String newPassword) {
        return BlockingExecutionUtil.runBlocking(() -> {
            try {
                // Build the new password credential
                CredentialRepresentation credential = new CredentialRepresentation();
                credential.setType(CredentialRepresentation.PASSWORD);
                credential.setValue(newPassword);
                credential.setTemporary(false); // set to 'true' if you want the user to reset on next login

                // Update password
                usersResource().get(userId.toString()).resetPassword(credential);
                LOGGER.info("Password updated successfully for user '{}'", userId);
                return true;
            } catch (Exception e) {
                LOGGER.error("Failed to update password for user '{}': {}", userId, e.getMessage(), e);
                throw new KeycloakServiceException("Failed to update password for user: " + userId, e);
            }
        });
    }

  @Override
  public Future<Integer> getTotalCount() {
    return BlockingExecutionUtil.runBlocking(() -> {

        try {
          return usersResource().count();
        }
        catch(Exception e) {
            LOGGER.error("Failed to retrieve total user count from Keycloak: {}", e.getMessage(), e);
            throw new KeycloakServiceException("Failed to retrieve total user count", e);
        }
  });
  }

    @Override
    public Future<List<DxUser>> getUsers(int page, int size, String name) {
        return BlockingExecutionUtil.runBlocking(() -> {
            try {
                //System.out.println("Fetching users from Keycloak: page=" + page + ", size=" + size + ", enabled=" + enabled);

              List<UserRepresentation> reps = usersResource().search(
                        name,          // search string
                        (page-1) * size,   // first (offset)
                        size,          // max
                        true// filter by enabled/disabled
                );
                return reps.stream()
                        .map(user -> {
                            UserRepresentation user_with_attr = usersResource().get(user.getId()).toRepresentation();
                            List<RoleRepresentation> roles = usersResource().get(user.getId()).roles().realmLevel().listEffective();
                            return DxUserMapper.fromUserRepresentation(user_with_attr, roles);
                        }).collect(Collectors.toList());
            } catch (Exception e) {
                throw new KeycloakServiceException("Failed to retrieve users from Keycloak", e);
            }
        });
    }

    @Override
    public Future<DxUser> getUserById(UUID userId) {
        return BlockingExecutionUtil.runBlocking(() -> {
            try {
                UserRepresentation user = usersResource().get(userId.toString()).toRepresentation();
                List<RoleRepresentation> roles = usersResource().get(userId.toString()).roles().realmLevel().listEffective();
                return DxUserMapper.fromUserRepresentation(user, roles);
            } catch (Exception e) {
                e.printStackTrace();
                throw new KeycloakServiceException("Failed to retrieve user with ID: " + userId, e);
            }
        });
    }



    @Override
    public Future<Boolean> addRoleToUser(UUID userId, DxRole dxRole) {
        return BlockingExecutionUtil.runBlocking(() -> {
            try {
                RealmResource realmResource = keycloak.realm(realm);
                UsersResource usersResource = realmResource.users();
                RoleRepresentation role = realmResource.roles().get(dxRole.getRole()).toRepresentation();

                if (role == null) {
                    LOGGER.warn("Role '{}' not found in realm '{}'", dxRole.getRole(), realm);
                    throw new KeycloakServiceException(dxRole.getRole()  + " not available in KC");
                }

                usersResource.get(userId.toString()).roles().realmLevel().add(Collections.singletonList(role));
                LOGGER.info("Assigned role '{}' to user '{}'", dxRole.getRole(), userId);
                return true;
            } catch (Exception e) {
                LOGGER.error("Failed to assign role '{}' to user '{}': {}", dxRole.getRole(), userId, e.getMessage(), e);
                throw new KeycloakServiceException("Failed to assign role to user", e);
            }
        });
    }

    @Override
    public Future<Boolean> removeRoleFromUser(UUID userId, DxRole dxRole) {
        return BlockingExecutionUtil.runBlocking(() -> {
            try {
                RealmResource realmResource = keycloak.realm(realm);
                UsersResource usersResource = realmResource.users();
                RoleRepresentation role = realmResource.roles().get(dxRole.getRole()).toRepresentation();

                if (role == null) {
                    throw new KeycloakServiceException("Given role not available in KC");
                }

                usersResource.get(userId.toString()).roles().realmLevel().remove(Collections.singletonList(role));
                LOGGER.info("Removed role '{}' from user '{}'", dxRole.getRole(), userId);
                return true;
            } catch (Exception e) {
                LOGGER.error("Failed to remove role '{}' from user '{}': {}", dxRole.getRole(), userId, e.getMessage(), e);
                throw new KeycloakServiceException("Failed to remove role from user", e);
            }
        });
    }



    @Override
    public Future<Boolean> updateUserAttributes(UUID userId, Map<String, String> attributes) {
        return BlockingExecutionUtil.runBlocking(() -> {
            try {
                UserRepresentation user = usersResource().get(userId.toString()).toRepresentation();
                Map<String, List<String>> existingAttrs = Optional.ofNullable(user.getAttributes()).orElse(new HashMap<>());
                attributes.forEach((k, v) -> existingAttrs.put(k, List.of(v)));
                user.setAttributes(existingAttrs);
                usersResource().get(userId.toString()).update(user);
                return true;
            } catch (Exception e) {
                throw new KeycloakServiceException("Failed to update attributes for user with ID: " + userId, e);
            }
        });
    }

    @Override
    public Future<Boolean> updateUserAttributes(UUID userId, Map<String, String> attributes, String firstName, String lastName) {
        return BlockingExecutionUtil.runBlocking(() -> {
            try {
                UserRepresentation user = usersResource().get(userId.toString()).toRepresentation();
                Map<String, List<String>> existingAttrs = Optional.ofNullable(user.getAttributes()).orElse(new HashMap<>());
                attributes.forEach((k, v) -> existingAttrs.put(k, List.of(v)));
                user.setAttributes(existingAttrs);

                if(firstName != null && !firstName.isEmpty()) {
                    user.setFirstName(firstName);
                }
                if(lastName != null && !lastName.isEmpty()) {
                    user.setLastName(lastName);
                }
                usersResource().get(userId.toString()).update(user);
                return true;
            } catch (Exception e) {
                throw new KeycloakServiceException("Failed to update attributes for user with ID: " + userId, e);
            }
        });
    }

    @Override
    public Future<Boolean> deleteUser(UUID userId) {
        return BlockingExecutionUtil.runBlocking(() -> {
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
  public Future<Boolean> setKycVerifiedTrueWithData(UUID userId, String userName,String txn) {
    Map<String, String> attributes = new HashMap<>();
    attributes.put(KeycloakConstants.KYC_VERIFIED, "true");
    JsonObject aadhaarJson = new JsonObject();

    aadhaarJson.put("kycVerifiedUserName", userName);
    aadhaarJson.put("kycAuthenticationMethod", "DigiLocker");
    aadhaarJson.put("kycVerifiedDate", DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
    aadhaarJson.put("kycStatus", "Active");
    aadhaarJson.put("txn", txn);

    attributes.put(KeycloakConstants.AADHAAR_KYC_DATA, aadhaarJson.encode());
    System.out.println("attributes = " + attributes);
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
        return BlockingExecutionUtil.runBlocking(() -> {
            try {
                UserRepresentation user = usersResource().get(userId.toString()).toRepresentation();
                user.setEnabled(enabled);
                usersResource().get(userId.toString()).update(user);
                return true;
            } catch (Exception e) {
                throw new KeycloakServiceException("Failed to " + (enabled ? "enable" : "disable") + " user with ID: " + userId, e);
            }
        });
    }
}
