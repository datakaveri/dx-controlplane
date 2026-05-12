package org.cdpg.dx.acl.accessRequest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.cdpg.dx.aaa.common.ResponseModel;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.item.util.GetItemRequest;
import org.cdpg.dx.acl.accessRequest.dao.AccessRequestDao;
import org.cdpg.dx.acl.accessRequest.dao.model.AccessRequestDto;
import org.cdpg.dx.acl.accessRequest.dao.model.Status;
import org.cdpg.dx.acl.accessRequest.service.impl.AccessRequestServiceImpl;
import org.cdpg.dx.acl.policy.dao.PolicyDao;
import org.cdpg.dx.acl.policy.service.model.CreatePolicyRequest;
import org.cdpg.dx.acl.rule.dao.AccessRuleDao;
import org.cdpg.dx.common.exception.DxConflictException;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.exception.DxForbiddenNoAccessException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.exception.DxValidationException;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.common.model.RequestType;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.util.PaginationInfo;
import org.cdpg.dx.database.postgres.models.PaginatedResult;
import org.cdpg.dx.database.postgres.models.QueryResult;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
@DisplayName("AccessRequestServiceImpl Tests")
class AccessRequestServiceTest {

  @Mock private AccessRequestDao accessRequestDao;
  @Mock private PolicyDao policyDao;
  @Mock private AccessRuleDao accessRuleDao;
  @Mock private ItemService itemService;
  @Mock private KeycloakUserService keycloakUserService;

  private AccessRequestServiceImpl accessRequestService;

