package org.cdpg.dx.keycloak.service;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.cdpg.dx.aaa.delegation.models.DelegationGrant;
import org.cdpg.dx.auth.model.DxRole;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.common.model.UserInfo;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface KeycloakUserService {
    Future<Integer> getTotalCount();
    Future<Integer> getTotalCount(String searchTerm);
    Future<List<DxUser>> getUsers(int page, int size, String name);
    Future<List<UserInfo>> getUsersInfo(int page, int size, String name);
    Future<UserInfo> getUserByUserId(String userId);
    Future<UserInfo> getUserByEmail(String email);
    Future<DxUser> getUserById(UUID userId);
    Future<Boolean> updateUserAttributes(UUID userId, Map<String, String> attributes);
    Future<Boolean> updateUserAttributes(UUID userId, Map<String, String> attributes, String firstName, String lastName);
    Future<Boolean> deleteUser(UUID userId);
    Future<Boolean> enableUser(UUID userId);
    Future<Boolean> disableUser(UUID userId);
    Future<Boolean> addRoleToUser(UUID userId, DxRole role);
    Future<Boolean> removeRoleFromUser(UUID userId, DxRole dxRole);
    Future<Boolean> setOrganisationDetails(UUID userId, UUID orgId, String orgName);
    Future<Boolean> setKycVerifiedTrueWithData(UUID userId, String userName,String txn);
    Future<Boolean> setKycVerifiedFalse(UUID userId);
    Future<Boolean> updateUserPassword(UUID userId, String password);
    Future<Boolean> setDelegationScopes(UUID userId , List<String> scope,UUID delegatorId);
    Future<Boolean> setCustomScopeToUser(UUID userId , List<String> scope);
    Future<Boolean> addCustomRoleToUser(UUID userId,String role);
    Future<Boolean> clearDelegationScopes(    UUID userId,
                                              Set<String> scopesToRemove);
    Future<DelegationGrant> publishScopesAndRolesToKeycloak(
    DelegationGrant created,
    JsonArray roles,
    String highestRole,
    List<String> delegatorRoles
  );
//    Future<Boolean> addScopesToUser(UUID userId, List<String> scopes);
}

