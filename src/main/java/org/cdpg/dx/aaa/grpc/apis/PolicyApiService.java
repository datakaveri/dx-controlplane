package org.cdpg.dx.aaa.grpc.apis;

import com.google.protobuf.Timestamp;
import io.grpc.stub.StreamObserver;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.acl.policy.dao.PolicyDao;
import org.cdpg.dx.acl.policy.dao.model.PolicyDto;
import org.cdpg.dx.acl.policy.service.PolicyService;
import org.cdpg.dx.acl.policy.service.model.CreatePolicyRequest;
import org.cdpg.dx.acl.rule.dao.AccessRuleDao;
import org.cdpg.dx.apis.acl.v1.CreatePolicyResponse;
import org.cdpg.dx.apis.acl.v1.ListActivePoliciesRequest;
import org.cdpg.dx.apis.acl.v1.ListActivePoliciesResponse;
import org.cdpg.dx.apis.acl.v1.Policy;
import org.cdpg.dx.apis.acl.v1.PolicyServiceGrpc;
import org.cdpg.dx.catalogueService.models.ItemType;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

/**
 * Serves the shared {@code dx.apis.acl.v1.PolicyService} contract (dx-common
 * src/main/proto/dx/apis/acl/v1/policy.proto), which dx-acl-go serves too.
 *
 * <p>CreatePolicy runs the controlplane's own {@link PolicyService#createPolicy} as the grantor,
 * so every rule of {@code POST /policies} applies unchanged: item type, access types, ownership or
 * org_admin, and the conflict checks.
 */
public class PolicyApiService extends PolicyServiceGrpc.PolicyServiceImplBase {

  private static final Logger LOGGER = LogManager.getLogger(PolicyApiService.class);

  private static final Set<String> POLICY_TYPES = Set.of("INDIVIDUAL", "GROUP");
  private static final Set<ItemType> ITEM_TYPES =
      Set.of(ItemType.DATABANK, ItemType.AIMODEL, ItemType.APPS);
  private static final String ACTIVE = "ACTIVE";

  private final Context vertxContext;
  private final PolicyService policyService;
  private final PolicyDao policyDao;
  private final AccessRuleDao accessRuleDao;
  private final KeycloakUserService keycloakUserService;

  /** Builds the service over the controlplane's own policy service and DAOs. */
  public PolicyApiService(
      Vertx vertx,
      PolicyService policyService,
      PolicyDao policyDao,
      AccessRuleDao accessRuleDao,
      KeycloakUserService keycloakUserService) {
    this.vertxContext = vertx.getOrCreateContext();
    this.policyService = policyService;
    this.policyDao = policyDao;
    this.accessRuleDao = accessRuleDao;
    this.keycloakUserService = keycloakUserService;
  }

  /* ── ListActivePolicies ────────────────────────────────────────────────── */

  @Override
  public void listActivePolicies(
      ListActivePoliciesRequest request, StreamObserver<ListActivePoliciesResponse> observer) {
    String itemId = request.getItemId();
    if (!ItemApiService.isUuid(itemId)) {
      observer.onError(GrpcErrors.invalid("item_id must be a UUID"));
      return;
    }
    Supplier<Future<List<PolicyDto>>> query;
    switch (request.getSubjectCase()) {
      case USER_ID -> {
        String userId = request.getUserId();
        if (!ItemApiService.isUuid(userId)) {
          observer.onError(GrpcErrors.invalid("user_id must be a UUID"));
          return;
        }
        query = () -> policyDao.getMatchingPolicies(UUID.fromString(itemId), userId);
      }
      case ORGANISATION_ID -> {
        String orgId = request.getOrganisationId();
        if (!ItemApiService.isUuid(orgId)) {
          observer.onError(GrpcErrors.invalid("organisation_id must be a UUID"));
          return;
        }
        query =
            () ->
                accessRuleDao.findMatchingRule(
                    UUID.fromString(itemId), List.of(), List.of(orgId), List.of());
      }
      default -> {
        observer.onError(GrpcErrors.invalid("user_id or organisation_id is required"));
        return;
      }
    }
    run(
        "ListActivePolicies",
        observer,
        () ->
            query
                .get()
                .map(
                    rows ->
                        ListActivePoliciesResponse.newBuilder()
                            .addAllPolicies(newestFirst(rows).stream().map(this::toPolicy).toList())
                            .build()));
  }

  /* ── CreatePolicy ──────────────────────────────────────────────────────── */

  @Override
  public void createPolicy(
      org.cdpg.dx.apis.acl.v1.CreatePolicyRequest request,
      StreamObserver<CreatePolicyResponse> observer) {
    CreatePolicyRequest req;
    UUID grantorId;
    try {
      grantorId = UUID.fromString(request.getGrantorId());
      req = toCreateRequest(request);
    } catch (IllegalArgumentException e) {
      observer.onError(GrpcErrors.invalid(e.getMessage()));
      return;
    }
    run(
        "CreatePolicy",
        observer,
        () ->
            keycloakUserService
                .getUserById(grantorId)
                .compose(grantor -> policyService.createPolicy(List.of(req), grantor))
                .compose(v -> findCreated(req, grantorId.toString()))
                .map(p -> CreatePolicyResponse.newBuilder().setPolicy(toPolicy(p)).build()));
  }