  // Common test UUIDs
  private final UUID consumerId = UUID.randomUUID();
  private final UUID providerId = UUID.randomUUID();
  private final UUID itemId = UUID.randomUUID();
  private final UUID requestId = UUID.randomUUID();
  private final UUID orgId = UUID.randomUUID();
  private final UUID providerOrgId = UUID.randomUUID();
  private final UUID policyId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    accessRequestService =
        new AccessRequestServiceImpl(
            keycloakUserService, itemService, accessRequestDao, policyDao, accessRuleDao);
  }

  private DxUser createConsumerUser(UUID sub) {
    return new DxUser(
        List.of("consumer"),
        orgId.toString(),
        "ConsumerOrg",
        sub,
        true,
        true,
        "Consumer User",
        "consumer",
        "Consumer",
        "User",
        "consumer@example.com",
        List.of(),
        new JsonObject(),
        null,
        null,
        null,
        null,
        null,
        true,
        null,
        null,
        null);
  }

  private DxUser createFullUser(UUID sub) {
    return new DxUser(
        List.of("consumer"),
        orgId.toString(),
        "ConsumerOrg",
        sub,
        true,
        true,
        "Full User",
        "fulluser",
        "Full",
        "User",
        "consumer@example.com",
        List.of(),
        new JsonObject(),
        null,
        null,
        null,
        null,
        null,
        true,
        null,
        null,
        null);
  }

  private AccessRequestDto createPendingAccessRequest() {
    return new AccessRequestDto()
        .setRequestId(requestId.toString())
        .setStatus(Status.PENDING)
        .setRequestType(RequestType.DOWNLOAD)
        .setConsumerId(consumerId.toString())
        .setConsumerEmail("consumer@example.com")
        .setConsumerFirstName("Consumer")
        .setConsumerLastName("User")
        .setConsumerOrganization("ConsumerOrg")
        .setProviderId(providerId.toString())
        .setItemId(itemId.toString())
        .setAssetName("Test Dataset")
        .setAssetType("adex:DataBank")
        .setItemOrganizationId(providerOrgId.toString())
        .setShortDescription("A test dataset");
  }

  private ResponseModel createMockItemResponseModel(UUID itemId, UUID providerId) {
    JsonObject itemJson =
        new JsonObject()
            .put("id", itemId.toString())
            .put("type", new JsonArray().add("adex:DataBank"))
            .put("ownerUserId", providerId.toString())
            .put("organizationId", providerOrgId.toString())
            .put("name", "Test Dataset")
            .put("short_description", "A test dataset")
            .put(
                "resourceServer",
                new JsonArray()
                    .add(
                        new JsonObject()
                            .put("url", "https://rs.example.com")
                            .put("accessTypes", new JsonArray().add("api").add("file"))));

    ResponseModel responseModel = org.mockito.Mockito.mock(ResponseModel.class);
    when(responseModel.getElasticsearchResponses()).thenReturn(List.of(itemJson));
    return responseModel;
  }

  // =========================================================================
  // createAccessRequest
  // =========================================================================
  @Nested
  @DisplayName("createAccessRequest")
  class CreateAccessRequestTests {

    @Test
    @DisplayName("should create access request successfully when no duplicate exists")
    void createAccessRequest_success(VertxTestContext ctx) {
      DxUser consumer = createConsumerUser(consumerId);
      DxUser fullUser = createFullUser(consumerId);
      JsonObject additionalInfo = new JsonObject().put("reason", "research");
      JsonObject constraints = new JsonObject().put("access", new JsonArray().add("api"));

      // Mock keycloak user lookup
      when(keycloakUserService.getUserById(consumerId))
          .thenReturn(Future.succeededFuture(fullUser));

      // Mock duplicate check: not present
      when(accessRequestDao.isAccessRequestPresent(consumerId, itemId))
          .thenReturn(Future.succeededFuture(false));

      // Mock item lookup
      ResponseModel itemResponse = createMockItemResponseModel(itemId, providerId);
      when(itemService.getItem(any(GetItemRequest.class)))
          .thenReturn(Future.succeededFuture(itemResponse));

      // Mock DAO create
      AccessRequestDto createdDto = createPendingAccessRequest();
      when(accessRequestDao.create(any(AccessRequestDto.class)))
          .thenReturn(Future.succeededFuture(createdDto));

      accessRequestService
          .createAccessRequest(
              consumer.sub(), itemId, RequestType.DOWNLOAD, additionalInfo, constraints)
          .onComplete(
              ctx.succeeding(
                  result ->
                      ctx.verify(
                          () -> {
                            assertThat(result).isNotNull();
                            assertThat(result.getRequestId()).isEqualTo(requestId.toString());
                            assertThat(result.getStatus()).isEqualTo(Status.PENDING);
                            verify(keycloakUserService).getUserById(consumerId);
                            verify(accessRequestDao).isAccessRequestPresent(consumerId, itemId);
                            verify(accessRequestDao).create(any(AccessRequestDto.class));
                            ctx.completeNow();
                          })));
    }

    @Test
    @DisplayName("should fail with conflict when access request already exists")
    void createAccessRequest_duplicate(VertxTestContext ctx) {
      DxUser consumer = createConsumerUser(consumerId);
      DxUser fullUser = createFullUser(consumerId);

      when(keycloakUserService.getUserById(consumerId))
          .thenReturn(Future.succeededFuture(fullUser));

      // Mock duplicate check: already present
      when(accessRequestDao.isAccessRequestPresent(consumerId, itemId))
          .thenReturn(Future.succeededFuture(true));

      accessRequestService
          .createAccessRequest(consumer.sub(), itemId, RequestType.DOWNLOAD, null, null)
          .onComplete(
              ctx.failing(
                  err ->
                      ctx.verify(
                          () -> {
                            assertThat(err).isInstanceOf(DxConflictException.class);
                            assertThat(err.getMessage()).contains("already exists");
                            verify(accessRequestDao, never()).create(any(AccessRequestDto.class));
                            ctx.completeNow();
                          })));
    }
  }

  // =========================================================================
  // approveAccessRequest
  // =========================================================================
  @Nested
  @DisplayName("approveAccessRequest")
  class ApproveAccessRequestTests {

    @Test
    @DisplayName(
        "should approve request, create policy and access rules, and update status to GRANTED")
    void approveAccessRequest_success(VertxTestContext ctx) {
      LocalDateTime expiryAt = LocalDateTime.now().plusDays(30);
      JsonObject constraints =
          new JsonObject()
              .put("access", new JsonArray().add(new JsonObject().put("accessType", "api")))
              .put(
                  "subjects", new JsonObject().put("allowedUserIds", new JsonArray().add("user1")));

      // Mock ownership check
      when(accessRequestDao.ownershipCheck(requestId, providerId, providerOrgId, false))
          .thenReturn(Future.succeededFuture(true));

      // Mock get request
      AccessRequestDto pendingRequest = createPendingAccessRequest();
      when(accessRequestDao.get(requestId)).thenReturn(Future.succeededFuture(pendingRequest));

      // Mock item service for constraint validation
      ResponseModel itemResponse = createMockItemResponseModel(itemId, providerId);
      when(itemService.getItem(any(GetItemRequest.class)))
          .thenReturn(Future.succeededFuture(itemResponse));

      // Mock no existing policy
      QueryResult emptyPolicyResult = new QueryResult();
      emptyPolicyResult.setRows(new JsonArray());
      when(policyDao.checkExistingPoliciesForIds(
              eq(itemId), eq(providerId), eq(consumerId.toString())))
          .thenReturn(Future.succeededFuture(emptyPolicyResult));

      // Mock policy insertion
      QueryResult insertResult = new QueryResult();
      insertResult.setRows(new JsonArray().add(new JsonObject().put("_id", policyId.toString())));
      when(policyDao.insertPolicies(anyList(), eq(providerId)))
          .thenReturn(Future.succeededFuture(List.of(insertResult)));

      // Mock approve status update
      AccessRequestDto grantedDto = createPendingAccessRequest();
      grantedDto.setStatus(Status.GRANTED);
      when(accessRequestDao.approveAccessRequest(eq(requestId), eq("GRANTED"), eq(expiryAt)))
          .thenReturn(Future.succeededFuture(grantedDto));

      // Mock access rule creation
      when(accessRuleDao.createRule(
              eq(policyId),
              eq(itemId),
              eq(providerId),
              any(JsonObject.class),
              any(JsonObject.class),
              anyString()))
          .thenReturn(Future.succeededFuture());

      accessRequestService
          .approveAccessRequest(
              providerId, requestId, expiryAt, providerOrgId, false, constraints, null, null)
          .onComplete(
              ctx.succeeding(
                  result ->
                      ctx.verify(
                          () -> {
                            assertThat(result).isNotNull();
                            assertThat(result.getStatus()).isEqualTo(Status.GRANTED);
                            verify(accessRequestDao)
                                .ownershipCheck(requestId, providerId, providerOrgId, false);
                            verify(policyDao).insertPolicies(anyList(), eq(providerId));
                            verify(accessRequestDao)
                                .approveAccessRequest(requestId, "GRANTED", expiryAt);
                            ctx.completeNow();
                          })));
    }
  }

  // =========================================================================
  // rejectAccessRequest
  // =========================================================================
  @Nested
  @DisplayName("rejectAccessRequest")
  class RejectAccessRequestTests {

    @Test
    @DisplayName("should reject access request and update status to REJECTED")
    void rejectAccessRequest_success(VertxTestContext ctx) {
      // Mock ownership check
      when(accessRequestDao.ownershipCheck(requestId, providerId, providerOrgId, false))
          .thenReturn(Future.succeededFuture(true));

      // Mock get request
      AccessRequestDto pendingRequest = createPendingAccessRequest();
      when(accessRequestDao.get(requestId)).thenReturn(Future.succeededFuture(pendingRequest));

      // Mock deactivate policy (no existing policy)
      QueryResult emptyResult = new QueryResult();
      emptyResult.setRows(new JsonArray());
      when(policyDao.deActivatePolicyByUserAndItem(
              eq(itemId), eq(providerId), eq(consumerId.toString())))
          .thenReturn(Future.succeededFuture(emptyResult));

      // Mock update status
      AccessRequestDto rejectedDto = createPendingAccessRequest();
      rejectedDto.setStatus(Status.REJECTED);
      when(accessRequestDao.update(
              eq(Map.of("request_id", requestId.toString())), eq(Map.of("status", "REJECTED"))))
          .thenReturn(Future.succeededFuture(rejectedDto));

      accessRequestService
          .rejectAccessRequest(
              providerId, requestId, providerOrgId, false, "denied", "not eligible")
          .onComplete(
              ctx.succeeding(
                  result ->
                      ctx.verify(
                          () -> {
                            assertThat(result).isNotNull();
                            assertThat(result.getStatus()).isEqualTo(Status.REJECTED);
                            verify(accessRequestDao)
                                .ownershipCheck(requestId, providerId, providerOrgId, false);
                            verify(accessRequestDao).get(requestId);
                            ctx.completeNow();
                          })));
    }
  }

  // =========================================================================
  // listAccessRequestForProvider / listAccessRequestForConsumer
  // =========================================================================
  @Nested
  @DisplayName("listAccessRequests")
  class ListAccessRequestTests {

    @Test
    @DisplayName("should return paginated access requests for provider")
    void getAccessRequestsForProvider_success(VertxTestContext ctx) {
      PaginatedRequest paginatedRequest = org.mockito.Mockito.mock(PaginatedRequest.class);
      PaginationInfo paginationInfo = new PaginationInfo(1, 10, 1, 1, false, false);
      AccessRequestDto dto = createPendingAccessRequest();
      PaginatedResult<AccessRequestDto> expectedResult =
          new PaginatedResult<>(paginationInfo, List.of(dto));

      when(accessRequestDao.getAllWithFilters(paginatedRequest))
          .thenReturn(Future.succeededFuture(expectedResult));

      accessRequestService
          .listAccessRequestForProvider(paginatedRequest)
          .onComplete(
              ctx.succeeding(
                  result ->
                      ctx.verify(
                          () -> {
                            assertThat(result).isNotNull();
                            assertThat(result.data()).hasSize(1);
                            assertThat(result.data().getFirst().getRequestId())
                                .isEqualTo(requestId.toString());
                            verify(accessRequestDao).getAllWithFilters(paginatedRequest);
                            ctx.completeNow();
                          })));
    }

    @Test
    @DisplayName("should return paginated access requests for consumer")
    void getAccessRequestsForConsumer_success(VertxTestContext ctx) {
      PaginatedRequest paginatedRequest = org.mockito.Mockito.mock(PaginatedRequest.class);
      PaginationInfo paginationInfo = new PaginationInfo(1, 10, 1, 1, false, false);
      AccessRequestDto dto = createPendingAccessRequest();
      PaginatedResult<AccessRequestDto> expectedResult =
          new PaginatedResult<>(paginationInfo, List.of(dto));

      when(accessRequestDao.getAllWithFilters(paginatedRequest))
          .thenReturn(Future.succeededFuture(expectedResult));

      accessRequestService
          .listAccessRequestForConsumer(paginatedRequest)
          .onComplete(
              ctx.succeeding(
                  result ->
                      ctx.verify(
                          () -> {
                            assertThat(result).isNotNull();
                            assertThat(result.data()).hasSize(1);
                            verify(accessRequestDao).getAllWithFilters(paginatedRequest);
                            ctx.completeNow();
                          })));
    }
  }

  // =========================================================================
  // updateAccessRequestForConsumer (withdraw)
  // =========================================================================
  @Nested
  @DisplayName("withdrawAccessRequest")
  class WithdrawAccessRequestTests {

    @Test
    @DisplayName("should withdraw a pending access request owned by the consumer")
    void withdrawAccessRequest_success(VertxTestContext ctx) {
      // Mock get request
      AccessRequestDto pendingRequest = createPendingAccessRequest();
      when(accessRequestDao.get(requestId)).thenReturn(Future.succeededFuture(pendingRequest));

      // Mock update
      AccessRequestDto withdrawnDto = createPendingAccessRequest();
      withdrawnDto.setStatus(Status.WITHDRAWN);
      when(accessRequestDao.update(
              eq(Map.of("request_id", requestId.toString())), eq(Map.of("status", "WITHDRAWN"))))
          .thenReturn(Future.succeededFuture(withdrawnDto));

      accessRequestService
          .updateAccessRequestForConsumer(consumerId, requestId)
          .onComplete(
              ctx.succeeding(
                  result ->
                      ctx.verify(
                          () -> {
                            assertThat(result).isNotNull();
                            assertThat(result.getStatus()).isEqualTo(Status.WITHDRAWN);
                            verify(accessRequestDao).get(requestId);
                            verify(accessRequestDao)
                                .update(
                                    eq(Map.of("request_id", requestId.toString())),
                                    eq(Map.of("status", "WITHDRAWN")));
                            ctx.completeNow();
                          })));
    }
  }

  // =========================================================================
  // checkAccessRequest
  // =========================================================================
  @Nested
  @DisplayName("checkAccessRequest")
  class CheckAccessRequestTests {

    @Test
    @DisplayName("should return true when user has access via policy")
    void checkAccessRequest_hasAccess(VertxTestContext ctx) {
      when(accessRequestDao.hasAccess(consumerId.toString(), itemId.toString()))
          .thenReturn(Future.succeededFuture(true));

      accessRequestService
          .checkAccessRequest(consumerId, itemId.toString())
          .onComplete(
              ctx.succeeding(
                  result ->
                      ctx.verify(
                          () -> {
                            assertThat(result).isTrue();
                            verify(accessRequestDao)
                                .hasAccess(consumerId.toString(), itemId.toString());
                            ctx.completeNow();
                          })));
    }

    @Test
    @DisplayName("should fail when user has no access and no matching rule")
    void checkAccessRequest_noAccess(VertxTestContext ctx) {
      DxUser fullUser = createFullUser(consumerId);

      // hasAccess fails with DxForbiddenNoAccessException
      when(accessRequestDao.hasAccess(consumerId.toString(), itemId.toString()))
          .thenReturn(
              Future.failedFuture(new DxForbiddenNoAccessException("No access policy found")));

      // Keycloak lookup for rule matching
      when(keycloakUserService.getUserById(consumerId))
          .thenReturn(Future.succeededFuture(fullUser));

      // Rule matching fails (no matching rule)
      when(accessRuleDao.ruleMatches(
              eq(itemId), eq(consumerId.toString()), eq(orgId.toString()), eq(List.of("consumer"))))
          .thenReturn(Future.succeededFuture(false));

      accessRequestService
          .checkAccessRequest(consumerId, itemId.toString())
          .onComplete(
              ctx.failing(
                  err ->
                      ctx.verify(
                          () -> {
                            assertThat(err).isInstanceOf(DxForbiddenNoAccessException.class);
                            verify(accessRequestDao)
                                .hasAccess(consumerId.toString(), itemId.toString());
                            verify(keycloakUserService).getUserById(consumerId);
                            verify(accessRuleDao)
                                .ruleMatches(
                                    eq(itemId),
                                    eq(consumerId.toString()),
                                    eq(orgId.toString()),
                                    eq(List.of("consumer")));
                            ctx.completeNow();
                          })));
    }
  }
}
