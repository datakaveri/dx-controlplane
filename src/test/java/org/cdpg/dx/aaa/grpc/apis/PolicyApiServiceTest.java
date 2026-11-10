package org.cdpg.dx.aaa.grpc.apis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.cdpg.dx.acl.policy.dao.PolicyDao;
import org.cdpg.dx.acl.policy.dao.model.PolicyDto;
import org.cdpg.dx.acl.policy.service.PolicyService;
import org.cdpg.dx.acl.rule.dao.AccessRuleDao;
import org.cdpg.dx.apis.acl.v1.CreatePolicyRequest;
import org.cdpg.dx.apis.acl.v1.CreatePolicyResponse;
import org.cdpg.dx.apis.acl.v1.ListActivePoliciesRequest;
import org.cdpg.dx.apis.acl.v1.ListActivePoliciesResponse;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PolicyApiServiceTest {

  private static final String ITEM = "8b95ab80-2aaf-4636-a65e-7f2563d0d371";
  private static final String GRANTOR = "5c0a7e9e-0b1f-4f7e-8f0a-3b8e6c1d2a10";
  private static final String BUYER = "0d0b1a52-6c0b-4f4e-9d52-1f0b8f5b6a11";
  private static final String ORG = "a1f1c2d3-4e5f-4a6b-8c7d-9e0f1a2b3c4d";

  private Vertx vertx;
  private PolicyService policyService;
  private PolicyDao policyDao;
  private AccessRuleDao accessRuleDao;
  private KeycloakUserService keycloak;
  private PolicyApiService svc;

  @BeforeEach
  void setUp() {
    vertx = Vertx.vertx();
    policyService = mock(PolicyService.class);
    policyDao = mock(PolicyDao.class);
    accessRuleDao = mock(AccessRuleDao.class);
    keycloak = mock(KeycloakUserService.class);
    svc = new PolicyApiService(vertx, policyService, policyDao, accessRuleDao, keycloak);
  }

  @AfterEach
  void tearDown() {
    vertx.close();
  }

  private static PolicyDto row(String id, String consumer, LocalDateTime createdAt) {
    return new PolicyDto(
        new JsonObject()
            .put("_id", id)
            .put("item_id", ITEM)
            .put("policy_type", "INDIVIDUAL")
            .put("owner_id", GRANTOR)
            .put("consumer_id", consumer)
            .put("status", "ACTIVE")
            .put("constraints", new JsonObject().put("access", new io.vertx.core.json.JsonArray()))
            .put("expiry_at", createdAt.plusYears(1).toString())
            .put("created_at", createdAt.toString()));
  }

  private static Timestamp inAYear() {
    return Timestamp.newBuilder()
        .setSeconds(Instant.now().plus(365, ChronoUnit.DAYS).getEpochSecond())
        .build();
  }

  private static CreatePolicyRequest.Builder individual() {
    return CreatePolicyRequest.newBuilder()
        .setItemId(ITEM)
        .setItemType("DATABANK")
        .setPolicyType("INDIVIDUAL")
        .setGrantorId(GRANTOR)
        .setUserId(BUYER)
        .setConstraints("{\"access\":[]}")
        .setExpiresAt(inAYear());
  }

  @Test
  void listsAUsersPoliciesNewestFirst() throws Exception {
    LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    String older = UUID.randomUUID().toString();
    String newer = UUID.randomUUID().toString();
    when(policyDao.getMatchingPolicies(UUID.fromString(ITEM), BUYER))
        .thenReturn(
            Future.succeededFuture(
                List.of(row(older, BUYER, now.minusDays(2)), row(newer, BUYER, now))));

    Recorder<ListActivePoliciesResponse> r = new Recorder<>();
    svc.listActivePolicies(
        ListActivePoliciesRequest.newBuilder().setItemId(ITEM).setUserId(BUYER).build(), r);

    var policies = r.value().getPoliciesList();
    assertThat(policies).extracting(p -> p.getPolicyId()).containsExactly(newer, older);
    assertThat(policies.get(0).getOwnerId()).isEqualTo(GRANTOR);
    assertThat(policies.get(0).getUserId()).isEqualTo(BUYER);
    assertThat(policies.get(0).hasExpiresAt()).isTrue();
  }

  @Test
  void listsAnOrganisationsPoliciesThroughAccessRulesWithoutDuplicates() throws Exception {
    String id = UUID.randomUUID().toString();
    PolicyDto p = row(id, null, LocalDateTime.now());
    when(accessRuleDao.findMatchingRule(UUID.fromString(ITEM), List.of(), List.of(ORG), List.of()))
        .thenReturn(Future.succeededFuture(List.of(p, p)));

    Recorder<ListActivePoliciesResponse> r = new Recorder<>();
    svc.listActivePolicies(
        ListActivePoliciesRequest.newBuilder().setItemId(ITEM).setOrganisationId(ORG).build(), r);

    assertThat(r.value().getPoliciesList()).hasSize(1);
  }

  @Test
  void listRequiresASubject() throws Exception {
    Recorder<ListActivePoliciesResponse> r = new Recorder<>();
    svc.listActivePolicies(ListActivePoliciesRequest.newBuilder().setItemId(ITEM).build(), r);
    assertThat(r.code()).isEqualTo(Status.Code.INVALID_ARGUMENT);
  }

  @Test
  void createsAsTheGrantorAndReturnsThePolicy() throws Exception {
    DxUser grantor = mock(DxUser.class);
    String id = UUID.randomUUID().toString();
    when(keycloak.getUserById(UUID.fromString(GRANTOR))).thenReturn(Future.succeededFuture(grantor));
    when(policyService.createPolicy(anyList(), eq(grantor))).thenReturn(Future.succeededFuture());
    when(policyDao.getAllWithFilters(anyMap()))
        .thenReturn(Future.succeededFuture(List.of(row(id, BUYER, LocalDateTime.now()))));

    Recorder<CreatePolicyResponse> r = new Recorder<>();
    svc.createPolicy(individual().build(), r);

    assertThat(r.value().getPolicy().getPolicyId()).isEqualTo(id);
    verify(policyService).createPolicy(anyList(), eq(grantor));
  }

  @Test
  void conflictIsAlreadyExists() throws Exception {
    when(keycloak.getUserById(any())).thenReturn(Future.succeededFuture(mock(DxUser.class)));
    when(policyService.createPolicy(anyList(), any()))
        .thenReturn(
            Future.failedFuture(
                new JsonObject()
                    .put("type", 409)
                    .put("title", "conflict")
                    .put("detail", "Active policy already exists")
                    .encode()));

    Recorder<CreatePolicyResponse> r = new Recorder<>();
    svc.createPolicy(individual().build(), r);

    assertThat(r.code()).isEqualTo(Status.Code.ALREADY_EXISTS);
  }

  @Test
  void validatesBeforeCallingAnything() throws Exception {
    List<CreatePolicyRequest> bad =
        List.of(
            individual().setItemId("x").build(),
            individual().setItemType("RESOURCE_GROUP").build(),
            individual().setPolicyType("OTHER").build(),
            individual().setUserId("").build(),
            individual().clearExpiresAt().build(),
            individual().setExpiresAt(Timestamp.newBuilder().setSeconds(1).build()).build(),
            individual().setConstraints("not json").build(),
            individual().setGrantorId("x").build());
    for (CreatePolicyRequest req : bad) {
      Recorder<CreatePolicyResponse> r = new Recorder<>();
      svc.createPolicy(req, r);
      assertThat(r.code()).as(req.toString()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }
    verifyNoInteractions(keycloak, policyService, policyDao);
  }
}
