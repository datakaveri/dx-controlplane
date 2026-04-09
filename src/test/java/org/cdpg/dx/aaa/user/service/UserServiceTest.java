package org.cdpg.dx.aaa.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cdpg.dx.testutil.VertxFutureAssert.assertFutureFailure;
import static org.cdpg.dx.testutil.VertxFutureAssert.assertFutureFailureType;
import static org.cdpg.dx.testutil.VertxFutureAssert.assertFutureSuccess;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.cdpg.dx.aaa.credit.service.CreditService;
import org.cdpg.dx.aaa.organization.models.OrganizationCreateRequest;
import org.cdpg.dx.aaa.organization.models.OrganizationJoinRequest;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.aaa.user.dao.CustomRoleDAO;
import org.cdpg.dx.aaa.user.models.CustomRole;
import org.cdpg.dx.aaa.user.models.UserInfo;
import org.cdpg.dx.common.exception.DxInternalServerErrorException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.util.PaginationInfo;
import org.cdpg.dx.database.elastic.model.ElasticsearchResponse;
import org.cdpg.dx.database.elastic.model.QueryModel;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;
import org.cdpg.dx.database.postgres.models.PaginatedResult;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
import org.cdpg.dx.testutil.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
@DisplayName("UserServiceImpl Tests")
class UserServiceTest {

  @Mock private KeycloakUserService keycloakUserService;
  @Mock private OrganizationService organizationService;
  @Mock private CreditService creditService;
  @Mock private ElasticsearchService elasticsearchService;
  @Mock private CustomRoleDAO customRoleDAO;

  private UserServiceImpl userService;

