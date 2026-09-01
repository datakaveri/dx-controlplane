package org.cdpg.dx.acl.policy.service;

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
import java.util.List;
import java.util.UUID;
import org.cdpg.dx.aaa.common.ResponseModel;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.item.util.GetItemRequest;
import org.cdpg.dx.acl.accessRequest.dao.AccessRequestDao;
import org.cdpg.dx.acl.policy.dao.PolicyDao;
import org.cdpg.dx.acl.policy.dao.model.PolicyDto;
import org.cdpg.dx.acl.policy.dao.model.VerifyPolicyDto;
import org.cdpg.dx.acl.policy.service.impl.PolicyServiceImpl;
import org.cdpg.dx.acl.policy.service.model.CreatePolicyRequest;
import org.cdpg.dx.acl.rule.dao.AccessRuleDao;
import org.cdpg.dx.catalogueService.models.ItemType;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.common.model.ResourceObj;
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
@DisplayName("PolicyServiceImpl Tests")
class PolicyServiceTest {

  @Mock private PolicyDao policyDao;
  @Mock private AccessRuleDao accessRuleDao;
  @Mock private AccessRequestDao accessRequestDao;
  @Mock private ItemService itemService;
  @Mock private KeycloakUserService keycloakUserService;

  private PolicyServiceImpl policyService;

  private static final String APD_URL = "https://apd.example.com";

