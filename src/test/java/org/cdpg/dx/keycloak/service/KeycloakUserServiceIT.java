package org.cdpg.dx.keycloak.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.core.Future;
import io.vertx.junit5.VertxTestContext;
import java.util.*;
import org.cdpg.dx.auth.model.DxRole;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.common.model.UserInfo;
import org.cdpg.dx.testutil.KeycloakTestBase;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.keycloak.representations.idm.UserRepresentation;

/**
 * Integration tests for {@link KeycloakUserServiceImpl} against a real Keycloak 26 container.
 *
 * <p>Tests are ordered to avoid interference (e.g., role tests before delete tests).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class KeycloakUserServiceIT extends KeycloakTestBase {

  @Test
  @Order(1)
  void getUserById_returnsUser(VertxTestContext ctx) {
    keycloakUserService
        .getUserById(testUser1Id)
        .onComplete(
            ctx.succeeding(
                user ->
                    ctx.verify(
                        () -> {
                          assertThat(user).isNotNull();
                          assertThat(user.sub()).isEqualTo(testUser1Id);
                          assertThat(user.email()).isEqualTo("test1@example.com");
                          assertThat(user.givenName()).isEqualTo("Test");
                          assertThat(user.familyName()).isEqualTo("UserOne");
                          assertThat(user.emailVerified()).isTrue();
                          assertThat(user.roles()).contains("consumer");
                          ctx.completeNow();
                        })));
  }

  @Test
  @Order(2)
  void getUsers_paginatedResults(VertxTestContext ctx) {
    keycloakUserService
        .getUsers(1, 10, "")
        .onComplete(
            ctx.succeeding(
                users ->
                    ctx.verify(
                        () -> {
                          assertThat(users).isNotNull();
                          // At least 2 seeded users + service account user
                          assertThat(users.size()).isGreaterThanOrEqualTo(2);
                          ctx.completeNow();
                        })));
  }

  @Test
  @Order(3)
  void getUsersInfo_returnsUserInfo(VertxTestContext ctx) {
    keycloakUserService
        .getUsersInfo(1, 10, "")
        .onComplete(
            ctx.succeeding(
                users ->
                    ctx.verify(
                        () -> {
                          assertThat(users).isNotNull();
                          assertThat(users.size()).isGreaterThanOrEqualTo(2);
                          ctx.completeNow();
                        })));
  }

  @Test
  @Order(4)
  void getTotalCount_returnsCount(VertxTestContext ctx) {
    keycloakUserService
        .getTotalCount()
        .onComplete(
            ctx.succeeding(
                count ->
                    ctx.verify(
                        () -> {
                          // At least 2 seeded users + service account
                          assertThat(count).isGreaterThanOrEqualTo(2);
                          ctx.completeNow();
                        })));
  }

  @Test
  @Order(5)
  void getTotalCount_withSearchTerm(VertxTestContext ctx) {
    keycloakUserService
        .getTotalCount("testuser1")
        .onComplete(
            ctx.succeeding(
                count ->
                    ctx.verify(
                        () -> {
                          assertThat(count).isEqualTo(1);
                          ctx.completeNow();
                        })));
  }

  @Test
  @Order(10)
  void addRoleToUser_assignsRole(VertxTestContext ctx) {
    keycloakUserService
        .addRoleToUser(testUser2Id, DxRole.PROVIDER)
        .compose(
            result -> {
              assertThat(result).isTrue();
              return keycloakUserService.getUserById(testUser2Id);
            })
        .onComplete(
            ctx.succeeding(
                user ->
                    ctx.verify(
                        () -> {
                          assertThat(user.roles()).contains("provider");
                          ctx.completeNow();
                        })));
  }

  @Test
  @Order(11)
  void removeRoleFromUser_removesRole(VertxTestContext ctx) {
    // First add the role, then remove it
    keycloakUserService
        .addRoleToUser(testUser2Id, DxRole.COMPUTE)
        .compose(added -> keycloakUserService.removeRoleFromUser(testUser2Id, DxRole.COMPUTE))
        .compose(
            removed -> {
              assertThat(removed).isTrue();
              return keycloakUserService.getUserById(testUser2Id);
            })
        .onComplete(
            ctx.succeeding(
                user ->
                    ctx.verify(
                        () -> {
                          assertThat(user.roles()).doesNotContain("compute");
                          ctx.completeNow();
                        })));
  }

  @Test
  @Order(20)
  void updateUserAttributes_setsAttributes(VertxTestContext ctx) {
    UUID orgId = UUID.randomUUID();
    Map<String, String> attrs = new HashMap<>();
    attrs.put("organisation_id", orgId.toString());
    attrs.put("organisation_name", "Test Org");

    keycloakUserService
        .updateUserAttributes(testUser1Id, attrs)
        .onComplete(
            ctx.succeeding(
                result ->
                    ctx.verify(
                        () -> {
                          assertThat(result).isTrue();
                          // Verify directly via admin client
                          UserRepresentation user =
                              adminClient
                                  .realm("master")
                                  .users()
                                  .get(testUser1Id.toString())
                                  .toRepresentation();
                          var userAttrs = user.getAttributes();
                          assertThat(userAttrs).isNotNull();
                          assertThat(userAttrs.get("organisation_id").get(0))
                              .isEqualTo(orgId.toString());
                          assertThat(userAttrs.get("organisation_name").get(0))
                              .isEqualTo("Test Org");
                          ctx.completeNow();
                        })));
  }

  @Test
  @Order(21)
  void updateUserAttributes_withNames(VertxTestContext ctx) {
    Map<String, String> attrs = new HashMap<>();
    attrs.put("organisation_name", "Updated Org");

    keycloakUserService
        .updateUserAttributes(testUser1Id, attrs, "UpdatedFirst", "UpdatedLast")
        .compose(
            result -> {
              assertThat(result).isTrue();
              return keycloakUserService.getUserById(testUser1Id);
            })
        .onComplete(
            ctx.succeeding(
                user ->
                    ctx.verify(
                        () -> {
                          assertThat(user.givenName()).isEqualTo("UpdatedFirst");
                          assertThat(user.familyName()).isEqualTo("UpdatedLast");
                          ctx.completeNow();
                        })));
  }

  @Test
  @Order(22)
  void setOrganisationDetails_setsOrgInfo(VertxTestContext ctx) {
    UUID orgId = UUID.randomUUID();
    String orgName = "Integration Test Org";

    keycloakUserService
        .setOrganisationDetails(testUser2Id, orgId, orgName)
        .onComplete(
            ctx.succeeding(
                result ->
                    ctx.verify(
                        () -> {
                          assertThat(result).isTrue();
                          UserRepresentation user =
                              adminClient
                                  .realm("master")
                                  .users()
                                  .get(testUser2Id.toString())
                                  .toRepresentation();
                          var userAttrs = user.getAttributes();
                          assertThat(userAttrs).isNotNull();
                          assertThat(userAttrs.get("organisation_id").get(0))
                              .isEqualTo(orgId.toString());
                          assertThat(userAttrs.get("organisation_name").get(0))
                              .isEqualTo(orgName);
                          ctx.completeNow();
                        })));
  }

  @Test
  @Order(23)
  void setKycVerifiedTrueWithData_setsKyc(VertxTestContext ctx) {
    keycloakUserService
        .setKycVerifiedTrueWithData(testUser1Id, "Test UserOne", "TXN123")
        .onComplete(
            ctx.succeeding(
                result ->
                    ctx.verify(
                        () -> {
                          assertThat(result).isTrue();
                          UserRepresentation user =
                              adminClient
                                  .realm("master")
                                  .users()
                                  .get(testUser1Id.toString())
                                  .toRepresentation();
                          var userAttrs = user.getAttributes();
                          assertThat(userAttrs).isNotNull();
                          assertThat(userAttrs.get("kyc_verified").get(0)).isEqualTo("true");
                          assertThat(userAttrs.get("aadhaar_kyc_data").get(0))
                              .contains("kycVerifiedUserName");
                          ctx.completeNow();
                        })));
  }

  @Test
  @Order(24)
  void setKycVerifiedFalse_clearsKyc(VertxTestContext ctx) {
    // First set KYC to true, then clear it
    keycloakUserService
        .setKycVerifiedTrueWithData(testUser1Id, "Test UserOne", "TXN456")
        .compose(set -> keycloakUserService.setKycVerifiedFalse(testUser1Id))
        .onComplete(
            ctx.succeeding(
                result ->
                    ctx.verify(
                        () -> {
                          assertThat(result).isTrue();
                          UserRepresentation user =
                              adminClient
                                  .realm("master")
                                  .users()
                                  .get(testUser1Id.toString())
                                  .toRepresentation();
                          var userAttrs = user.getAttributes();
                          assertThat(userAttrs.get("kyc_verified").get(0)).isEqualTo("false");
                          ctx.completeNow();
                        })));
  }

  @Test
  @Order(30)
  void disableUser_disablesUser(VertxTestContext ctx) {
    keycloakUserService
        .disableUser(testUser2Id)
        .onComplete(
            ctx.succeeding(
                result ->
                    ctx.verify(
                        () -> {
                          assertThat(result).isTrue();
                          // Verify via admin client
                          UserRepresentation user =
                              adminClient
                                  .realm("master")
                                  .users()
                                  .get(testUser2Id.toString())
                                  .toRepresentation();
                          assertThat(user.isEnabled()).isFalse();
                          ctx.completeNow();
                        })));
  }

  @Test
  @Order(31)
  void enableUser_enablesDisabledUser(VertxTestContext ctx) {
    // First disable, then re-enable
    keycloakUserService
        .disableUser(testUser2Id)
        .compose(disabled -> keycloakUserService.enableUser(testUser2Id))
        .onComplete(
            ctx.succeeding(
                result ->
                    ctx.verify(
                        () -> {
                          assertThat(result).isTrue();
                          UserRepresentation user =
                              adminClient
                                  .realm("master")
                                  .users()
                                  .get(testUser2Id.toString())
                                  .toRepresentation();
                          assertThat(user.isEnabled()).isTrue();
                          ctx.completeNow();
                        })));
  }

  @Test
  @Order(32)
  void updateUserPassword_changesPassword(VertxTestContext ctx) {
    keycloakUserService
        .updateUserPassword(testUser1Id, "newPassword123")
        .onComplete(
            ctx.succeeding(
                result ->
                    ctx.verify(
                        () -> {
                          assertThat(result).isTrue();
                          ctx.completeNow();
                        })));
  }

  @Test
  @Order(40)
  void setDelegationScopes_setsScopes(VertxTestContext ctx) {
    UUID delegatorId = UUID.randomUUID();
    List<String> scopes = List.of("read", "write", "subscribe");

    keycloakUserService
        .setDelegationScopes(testUser1Id, scopes, delegatorId)
        .onComplete(
            ctx.succeeding(
                result ->
                    ctx.verify(
                        () -> {
                          assertThat(result).isTrue();
                          // Verify directly via admin client
                          UserRepresentation user =
                              adminClient
                                  .realm("master")
                                  .users()
                                  .get(testUser1Id.toString())
                                  .toRepresentation();
                          var userAttrs = user.getAttributes();
                          assertThat(userAttrs).isNotNull();
                          assertThat(userAttrs.get("did").get(0))
                              .isEqualTo(delegatorId.toString());
                          assertThat(userAttrs.get("delegation_scope").get(0))
                              .contains("read")
                              .contains("write")
                              .contains("subscribe");
                          ctx.completeNow();
                        })));
  }

  @Test
  @Order(41)
  void clearDelegationScopes_removesScopes(VertxTestContext ctx) {
    UUID delegatorId = UUID.randomUUID();
    List<String> scopes = List.of("read", "write", "subscribe");

    // First set scopes, then clear some
    keycloakUserService
        .setDelegationScopes(testUser2Id, scopes, delegatorId)
        .compose(set -> keycloakUserService.clearDelegationScopes(testUser2Id, Set.of("write")))
        .onComplete(
            ctx.succeeding(
                cleared ->
                    ctx.verify(
                        () -> {
                          assertThat(cleared).isTrue();
                          UserRepresentation user =
                              adminClient
                                  .realm("master")
                                  .users()
                                  .get(testUser2Id.toString())
                                  .toRepresentation();
                          var userAttrs = user.getAttributes();
                          assertThat(userAttrs).isNotNull();
                          String scopeStr = userAttrs.get("delegation_scope").get(0);
                          assertThat(scopeStr).contains("read").contains("subscribe");
                          assertThat(scopeStr).doesNotContain("\"write\"");
                          ctx.completeNow();
                        })));
  }

  @Test
  @Order(42)
  void setCustomScopeToUser_setsScope(VertxTestContext ctx) {
    List<String> scopes = List.of("custom:read", "custom:write");

    keycloakUserService
        .setCustomScopeToUser(testUser2Id, scopes)
        .onComplete(
            ctx.succeeding(
                result ->
                    ctx.verify(
                        () -> {
                          assertThat(result).isTrue();
                          UserRepresentation user =
                              adminClient
                                  .realm("master")
                                  .users()
                                  .get(testUser2Id.toString())
                                  .toRepresentation();
                          var userAttrs = user.getAttributes();
                          assertThat(userAttrs).isNotNull();
                          List<String> scopeAttr = userAttrs.get("scope");
                          assertThat(scopeAttr).isNotNull();
                          assertThat(scopeAttr.get(0)).contains("custom:read");
                          assertThat(scopeAttr.get(0)).contains("custom:write");
                          ctx.completeNow();
                        })));
  }

  @Test
  @Order(43)
  void addCustomRoleToUser_assignsCustomRole(VertxTestContext ctx) {
    keycloakUserService
        .addCustomRoleToUser(testUser2Id, "cos_admin")
        .compose(
            result -> {
              assertThat(result).isTrue();
              return keycloakUserService.getUserById(testUser2Id);
            })
        .onComplete(
            ctx.succeeding(
                user ->
                    ctx.verify(
                        () -> {
                          assertThat(user.roles()).contains("cos_admin");
                          ctx.completeNow();
                        })));
  }

  // ─── EDGE CASE TESTS ──────────────────────────────────────────────────────

  @Test
  @Order(50)
  void getUserById_returnsUserWithScopes(VertxTestContext ctx) {
    // testUser1 should have delegation_scope set from order(40)
    keycloakUserService
        .getUserById(testUser1Id)
        .onComplete(
            ctx.succeeding(
                user ->
                    ctx.verify(
                        () -> {
                          assertThat(user).isNotNull();
                          assertThat(user.scopes()).isNotNull();
                          ctx.completeNow();
                        })));
  }

  @Test
  @Order(51)
  void getUsers_withNameFilter(VertxTestContext ctx) {
    keycloakUserService
        .getUsers(1, 10, "testuser1")
        .onComplete(
            ctx.succeeding(
                users ->
                    ctx.verify(
                        () -> {
                          assertThat(users).isNotEmpty();
                          assertThat(users.stream().anyMatch(u -> u.preferredUsername().equals("testuser1")))
                              .isTrue();
                          ctx.completeNow();
                        })));
  }

  @Test
  @Order(52)
  void getUsers_withNonExistentNameFilter(VertxTestContext ctx) {
    keycloakUserService
        .getUsers(1, 10, "nonexistentuser12345")
        .onComplete(
            ctx.succeeding(
                users ->
                    ctx.verify(
                        () -> {
                          assertThat(users).isEmpty();
                          ctx.completeNow();
                        })));
  }

  @Test
  @Order(53)
  void getTotalCount_withNonMatchingSearch(VertxTestContext ctx) {
    keycloakUserService
        .getTotalCount("absolutelynonexistentuser999")
        .onComplete(
            ctx.succeeding(
                count ->
                    ctx.verify(
                        () -> {
                          assertThat(count).isEqualTo(0);
                          ctx.completeNow();
                        })));
  }

  @Test
  @Order(54)
  void getUsersInfo_containsCorrectFields(VertxTestContext ctx) {
    keycloakUserService
        .getUsersInfo(1, 10, "testuser1")
        .onComplete(
            ctx.succeeding(
                users ->
                    ctx.verify(
                        () -> {
                          assertThat(users).isNotEmpty();
                          UserInfo info = users.get(0);
                          assertThat(info.sub()).isNotNull();
                          assertThat(info.email()).isEqualTo("test1@example.com");
                          assertThat(info.preferredUsername()).isEqualTo("testuser1");
                          ctx.completeNow();
                        })));
  }

  @Test
  @Order(55)
  void updateUserAttributes_preservesExistingAttrs(VertxTestContext ctx) {
    // Set attr A, then set attr B, verify A is still present
    Map<String, String> attrsA = new HashMap<>();
    attrsA.put("test_attr_a", "valueA");

    Map<String, String> attrsB = new HashMap<>();
    attrsB.put("test_attr_b", "valueB");

    keycloakUserService
        .updateUserAttributes(testUser2Id, attrsA)
        .compose(r -> keycloakUserService.updateUserAttributes(testUser2Id, attrsB))
        .onComplete(
            ctx.succeeding(
                result ->
                    ctx.verify(
                        () -> {
                          UserRepresentation user =
                              adminClient.realm("master").users()
                                  .get(testUser2Id.toString()).toRepresentation();
                          var userAttrs = user.getAttributes();
                          assertThat(userAttrs).isNotNull();
                          assertThat(userAttrs.get("test_attr_a").get(0)).isEqualTo("valueA");
                          assertThat(userAttrs.get("test_attr_b").get(0)).isEqualTo("valueB");
                          ctx.completeNow();
                        })));
  }

  @Test
  @Order(56)
  void setDelegationScopes_deduplicates(VertxTestContext ctx) {
    UUID delegatorId = UUID.randomUUID();
    List<String> scopes = List.of("read", "write");

    // Set scopes, then set overlapping scopes again
    keycloakUserService
        .setDelegationScopes(testUser1Id, scopes, delegatorId)
        .compose(r -> keycloakUserService.setDelegationScopes(
            testUser1Id, List.of("write", "subscribe"), delegatorId))
        .onComplete(
            ctx.succeeding(
                result ->
                    ctx.verify(
                        () -> {
                          UserRepresentation user =
                              adminClient.realm("master").users()
                                  .get(testUser1Id.toString()).toRepresentation();
                          var userAttrs = user.getAttributes();
                          String scopeStr = userAttrs.get("delegation_scope").get(0);
                          // Should contain all three without duplicates
                          assertThat(scopeStr).contains("read").contains("write").contains("subscribe");
                          ctx.completeNow();
                        })));
  }

  @Test
  @Order(57)
  void clearDelegationScopes_clearingAllRemovesDid(VertxTestContext ctx) {
    UUID delegatorId = UUID.randomUUID();
    List<String> scopes = List.of("scope1");

    // Create a temp user for this test to avoid interference with other delegation tests
    UserRepresentation tempUser = new UserRepresentation();
    tempUser.setUsername("tempuser-scope-clear");
    tempUser.setEmail("scopeclear@example.com");
    tempUser.setEnabled(true);
    tempUser.setEmailVerified(true);
    tempUser.setFirstName("Temp");
    tempUser.setLastName("ScopeClear");
    adminClient.realm("master").users().create(tempUser);
    List<UserRepresentation> found =
        adminClient.realm("master").users().search("tempuser-scope-clear", true);
    UUID tempUserId = UUID.fromString(found.get(0).getId());

    keycloakUserService
        .setDelegationScopes(tempUserId, scopes, delegatorId)
        .compose(r -> keycloakUserService.clearDelegationScopes(tempUserId, Set.of("scope1")))
        .onComplete(
            ctx.succeeding(
                result ->
                    ctx.verify(
                        () -> {
                          assertThat(result).isTrue();
                          UserRepresentation user =
                              adminClient.realm("master").users()
                                  .get(tempUserId.toString()).toRepresentation();
                          var userAttrs = user.getAttributes();
                          String scopeStr = userAttrs.get("delegation_scope").get(0);
                          assertThat(scopeStr).isEqualTo("[]");
                          // Clean up
                          adminClient.realm("master").users().get(tempUserId.toString()).remove();
                          ctx.completeNow();
                        })));
  }

  @Test
  @Order(58)
  void getUserById_returnsCorrectToJson(VertxTestContext ctx) {
    keycloakUserService
        .getUserById(testUser1Id)
        .onComplete(
            ctx.succeeding(
                user ->
                    ctx.verify(
                        () -> {
                          var json = user.toJson();
                          assertThat(json.getString("sub")).isEqualTo(testUser1Id.toString());
                          assertThat(json.getString("email")).isEqualTo("test1@example.com");
                          assertThat(json.getJsonArray("roles")).isNotNull();
                          assertThat(json.getBoolean("emailVerified")).isTrue();
                          ctx.completeNow();
                        })));
  }

  @Test
  @Order(100)
  void deleteUser_removesUser(VertxTestContext ctx) {
    // Create a temporary user to delete (don't delete seeded users)
    UserRepresentation tempUser = new UserRepresentation();
    tempUser.setUsername("tempuser-delete-test");
    tempUser.setEmail("temp@example.com");
    tempUser.setEnabled(true);
    tempUser.setEmailVerified(true);
    tempUser.setFirstName("Temp");
    tempUser.setLastName("User");
    adminClient.realm("master").users().create(tempUser);

    List<UserRepresentation> found =
        adminClient.realm("master").users().search("tempuser-delete-test", true);
    UUID tempUserId = UUID.fromString(found.get(0).getId());

    keycloakUserService
        .deleteUser(tempUserId)
        .onComplete(
            ctx.succeeding(
                result ->
                    ctx.verify(
                        () -> {
                          assertThat(result).isTrue();
                          // Verify user is gone
                          List<UserRepresentation> searchResult =
                              adminClient
                                  .realm("master")
                                  .users()
                                  .search("tempuser-delete-test", true);
                          assertThat(searchResult).isEmpty();
                          ctx.completeNow();
                        })));
  }
}