  private static final String DOC_USER_INDEX = "test-user-index";
  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID ORG_ID = UUID.randomUUID();
  private static final UUID REQUESTER_ID = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    userService =
        new UserServiceImpl(
            keycloakUserService,
            organizationService,
            creditService,
            elasticsearchService,
            customRoleDAO,
            DOC_USER_INDEX);
  }

  // ---------------------------------------------------------------------------
  // Helper: build a DxUser with an organisationId set
  // ---------------------------------------------------------------------------
  private static DxUser aDxUserWithOrg(UUID userId, UUID orgId, String... roles) {
    return new DxUser(
        List.of(roles),
        orgId.toString(),    // organisationId
        "Test Org",          // organisationName
        userId,              // sub
        true,                // emailVerified
        false,               // kycVerified
        "Test User",         // name
        "test",              // preferredUsername
        "Test",              // givenName
        "User",              // familyName
        "test@example.com",  // email
        List.of(),           // pendingRoles
        null,                // organisation
        null,                // createdAt
        null,                // kycData
        null,                // twitter_account
        null,                // linkedin_account
        null,                // github_account
        true,                // account_enabled
        null,                // did
        null,                // aud
        new JsonArray()      // scopes
    );
  }

  // ---------------------------------------------------------------------------
  // 1. getUserInfo(DxUser)
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("getUserInfo(DxUser)")
  class GetUserInfoByDxUser {

    @Test
    @DisplayName("should enrich user with pending provider and compute roles when both are pending")
    void success_bothPending(VertxTestContext ctx) {
      DxUser dxUser = aDxUserWithOrg(USER_ID, ORG_ID, "consumer");

      when(organizationService.hasPendingProviderRole(USER_ID, ORG_ID))
          .thenReturn(Future.succeededFuture(true));
      when(creditService.hasPendingComputeRequest(USER_ID))
          .thenReturn(Future.succeededFuture(true));
      when(organizationService.getOrganizationJoinRequestsByUser(USER_ID))
          .thenReturn(Future.succeededFuture(Collections.emptyList()));
      when(organizationService.getOrganizationCreateRequestsByUserId(USER_ID))
          .thenReturn(Future.succeededFuture(Collections.emptyList()));

      Future<DxUser> future = userService.getUserInfo(dxUser);

      assertFutureSuccess(
          future,
          ctx,
          enriched -> {
            assertThat(enriched.pendingRoles()).containsExactlyInAnyOrder("provider", "compute");
            assertThat(enriched.sub()).isEqualTo(USER_ID);
            assertThat(enriched.roles()).containsExactly("consumer");
          });
    }

    @Test
    @DisplayName("should return empty pending roles when nothing is pending")
    void success_noPendingRoles(VertxTestContext ctx) {
      DxUser dxUser = aDxUserWithOrg(USER_ID, ORG_ID, "consumer");

      when(organizationService.hasPendingProviderRole(USER_ID, ORG_ID))
          .thenReturn(Future.succeededFuture(false));
      when(creditService.hasPendingComputeRequest(USER_ID))
          .thenReturn(Future.succeededFuture(false));
      when(organizationService.getOrganizationJoinRequestsByUser(USER_ID))
          .thenReturn(Future.succeededFuture(Collections.emptyList()));
      when(organizationService.getOrganizationCreateRequestsByUserId(USER_ID))
          .thenReturn(Future.succeededFuture(Collections.emptyList()));

      Future<DxUser> future = userService.getUserInfo(dxUser);

      assertFutureSuccess(
          future,
          ctx,
          enriched -> {
            assertThat(enriched.pendingRoles()).isEmpty();
          });
    }

    @Test
    @DisplayName("should skip provider check when organisationId is null")
    void success_noOrgId(VertxTestContext ctx) {
      DxUser dxUser = TestDataFactory.aDxUser(USER_ID, "consumer");
      // dxUser from TestDataFactory has null organisationId

      when(creditService.hasPendingComputeRequest(USER_ID))
          .thenReturn(Future.succeededFuture(true));
      when(organizationService.getOrganizationJoinRequestsByUser(USER_ID))
          .thenReturn(Future.succeededFuture(Collections.emptyList()));
      when(organizationService.getOrganizationCreateRequestsByUserId(USER_ID))
          .thenReturn(Future.succeededFuture(Collections.emptyList()));

      Future<DxUser> future = userService.getUserInfo(dxUser);

      assertFutureSuccess(
          future,
          ctx,
          enriched -> {
            // provider should be false (defaulted), compute should be true
            assertThat(enriched.pendingRoles()).containsExactly("compute");
            verify(organizationService, never()).hasPendingProviderRole(any(), any());
          });
    }

    @Test
    @DisplayName("should attach organisation create request info when present")
    void success_withOrgCreateRequest(VertxTestContext ctx) {
      DxUser dxUser = aDxUserWithOrg(USER_ID, ORG_ID, "consumer");

      OrganizationCreateRequest createReq = TestDataFactory.anOrgCreateRequest(UUID.randomUUID(), USER_ID);

      when(organizationService.hasPendingProviderRole(USER_ID, ORG_ID))
          .thenReturn(Future.succeededFuture(false));
      when(creditService.hasPendingComputeRequest(USER_ID))
          .thenReturn(Future.succeededFuture(false));
      when(organizationService.getOrganizationJoinRequestsByUser(USER_ID))
          .thenReturn(Future.succeededFuture(Collections.emptyList()));
      when(organizationService.getOrganizationCreateRequestsByUserId(USER_ID))
          .thenReturn(Future.succeededFuture(List.of(createReq)));

      Future<DxUser> future = userService.getUserInfo(dxUser);

      assertFutureSuccess(
          future,
          ctx,
          enriched -> {
            assertThat(enriched.organisation()).isNotNull();
            assertThat(enriched.organisation().getString("request_type"))
                .isEqualTo("organisation_create");
          });
    }

    @Test
    @DisplayName("should attach organisation join request info when no create request exists")
    void success_withOrgJoinRequest(VertxTestContext ctx) {
      DxUser dxUser = aDxUserWithOrg(USER_ID, ORG_ID, "consumer");

      OrganizationJoinRequest joinReq = TestDataFactory.anOrgJoinRequest(ORG_ID, USER_ID);

      when(organizationService.hasPendingProviderRole(USER_ID, ORG_ID))
          .thenReturn(Future.succeededFuture(false));
      when(creditService.hasPendingComputeRequest(USER_ID))
          .thenReturn(Future.succeededFuture(false));
      when(organizationService.getOrganizationJoinRequestsByUser(USER_ID))
          .thenReturn(Future.succeededFuture(List.of(joinReq)));
      when(organizationService.getOrganizationCreateRequestsByUserId(USER_ID))
          .thenReturn(Future.succeededFuture(Collections.emptyList()));

      Future<DxUser> future = userService.getUserInfo(dxUser);

      assertFutureSuccess(
          future,
          ctx,
          enriched -> {
            assertThat(enriched.organisation()).isNotNull();
            assertThat(enriched.organisation().getString("request_type"))
                .isEqualTo("organisation_join");
          });
    }
  }

  // ---------------------------------------------------------------------------
  // 2. getUserInfoByID(UUID)
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("getUserInfoByID(UUID)")
  class GetUserInfoById {

    @Test
    @DisplayName("should fetch user from Keycloak and enrich with pending roles")
    void success(VertxTestContext ctx) {
      DxUser kcUser = TestDataFactory.aDxUser(USER_ID, "consumer");

      when(keycloakUserService.getUserById(USER_ID))
          .thenReturn(Future.succeededFuture(kcUser));
      when(creditService.hasPendingComputeRequest(USER_ID))
          .thenReturn(Future.succeededFuture(false));
      when(organizationService.getOrganizationJoinRequestsByUser(USER_ID))
          .thenReturn(Future.succeededFuture(Collections.emptyList()));
      when(organizationService.getOrganizationCreateRequestsByUserId(USER_ID))
          .thenReturn(Future.succeededFuture(Collections.emptyList()));

      Future<DxUser> future = userService.getUserInfoByID(USER_ID);

      assertFutureSuccess(
          future,
          ctx,
          enriched -> {
            assertThat(enriched.sub()).isEqualTo(USER_ID);
            verify(keycloakUserService).getUserById(USER_ID);
          });
    }

    @Test
    @DisplayName("should propagate failure when user is not found in Keycloak")
    void failure_userNotFound(VertxTestContext ctx) {
      when(keycloakUserService.getUserById(USER_ID))
          .thenReturn(Future.failedFuture(new DxNotFoundException("User not found")));

      Future<DxUser> future = userService.getUserInfoByID(USER_ID);

      assertFutureFailureType(future, ctx, DxNotFoundException.class);
    }
  }

  // ---------------------------------------------------------------------------
  // 3. createUserInfo(UserInfo)
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("createUserInfo(UserInfo)")
  class CreateUserInfo {

    @Test
    @DisplayName("should store user info in Elasticsearch and complete successfully")
    void success(VertxTestContext ctx) {
      UserInfo userInfo = buildUserInfo();

      when(elasticsearchService.createDocuments(eq(DOC_USER_INDEX), anyList()))
          .thenReturn(Future.succeededFuture(List.of("doc-id-1")));

      Future<Void> future = userService.createUserInfo(userInfo);

      assertFutureSuccess(
          future,
          ctx,
          v -> {
            verify(elasticsearchService).createDocuments(eq(DOC_USER_INDEX), anyList());
          });
    }

    @Test
    @DisplayName("should propagate failure when Elasticsearch write fails")
    void failure_esFails(VertxTestContext ctx) {
      UserInfo userInfo = buildUserInfo();

      when(elasticsearchService.createDocuments(eq(DOC_USER_INDEX), anyList()))
          .thenReturn(Future.failedFuture(new RuntimeException("ES unavailable")));

      Future<Void> future = userService.createUserInfo(userInfo);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).hasMessageContaining("ES unavailable");
          });
    }

    private UserInfo buildUserInfo() {
      JsonObject json =
          new JsonObject()
              .put("userId", USER_ID.toString())
              .put("about", "test about")
              .put("experience", new JsonArray())
              .put("education", new JsonArray())
              .put("projects", new JsonArray())
              .put("publications", new JsonArray())
              .put("skills", new JsonArray());
      return UserInfo.fromJson(json);
    }
  }

  // ---------------------------------------------------------------------------
  // 4. updateUserInfo(UUID, Boolean)
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("updateUserInfo(UUID, Boolean)")
  class UpdateUserInfo {

    @Test
    @DisplayName("should fetch existing user and update account_enabled attribute")
    void success(VertxTestContext ctx) {
      DxUser existingUser = TestDataFactory.aDxUser(USER_ID, "consumer");

      when(keycloakUserService.getUserById(USER_ID))
          .thenReturn(Future.succeededFuture(existingUser));
      when(keycloakUserService.updateUserAttributes(
              eq(USER_ID), eq(Map.of("account_enabled", "false"))))
          .thenReturn(Future.succeededFuture(true));

      Future<DxUser> future = userService.updateUserInfo(USER_ID, false);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result.sub()).isEqualTo(USER_ID);
            verify(keycloakUserService).getUserById(USER_ID);
            verify(keycloakUserService)
                .updateUserAttributes(USER_ID, Map.of("account_enabled", "false"));
          });
    }

    @Test
    @DisplayName("should propagate failure when getUserById fails")
    void failure_getUserFails(VertxTestContext ctx) {
      when(keycloakUserService.getUserById(USER_ID))
          .thenReturn(Future.failedFuture(new DxNotFoundException("Not found")));

      Future<DxUser> future = userService.updateUserInfo(USER_ID, true);

      assertFutureFailureType(future, ctx, DxNotFoundException.class);
    }
  }

  // ---------------------------------------------------------------------------
  // 5. addCustomRoleAndScope(JsonObject)
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("addCustomRoleAndScope(JsonObject)")
  class AddCustomRoleAndScope {

    @Test
    @DisplayName("should create custom role, assign KC role and scopes when all succeed")
    void success_withScopes(VertxTestContext ctx) {
      UUID customRoleId = UUID.randomUUID();
      JsonArray scopes = new JsonArray().add("scope:read").add("scope:write");

      CustomRole created =
          new CustomRole(customRoleId, USER_ID, scopes, REQUESTER_ID, null, null);

      JsonObject body =
          new JsonObject()
              .put("id", customRoleId.toString())
              .put("user_id", USER_ID.toString())
              .put("scope", scopes)
              .put("requested_by", REQUESTER_ID.toString());

      when(customRoleDAO.create(any(CustomRole.class)))
          .thenReturn(Future.succeededFuture(created));
      when(keycloakUserService.addCustomRoleToUser(USER_ID, "custom_role"))
          .thenReturn(Future.succeededFuture(true));
      when(keycloakUserService.setCustomScopeToUser(eq(USER_ID), anyList()))
          .thenReturn(Future.succeededFuture(true));

      Future<CustomRole> future = userService.addCustomRoleAndScope(body);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result.userId()).isEqualTo(USER_ID);
            assertThat(result.scope()).hasSize(2);
            verify(keycloakUserService).addCustomRoleToUser(USER_ID, "custom_role");
            verify(keycloakUserService).setCustomScopeToUser(eq(USER_ID), anyList());
          });
    }

    @Test
    @DisplayName("should succeed without setting scopes when scope list is empty")
    void success_withoutScopes(VertxTestContext ctx) {
      UUID customRoleId = UUID.randomUUID();

      CustomRole created =
          new CustomRole(customRoleId, USER_ID, new JsonArray(), REQUESTER_ID, null, null);

      JsonObject body =
          new JsonObject()
              .put("id", customRoleId.toString())
              .put("user_id", USER_ID.toString())
              .put("scope", new JsonArray())
              .put("requested_by", REQUESTER_ID.toString());

      when(customRoleDAO.create(any(CustomRole.class)))
          .thenReturn(Future.succeededFuture(created));
      when(keycloakUserService.addCustomRoleToUser(USER_ID, "custom_role"))
          .thenReturn(Future.succeededFuture(true));

      Future<CustomRole> future = userService.addCustomRoleAndScope(body);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result.userId()).isEqualTo(USER_ID);
            verify(keycloakUserService, never()).setCustomScopeToUser(any(), anyList());
          });
    }

    @Test
    @DisplayName("should fail when Keycloak role assignment returns false")
    void failure_kcRoleAssignmentFails(VertxTestContext ctx) {
      UUID customRoleId = UUID.randomUUID();
      JsonArray scopes = new JsonArray().add("scope:read");

      CustomRole created =
          new CustomRole(customRoleId, USER_ID, scopes, REQUESTER_ID, null, null);

      JsonObject body =
          new JsonObject()
              .put("id", customRoleId.toString())
              .put("user_id", USER_ID.toString())
              .put("scope", scopes)
              .put("requested_by", REQUESTER_ID.toString());

      when(customRoleDAO.create(any(CustomRole.class)))
          .thenReturn(Future.succeededFuture(created));
      when(keycloakUserService.addCustomRoleToUser(USER_ID, "custom_role"))
          .thenReturn(Future.succeededFuture(false));

      Future<CustomRole> future = userService.addCustomRoleAndScope(body);

      assertFutureFailureType(future, ctx, DxInternalServerErrorException.class);
    }

    @Test
    @DisplayName("should fail when Keycloak scope assignment returns false")
    void failure_kcScopeAssignmentFails(VertxTestContext ctx) {
      UUID customRoleId = UUID.randomUUID();
      JsonArray scopes = new JsonArray().add("scope:read");

      CustomRole created =
          new CustomRole(customRoleId, USER_ID, scopes, REQUESTER_ID, null, null);

      JsonObject body =
          new JsonObject()
              .put("id", customRoleId.toString())
              .put("user_id", USER_ID.toString())
              .put("scope", scopes)
              .put("requested_by", REQUESTER_ID.toString());

      when(customRoleDAO.create(any(CustomRole.class)))
          .thenReturn(Future.succeededFuture(created));
      when(keycloakUserService.addCustomRoleToUser(USER_ID, "custom_role"))
          .thenReturn(Future.succeededFuture(true));
      when(keycloakUserService.setCustomScopeToUser(eq(USER_ID), anyList()))
          .thenReturn(Future.succeededFuture(false));

      Future<CustomRole> future = userService.addCustomRoleAndScope(body);

      assertFutureFailureType(future, ctx, DxInternalServerErrorException.class);
    }
  }

  // ---------------------------------------------------------------------------
  // 6. getAllCustomRoles(PaginatedRequest)
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("getAllCustomRoles(PaginatedRequest)")
  class GetAllCustomRoles {

    @Test
    @DisplayName("should delegate to customRoleDAO and return paginated result")
    void success(VertxTestContext ctx) {
      PaginatedRequest request = new PaginatedRequest(1, 10, null, null, null);

      CustomRole role1 =
          new CustomRole(UUID.randomUUID(), USER_ID, new JsonArray().add("scope:read"),
              REQUESTER_ID, null, null);
      CustomRole role2 =
          new CustomRole(UUID.randomUUID(), USER_ID, new JsonArray().add("scope:write"),
              REQUESTER_ID, null, null);

      PaginationInfo pageInfo = PaginationInfo.from(1, 10, 2);
      PaginatedResult<CustomRole> expected =
          new PaginatedResult<>(pageInfo, List.of(role1, role2));

      when(customRoleDAO.getAllWithFilters(request))
          .thenReturn(Future.succeededFuture(expected));

      Future<PaginatedResult<CustomRole>> future = userService.getAllCustomRoles(request);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result.data()).hasSize(2);
            assertThat(result.paginationInfo().getTotalCount()).isEqualTo(2);
            verify(customRoleDAO).getAllWithFilters(request);
          });
    }

    @Test
    @DisplayName("should return empty list when no custom roles exist")
    void success_empty(VertxTestContext ctx) {
      PaginatedRequest request = new PaginatedRequest(1, 10, null, null, null);

      PaginationInfo pageInfo = PaginationInfo.from(1, 10, 0);
      PaginatedResult<CustomRole> expected =
          new PaginatedResult<CustomRole>(pageInfo, Collections.emptyList());

      when(customRoleDAO.getAllWithFilters(request))
          .thenReturn(Future.succeededFuture(expected));

      Future<PaginatedResult<CustomRole>> future = userService.getAllCustomRoles(request);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result.data()).isEmpty();
          });
    }
  }

  // ---------------------------------------------------------------------------
  // 7. deleteScope(UUID, JsonObject)
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("deleteScope(UUID, JsonObject)")
  class DeleteScope {

    @Test
    @DisplayName("should delete by requestId when requestId is provided and requester matches")
    void success_byRequestId(VertxTestContext ctx) {
      UUID requestId = UUID.randomUUID();
      JsonArray scopes = new JsonArray().add("scope:read").add("scope:write");

      CustomRole customRole =
          new CustomRole(requestId, USER_ID, scopes, REQUESTER_ID, null, null);

      JsonObject body = new JsonObject().put("requestId", requestId.toString());

      when(customRoleDAO.get(requestId))
          .thenReturn(Future.succeededFuture(customRole));
      when(keycloakUserService.clearDelegationScopes(eq(USER_ID), any(Set.class)))
          .thenReturn(Future.succeededFuture(true));
      when(customRoleDAO.delete(requestId))
          .thenReturn(Future.succeededFuture(true));

      Future<Boolean> future = userService.deleteScope(REQUESTER_ID, body);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isTrue();
            verify(keycloakUserService).clearDelegationScopes(eq(USER_ID), any(Set.class));
            verify(customRoleDAO).delete(requestId);
          });
    }

    @Test
    @DisplayName("should fail when requestId is provided but requester does not match")
    void failure_unauthorizedRequester(VertxTestContext ctx) {
      UUID requestId = UUID.randomUUID();
      UUID differentRequester = UUID.randomUUID();
      JsonArray scopes = new JsonArray().add("scope:read");

      CustomRole customRole =
          new CustomRole(requestId, USER_ID, scopes, differentRequester, null, null);

      JsonObject body = new JsonObject().put("requestId", requestId.toString());

      when(customRoleDAO.get(requestId))
          .thenReturn(Future.succeededFuture(customRole));

      Future<Boolean> future = userService.deleteScope(REQUESTER_ID, body);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err.getMessage()).contains("Unauthorized");
          });
    }

    @Test
    @DisplayName("should delete specific scope by userId+scope and remove row when no scopes remain")
    void success_byUserIdAndScope_lastScope(VertxTestContext ctx) {
      UUID customRoleId = UUID.randomUUID();
      JsonArray scopes = new JsonArray().add("scope:read");

      CustomRole customRole =
          new CustomRole(customRoleId, USER_ID, scopes, REQUESTER_ID, null, null);

      JsonObject body =
          new JsonObject()
              .put("userId", USER_ID.toString())
              .put("scope", "scope:read");

      when(customRoleDAO.getAllWithFilters(anyMap()))
          .thenReturn(Future.succeededFuture(List.of(customRole)));
      when(keycloakUserService.clearDelegationScopes(eq(USER_ID), eq(Set.of("scope:read"))))
          .thenReturn(Future.succeededFuture(true));
      when(customRoleDAO.delete(customRoleId))
          .thenReturn(Future.succeededFuture(true));

      Future<Boolean> future = userService.deleteScope(REQUESTER_ID, body);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isTrue();
            verify(customRoleDAO).delete(customRoleId);
          });
    }

    @Test
    @DisplayName("should update remaining scopes when deleting one of multiple scopes")
    void success_byUserIdAndScope_remainingScopes(VertxTestContext ctx) {
      UUID customRoleId = UUID.randomUUID();
      JsonArray scopes = new JsonArray().add("scope:read").add("scope:write");

      CustomRole customRole =
          new CustomRole(customRoleId, USER_ID, scopes, REQUESTER_ID, null, null);

      // After update, the updated role is returned
      CustomRole updatedRole =
          new CustomRole(
              customRoleId, USER_ID, new JsonArray().add("scope:write"),
              REQUESTER_ID, null, null);

      JsonObject body =
          new JsonObject()
              .put("userId", USER_ID.toString())
              .put("scope", "scope:read");

      when(customRoleDAO.getAllWithFilters(anyMap()))
          .thenReturn(Future.succeededFuture(List.of(customRole)));
      when(keycloakUserService.clearDelegationScopes(eq(USER_ID), eq(Set.of("scope:read"))))
          .thenReturn(Future.succeededFuture(true));
      // updateCustomScope internally calls customRoleDAO.get then customRoleDAO.update
      when(customRoleDAO.get(customRoleId))
          .thenReturn(Future.succeededFuture(customRole));
      when(customRoleDAO.update(anyMap(), anyMap()))
          .thenReturn(Future.succeededFuture(updatedRole));
      when(keycloakUserService.setCustomScopeToUser(eq(USER_ID), eq(List.of("scope:write"))))
          .thenReturn(Future.succeededFuture(true));

      Future<Boolean> future = userService.deleteScope(REQUESTER_ID, body);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isTrue();
          });
    }

    @Test
    @DisplayName("should fail validation when neither requestId nor userId+scope is provided")
    void failure_missingParams(VertxTestContext ctx) {
      JsonObject body = new JsonObject();

      Future<Boolean> future = userService.deleteScope(REQUESTER_ID, body);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err.getMessage()).contains("requestId");
          });
    }

    @Test
    @DisplayName("should fail when scope to delete is not found in existing scopes")
    void failure_scopeNotFound(VertxTestContext ctx) {
      UUID customRoleId = UUID.randomUUID();
      JsonArray scopes = new JsonArray().add("scope:read");

      CustomRole customRole =
          new CustomRole(customRoleId, USER_ID, scopes, REQUESTER_ID, null, null);

      JsonObject body =
          new JsonObject()
              .put("userId", USER_ID.toString())
              .put("scope", "scope:nonexistent");

      when(customRoleDAO.getAllWithFilters(anyMap()))
          .thenReturn(Future.succeededFuture(List.of(customRole)));

      Future<Boolean> future = userService.deleteScope(REQUESTER_ID, body);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err.getMessage()).contains("Scope not found");
          });
    }
  }

  // ---------------------------------------------------------------------------
  // 8. getCustomRoleRequestByRequester(UUID)
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("getCustomRoleRequestByRequester(UUID)")
  class GetCustomRoleRequestByRequester {

    @Test
    @DisplayName("should return custom roles for requester")
    void success(VertxTestContext ctx) {
      CustomRole role =
          new CustomRole(
              UUID.randomUUID(), USER_ID, new JsonArray().add("scope:read"),
              REQUESTER_ID, null, null);

      when(customRoleDAO.getAllWithFilters(anyMap()))
          .thenReturn(Future.succeededFuture(List.of(role)));

      Future<List<CustomRole>> future =
          userService.getCustomRoleRequestByRequester(REQUESTER_ID);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().requestedBy()).isEqualTo(REQUESTER_ID);
          });
    }

    @Test
    @DisplayName("should fail with DxNotFoundException when no custom roles exist for requester")
    void failure_notFound(VertxTestContext ctx) {
      when(customRoleDAO.getAllWithFilters(anyMap()))
          .thenReturn(Future.succeededFuture(Collections.emptyList()));

      Future<List<CustomRole>> future =
          userService.getCustomRoleRequestByRequester(REQUESTER_ID);

      assertFutureFailureType(future, ctx, DxNotFoundException.class);
    }
  }

  // ---------------------------------------------------------------------------
  // 9. updateCustomScope(UUID, JsonArray)
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("updateCustomScope(UUID, JsonArray)")
  class UpdateCustomScope {

    @Test
    @DisplayName("should update scope in DB and Keycloak")
    void success(VertxTestContext ctx) {
      UUID requestId = UUID.randomUUID();
      JsonArray newScopes = new JsonArray().add("scope:admin");

      CustomRole existingRole =
          new CustomRole(requestId, USER_ID, new JsonArray().add("scope:read"),
              REQUESTER_ID, null, null);
      CustomRole updatedRole =
          new CustomRole(requestId, USER_ID, newScopes, REQUESTER_ID, null, null);

      when(customRoleDAO.get(requestId))
          .thenReturn(Future.succeededFuture(existingRole));
      when(customRoleDAO.update(anyMap(), anyMap()))
          .thenReturn(Future.succeededFuture(updatedRole));
      when(keycloakUserService.setCustomScopeToUser(eq(USER_ID), eq(List.of("scope:admin"))))
          .thenReturn(Future.succeededFuture(true));

      Future<CustomRole> future = userService.updateCustomScope(requestId, newScopes);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result.scope()).contains("scope:admin");
            verify(keycloakUserService).setCustomScopeToUser(USER_ID, List.of("scope:admin"));
          });
    }

    @Test
    @DisplayName("should fail when custom role not found")
    void failure_notFound(VertxTestContext ctx) {
      UUID requestId = UUID.randomUUID();

      when(customRoleDAO.get(requestId))
          .thenReturn(Future.succeededFuture(null));

      Future<CustomRole> future =
          userService.updateCustomScope(requestId, new JsonArray().add("scope:x"));

      assertFutureFailureType(future, ctx, DxNotFoundException.class);
    }
  }

  // ---------------------------------------------------------------------------
  // 10. getCustomRoleByUserAndRequester(UUID, UUID)
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("getCustomRoleByUserAndRequester(UUID, UUID)")
  class GetCustomRoleByUserAndRequester {

    @Test
    @DisplayName("should return the first matching custom role")
    void success(VertxTestContext ctx) {
      CustomRole role =
          new CustomRole(
              UUID.randomUUID(), USER_ID, new JsonArray().add("scope:read"),
              REQUESTER_ID, null, null);

      when(customRoleDAO.getAllWithFilters(anyMap()))
          .thenReturn(Future.succeededFuture(List.of(role)));

      Future<CustomRole> future =
          userService.getCustomRoleByUserAndRequester(USER_ID, REQUESTER_ID);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result.userId()).isEqualTo(USER_ID);
            assertThat(result.requestedBy()).isEqualTo(REQUESTER_ID);
          });
    }

    @Test
    @DisplayName("should fail with DxNotFoundException when no matching role exists")
    void failure_notFound(VertxTestContext ctx) {
      when(customRoleDAO.getAllWithFilters(anyMap()))
          .thenReturn(Future.succeededFuture(Collections.emptyList()));

      Future<CustomRole> future =
          userService.getCustomRoleByUserAndRequester(USER_ID, REQUESTER_ID);

      assertFutureFailureType(future, ctx, DxNotFoundException.class);
    }
  }
}