  private CreatePolicyRequest toCreateRequest(org.cdpg.dx.apis.acl.v1.CreatePolicyRequest r) {
    if (!ItemApiService.isUuid(r.getItemId())) {
      throw new IllegalArgumentException("item_id must be a UUID");
    }
    if (!POLICY_TYPES.contains(r.getPolicyType())) {
      throw new IllegalArgumentException("policy_type must be INDIVIDUAL or GROUP");
    }
    ItemType itemType;
    try {
      itemType = ItemType.fromTypeValue(r.getItemType());
    } catch (IllegalArgumentException e) {
      itemType = null;
    }
    if (!ITEM_TYPES.contains(itemType)) {
      throw new IllegalArgumentException("item_type must be DATABANK, AIMODEL or APPS");
    }
    boolean individual = "INDIVIDUAL".equals(r.getPolicyType());
    if (individual && !ItemApiService.isUuid(r.getUserId())) {
      throw new IllegalArgumentException("user_id must be a UUID for an INDIVIDUAL policy");
    }
    if (!r.hasExpiresAt()) {
      throw new IllegalArgumentException("expires_at is required");
    }
    JsonObject constraints;
    try {
      String json = r.getConstraints();
      constraints = json.isBlank() ? new JsonObject() : new JsonObject(json);
    } catch (Exception e) {
      throw new IllegalArgumentException("constraints must be a JSON object");
    }

    CreatePolicyRequest req = new CreatePolicyRequest();
    req.setItemId(r.getItemId());
    req.setItemType(itemType);
    req.setPolicyType(r.getPolicyType());
    if (individual) {
      req.setUserId(r.getUserId());
    }
    req.setConstraints(constraints);
    // Throws IllegalArgumentException when not in the future.
    req.setExpiryTime(toLocal(r.getExpiresAt()).truncatedTo(ChronoUnit.SECONDS).toString());
    return req;
  }

  /** createPolicy returns no id, so read back the newest ACTIVE policy it just wrote. */
  private Future<PolicyDto> findCreated(CreatePolicyRequest req, String ownerId) {
    Map<String, Object> filters = new HashMap<>();
    filters.put("item_id", req.getItemId().toString());
    filters.put("owner_id", ownerId);
    filters.put("status", ACTIVE);
    if (req.getUserId() != null) {
      filters.put("consumer_id", req.getUserId());
    }
    return policyDao
        .getAllWithFilters(filters)
        .compose(
            rows -> {
              List<PolicyDto> matching =
                  newestFirst(
                      rows.stream()
                          .filter(p -> req.getPolicyType().equals(p.getPolicyType()))
                          .toList());
              return matching.isEmpty()
                  ? Future.failedFuture(new IllegalStateException("created policy not found"))
                  : Future.succeededFuture(matching.get(0));
            });
  }

  /* ── Mapping ───────────────────────────────────────────────────────────── */

  private Policy toPolicy(PolicyDto p) {
    Policy.Builder b =
        Policy.newBuilder()
            .setPolicyId(nz(p.getPolicyId()))
            .setItemId(nz(p.getItemId()))
            .setPolicyType(nz(p.getPolicyType()))
            .setOwnerId(nz(p.getOwnerId()))
            .setUserId(nz(p.getConsumerId()))
            .setConstraints(p.getConstraints() == null ? "{}" : p.getConstraints().encode());
    if (p.getExpiryAt() != null) {
      b.setExpiresAt(toTimestamp(p.getExpiryAt()));
    }
    if (p.getCreatedAt() != null) {
      b.setCreatedAt(toTimestamp(p.getCreatedAt()));
    }
    return b.build();
  }

  /** Distinct by policy id (access-rule joins repeat rows), newest first. */
  private static List<PolicyDto> newestFirst(List<PolicyDto> rows) {
    Map<String, PolicyDto> byId = new LinkedHashMap<>();
    rows.forEach(p -> byId.putIfAbsent(p.getPolicyId(), p));
    Comparator<LocalDateTime> oldestFirst = Comparator.nullsFirst(Comparator.naturalOrder());
    return byId.values().stream()
        .sorted(Comparator.comparing(PolicyDto::getCreatedAt, oldestFirst).reversed())
        .toList();
  }

  // Policy timestamps are stored without a zone, in the JVM's zone (LocalDateTime.now()).
  private static Timestamp toTimestamp(LocalDateTime t) {
    Instant i = t.atZone(ZoneId.systemDefault()).toInstant();
    return Timestamp.newBuilder().setSeconds(i.getEpochSecond()).setNanos(i.getNano()).build();
  }

  private static LocalDateTime toLocal(Timestamp t) {
    return LocalDateTime.ofInstant(
        Instant.ofEpochSecond(t.getSeconds(), t.getNanos()), ZoneId.systemDefault());
  }

  private static String nz(String s) {
    return s == null ? "" : s;
  }

  private <T> void run(String rpc, StreamObserver<T> observer, Supplier<Future<T>> call) {
    vertxContext.runOnContext(
        ignored ->
            call.get()
                .onSuccess(
                    resp -> {
                      observer.onNext(resp);
                      observer.onCompleted();
                    })
                .onFailure(
                    err -> {
                      LOGGER.error("{} failed: {}", rpc, err.getMessage());
                      observer.onError(GrpcErrors.toStatus(err));
                    }));
  }
}