  // Common test UUIDs
  private final UUID userId = UUID.randomUUID();
  private final UUID itemId = UUID.randomUUID();
  private final UUID consumerId = UUID.randomUUID();
  private final UUID orgId = UUID.randomUUID();
  private final UUID policyId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    policyService = new PolicyServiceImpl(itemService, keycloakUserService, policyDao,
        accessRuleDao, accessRequestDao, APD_URL);
  }

  private DxUser createProviderUser(UUID sub, UUID orgId) {
    return new DxUser(
        List.of("provider"),
        orgId.toString(),
        "TestOrg",
        sub,
        true,
        true,
        "Test User",
        "testuser",
        "Test",
        "User",
        "test@example.com",
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
        null,
        null,
        null);
  }

  private DxUser createConsumerUser(UUID sub, UUID orgId) {
    return new DxUser(
        List.of("consumer"),
        orgId.toString(),
        "TestOrg",
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
        null,
        null,
        null);
  }

  private ResponseModel createMockResponseModel(UUID itemId, UUID providerId) {
    // Build a fake JsonObject that looks like a catalogue item
    JsonObject itemJson =
        new JsonObject()
            .put("id", itemId.toString())
            .put("type", new JsonArray().add("adex:DataBank"))
            .put("ownerUserId", providerId.toString())
            .put("organizationId", orgId.toString())
            .put("apdURL", APD_URL)
            .put(
                "resourceServer",
                new JsonArray().add(new JsonObject().put("url", "https://rs.example.com")));

    // We need to create a ResponseModel. Since its constructor requires ElasticsearchResponse
    // objects, we mock the ResponseModel itself and configure getElasticsearchResponses.
    ResponseModel responseModel = org.mockito.Mockito.mock(ResponseModel.class);
    when(responseModel.getElasticsearchResponses()).thenReturn(List.of(itemJson));
    return responseModel;
  }

  // =========================================================================
  // createPolicy
  // =========================================================================
  @Nested
  @DisplayName("createPolicy")
  class CreatePolicyTests {

    @Test
    @DisplayName("should create policy and access rules when user is owner of item")
    void createPolicy_success(VertxTestContext ctx) {
      DxUser provider = createProviderUser(userId, orgId);

      // Mock item lookup
      ResponseModel responseModel = createMockResponseModel(itemId, userId);
      when(itemService.getItem(any(GetItemRequest.class)))
          .thenReturn(Future.succeededFuture(responseModel));

      // Mock existing policy check: no duplicates
      QueryResult emptyResult = new QueryResult();
      emptyResult.setRows(new JsonArray());
      when(policyDao.checkExistingPoliciesForIds(anyList(), eq(userId)))
          .thenReturn(Future.succeededFuture(emptyResult));

      // Mock insert
      QueryResult insertResult = new QueryResult();
      insertResult.setRows(
          new JsonArray()
              .add(
                  new JsonObject()
                      .put("_id", policyId.toString())
                      .put("user_emailid", "consumer@example.com")
                      .put("item_id", itemId.toString())
                      .put("expiry_at", "2030-01-01T00:00:00")
                      .put("owner_id", userId.toString())));
      when(policyDao.insertPolicies(anyList(), eq(userId)))
          .thenReturn(Future.succeededFuture(List.of(insertResult)));

      // Build a CreatePolicyRequest
      CreatePolicyRequest req = new CreatePolicyRequest();
      req.setUserId(consumerId.toString());
      req.setItemId(itemId.toString());
      req.setItemType(ItemType.DATABANK);
      req.setExpiryTime("2030-01-01T00:00:00");
      req.setConstraints(
          new JsonObject()
              .put(
                  "subjects",
                  new JsonObject().put("allowedUserIds", new JsonArray().add("user1"))));

      when(accessRuleDao.createRule(
              eq(policyId), eq(itemId), eq(userId), any(JsonObject.class), any(JsonObject.class),
              anyString()))
          .thenReturn(Future.succeededFuture());

      // Mock subject conflict check (request has "subjects" constraints)
      when(accessRuleDao.findMatchingRule(eq(itemId), anyList(), anyList(), anyList()))
          .thenReturn(Future.succeededFuture(List.of()));

      policyService
          .createPolicy(List.of(req), provider)
          .onComplete(
              ctx.succeeding(
                  v ->
                      ctx.verify(
                          () -> {
                            verify(policyDao).checkExistingPoliciesForIds(anyList(), eq(userId));
                            verify(policyDao).insertPolicies(anyList(), eq(userId));
                            verify(accessRuleDao)
                                .createRule(
                                    eq(policyId),
                                    eq(itemId),
                                    eq(userId),
                                    any(JsonObject.class),
                                    any(JsonObject.class),
                                    anyString());
                            ctx.completeNow();
                          })));
    }

    @Test
    @DisplayName("should fail when user does not own the item")
    void createPolicy_failNotOwner(VertxTestContext ctx) {
      UUID differentProviderId = UUID.randomUUID();
      DxUser caller = createProviderUser(userId, orgId);

      // Item belongs to a different provider
      ResponseModel responseModel = createMockResponseModel(itemId, differentProviderId);
      when(itemService.getItem(any(GetItemRequest.class)))
          .thenReturn(Future.succeededFuture(responseModel));

      CreatePolicyRequest req = new CreatePolicyRequest();
      req.setUserId(consumerId.toString());
      req.setItemId(itemId.toString());
      req.setItemType(ItemType.DATABANK);
      req.setExpiryTime("2030-01-01T00:00:00");
      req.setConstraints(null);

      policyService
          .createPolicy(List.of(req), caller)
          .onComplete(
              ctx.failing(
                  err ->
                      ctx.verify(
                          () -> {
                            assertThat(err.getMessage()).contains("Access Denied");
                            verify(policyDao, never()).insertPolicies(anyList(), any(UUID.class));
                            ctx.completeNow();
                          })));
    }
  }

  // =========================================================================
  // getPolicy
  // =========================================================================
  @Nested
  @DisplayName("getPolicy")
  class GetPolicyTests {

    @Test
    @DisplayName("should return policies for consumer user")
    void getPolicy_consumerSuccess(VertxTestContext ctx) {
      DxUser consumer = createConsumerUser(userId, orgId);

      JsonObject policyRow =
          new JsonObject()
              .put("policyId", policyId.toString())
              .put("itemId", itemId.toString())
              .put("itemType", "DATABANK")
              .put("status", "ACTIVE")
              .put("expiryAt", "2030-01-01T00:00:00")
              .put("createdAt", "2024-01-01T00:00:00")
              .put("updatedAt", "2024-01-01T00:00:00")
              .put("constraints", new JsonObject())
              .put("consumer_id", userId.toString())
              .put("consumer_emailid", consumer.email())
              .put("consumer_first_name", "Consumer")
              .put("consumer_last_name", "User");

      QueryResult queryResult = new QueryResult();
      queryResult.setRows(new JsonArray().add(policyRow));

      when(policyDao.getPoliciesByConsumer(consumer.email()))
          .thenReturn(Future.succeededFuture(queryResult));

      policyService
          .getPolicy(consumer)
          .onComplete(
              ctx.succeeding(
                  policies ->
                      ctx.verify(
                          () -> {
                            assertThat(policies).isNotEmpty();
                            assertThat(policies).hasSize(1);
                            verify(policyDao).getPoliciesByConsumer(consumer.email());
                            ctx.completeNow();
                          })));
    }

    @Test
    @DisplayName("should return policies for provider user")
    void getPolicy_providerSuccess(VertxTestContext ctx) {
      DxUser provider = createProviderUser(userId, orgId);

      JsonObject policyRow =
          new JsonObject()
              .put("policyId", policyId.toString())
              .put("itemId", itemId.toString())
              .put("itemType", "DATABANK")
              .put("status", "ACTIVE")
              .put("expiryAt", "2030-01-01T00:00:00")
              .put("createdAt", "2024-01-01T00:00:00")
              .put("updatedAt", "2024-01-01T00:00:00")
              .put("constraints", new JsonObject())
              .put("ownerId", userId.toString())
              .put("owner_emailid", "owner@example.com")
              .put("owner_first_name", "Owner")
              .put("owner_last_name", "User");

      QueryResult queryResult = new QueryResult();
      queryResult.setRows(new JsonArray().add(policyRow));

      when(policyDao.getPoliciesByProvider(userId.toString()))
          .thenReturn(Future.succeededFuture(queryResult));

      policyService
          .getPolicy(provider)
          .onComplete(
              ctx.succeeding(
                  policies ->
                      ctx.verify(
                          () -> {
                            assertThat(policies).isNotEmpty();
                            assertThat(policies).hasSize(1);
                            verify(policyDao).getPoliciesByProvider(userId.toString());
                            ctx.completeNow();
                          })));
    }
  }

  // =========================================================================
  // deActivatePolicy
  // =========================================================================
  @Nested
  @DisplayName("deActivatePolicy")
  class DeActivatePolicyTests {

    @Test
    @DisplayName("should successfully deactivate a policy owned by user")
    void deActivatePolicy_success(VertxTestContext ctx) {
      DxUser provider = createProviderUser(userId, orgId);

      // Mock verifyPolicy: returns a row with owner = current user, status = ACTIVE
      JsonObject verifyRow =
          new JsonObject()
              .put("owner_id", userId.toString())
              .put("status", "ACTIVE");
      QueryResult verifyResult = new QueryResult();
      verifyResult.setRows(new JsonArray().add(verifyRow));
      when(policyDao.verifyPolicy(policyId)).thenReturn(Future.succeededFuture(verifyResult));

      // Mock deActivatePolicy: returns a result with a row
      JsonObject deactivateRow =
          new JsonObject()
              .put("_id", policyId.toString())
              .put("status", "INACTIVE");
      QueryResult deactivateResult = new QueryResult();
      deactivateResult.setRows(new JsonArray().add(deactivateRow));
      when(policyDao.deActivatePolicy(policyId))
          .thenReturn(Future.succeededFuture(deactivateResult));

      // Mock access rule update
      QueryResult ruleUpdateResult = new QueryResult();
      ruleUpdateResult.setRows(new JsonArray());
      when(accessRuleDao.updateStatusByPolicyId(policyId, "INACTIVE"))
          .thenReturn(Future.succeededFuture(ruleUpdateResult));

      policyService
          .deActivatePolicy(policyId.toString(), provider)
          .onComplete(
              ctx.succeeding(
                  v ->
                      ctx.verify(
                          () -> {
                            verify(policyDao).verifyPolicy(policyId);
                            verify(accessRuleDao).updateStatusByPolicyId(policyId, "INACTIVE");
                            ctx.completeNow();
                          })));
    }

    @Test
    @DisplayName("should fail when policy does not exist")
    void deActivatePolicy_notFound(VertxTestContext ctx) {
      DxUser provider = createProviderUser(userId, orgId);

      // Mock verifyPolicy: returns empty rows
      QueryResult emptyResult = new QueryResult();
      emptyResult.setRows(new JsonArray());
      when(policyDao.verifyPolicy(policyId)).thenReturn(Future.succeededFuture(emptyResult));

      policyService
          .deActivatePolicy(policyId.toString(), provider)
          .onComplete(
              ctx.failing(
                  err ->
                      ctx.verify(
                          () -> {
                            assertThat(err.getMessage()).contains("doesn't exist");
                            verify(policyDao).verifyPolicy(policyId);
                            verify(policyDao, never()).deActivatePolicy(any(UUID.class));
                            ctx.completeNow();
                          })));
    }
  }

  // =========================================================================
  // initiateVerifyPolicy
  // =========================================================================
  @Nested
  @DisplayName("initiateVerifyPolicy")
  class InitiateVerifyPolicyTests {

    @Test
    @DisplayName("should succeed when an active policy exists")
    void initiateVerifyPolicy_success(VertxTestContext ctx) {
      DxUser consumer = createConsumerUser(userId, orgId);
      UUID ownerId = UUID.randomUUID();
      String userEmail = consumer.email();
      JsonObject constraints = new JsonObject().put("access", new JsonArray().add("api"));
      String expiryAt = "2030-01-01T00:00:00";

      // Mock checkExistingPoliciesForIds: returns a matching policy
      JsonObject policyRow =
          new JsonObject()
              .put("_id", policyId.toString())
              .put("constraints", constraints)
              .put("expiry_at", expiryAt);
      QueryResult existingResult = new QueryResult();
      existingResult.setRows(new JsonArray().add(policyRow));
      when(policyDao.checkExistingPoliciesForIds(itemId, ownerId, userId.toString()))
          .thenReturn(Future.succeededFuture(existingResult));

      when(keycloakUserService.getUserById(userId)).thenReturn(Future.succeededFuture(consumer));
      when(accessRuleDao.findMatchingRule(any(UUID.class), anyString(), anyString(), anyList()))
          .thenReturn(Future.succeededFuture(List.of()));

      policyService
          .initiateVerifyPolicy(ownerId, userId.toString(), itemId, ItemType.DATABANK, consumer)
          .onComplete(
              ctx.succeeding(
                  result ->
                      ctx.verify(
                          () -> {
                            assertThat(result).isNotNull();
                            assertThat(result).hasSize(1);
                            assertThat(result.getFirst().getPolicyId())
                                .isEqualTo(policyId.toString());
                            verify(policyDao)
                                .checkExistingPoliciesForIds(itemId, ownerId, userId.toString());
                            ctx.completeNow();
                          })));
    }
  }
}
