package org.cdpg.dx.aaa.delegation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cdpg.dx.testutil.VertxFutureAssert.assertFutureFailure;
import static org.cdpg.dx.testutil.VertxFutureAssert.assertFutureSuccess;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.cdpg.dx.aaa.delegation.dao.DelegationDAOFactory;
import org.cdpg.dx.aaa.delegation.dao.DelegationGrantDAO;
import org.cdpg.dx.aaa.delegation.dao.DelegationRequestDAO;
import org.cdpg.dx.aaa.delegation.dao.ScopeConstraintDAO;
import org.cdpg.dx.aaa.delegation.dao.TokenDAO;
import org.cdpg.dx.aaa.delegation.models.DelegationGrant;
import org.cdpg.dx.aaa.delegation.models.DelegationScopeConstraint;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
@DisplayName("DelegationService Tests")
class DelegationServiceTest {

  private static final DateTimeFormatter FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

  @Mock private DelegationDAOFactory factory;
  @Mock private DelegationGrantDAO delegationGrantDAO;
  @Mock private DelegationRequestDAO delegationRequestDAO;
  @Mock private ScopeConstraintDAO scopeConstraintDAO;
  @Mock private TokenDAO tokenDAO;
  @Mock private KeycloakUserService keycloakUserService;
  @Mock private OrganizationService organizationService;
  @Mock private ItemService itemService;

  private DelegationServiceImpl delegationService;

  @BeforeEach
  void setUp() {
    lenient().when(factory.delegationGrantDAO()).thenReturn(delegationGrantDAO);
    lenient().when(factory.delegationRequestDAO()).thenReturn(delegationRequestDAO);
    lenient().when(factory.scopeConstraintDAO()).thenReturn(scopeConstraintDAO);
    lenient().when(factory.tokenDAO()).thenReturn(tokenDAO);

    delegationService =
        new DelegationServiceImpl(factory, keycloakUserService, organizationService, itemService);
  }

  private DelegationGrant buildGrant(UUID delegationId, UUID delegatorId, UUID delegateId) {
    LocalDateTime expiry = LocalDateTime.now().plusDays(30);
    return new DelegationGrant(
        delegationId,
        delegatorId,
        delegateId,
        "test justification",
        expiry,
        "active",
        LocalDateTime.now(),
        null);
  }

  @Nested
  @DisplayName("createDelegationGrant")
  class CreateDelegationGrant {

    @Test
    @DisplayName("should create a wildcard delegation grant successfully")
    void createDelegation_success(VertxTestContext ctx) {
      UUID delegatorId = UUID.randomUUID();
      UUID delegateId = UUID.randomUUID();
      UUID delegationId = UUID.randomUUID();
      LocalDateTime expiry = LocalDateTime.now().plusDays(30);

      DelegationGrant grant = buildGrant(delegationId, delegatorId, delegateId);

      JsonObject body =
          new JsonObject()
              .put("delegator_id", delegatorId.toString())
              .put("delegate_id", delegateId.toString())
              .put("justification", "test justification")
              .put("expiry_at", expiry.format(FORMATTER));

      Set<String> roles = Set.of("provider");

      when(delegationGrantDAO.create(any(DelegationGrant.class)))
          .thenReturn(Future.succeededFuture(grant));
      when(scopeConstraintDAO.create(any(DelegationScopeConstraint.class)))
          .thenReturn(Future.succeededFuture(null));
      when(keycloakUserService.addRoleToUser(any(UUID.class), any()))
          .thenReturn(Future.succeededFuture(true));
      when(keycloakUserService.setDelegationScopes(any(UUID.class), anyList(), any(UUID.class)))
          .thenReturn(Future.succeededFuture(true));

      Future<JsonObject> future =
          delegationService.createDelegationGrant(body, roles, new JsonArray());

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isNotNull();
            assertThat(result.getString("delegator_id")).isEqualTo(delegatorId.toString());
            assertThat(result.getString("delegate_id")).isEqualTo(delegateId.toString());
          });
    }

    @Test
    @DisplayName("should fail when entity ownership validation fails")
    void createDelegation_failUnauthorized(VertxTestContext ctx) {
      UUID delegatorId = UUID.randomUUID();
      UUID delegateId = UUID.randomUUID();
      LocalDateTime expiry = LocalDateTime.now().plusDays(30);

      JsonObject body =
          new JsonObject()
              .put("delegator_id", delegatorId.toString())
              .put("delegate_id", delegateId.toString())
              .put("justification", "test justification")
              .put("expiry_at", expiry.format(FORMATTER));

      Set<String> roles = Set.of("provider");

      JsonArray roleConstraints =
          new JsonArray()
              .add(
                  new JsonObject()
                      .put("role", "provider")
                      .put(
                          "constraints",
                          new JsonArray()
                              .add(
                                  new JsonObject()
                                      .put("scope", "data_access")
                                      .put("entity_type", "item")
                                      .put(
                                          "entity_id",
                                          new JsonArray().add(UUID.randomUUID().toString())))));

      when(itemService.getItemWithAccessChecks(any()))
          .thenReturn(Future.failedFuture(new DxForbiddenException("Not the owner")));

      Future<JsonObject> future =
          delegationService.createDelegationGrant(body, roles, roleConstraints);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isNotNull();
          });
    }
  }

  @Nested
  @DisplayName("getAllDelegationsByDelegator")
  class GetDelegationsByDelegator {

    @Test
    @DisplayName("should return delegations for a delegator")
    void getDelegationsByDelegator_success(VertxTestContext ctx) {
      UUID delegatorId = UUID.randomUUID();
      UUID delegateId = UUID.randomUUID();
      UUID delegationId = UUID.randomUUID();

      DelegationGrant grant = buildGrant(delegationId, delegatorId, delegateId);

      when(delegationGrantDAO.getAllWithFilters(anyMap()))
          .thenReturn(Future.succeededFuture(List.of(grant)));
      when(scopeConstraintDAO.getAllWithFilters(anyMap()))
          .thenReturn(Future.succeededFuture(List.of()));

      Future<List<JsonObject>> future =
          delegationService.getAllDelegationsByDelegator(delegatorId.toString());

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getString("delegator_id"))
                .isEqualTo(delegatorId.toString());
            assertThat(result.get(0).containsKey("constraints")).isTrue();
          });
    }
  }

  @Nested
  @DisplayName("getAllDelegationsOfDelegate")
  class GetDelegationsByDelegate {

    @Test
    @DisplayName("should return delegations for a delegate")
    void getDelegationsByDelegate_success(VertxTestContext ctx) {
      UUID delegatorId = UUID.randomUUID();
      UUID delegateId = UUID.randomUUID();
      UUID delegationId = UUID.randomUUID();

      DelegationGrant grant = buildGrant(delegationId, delegatorId, delegateId);

      when(delegationGrantDAO.getAllWithFilters(anyMap()))
          .thenReturn(Future.succeededFuture(List.of(grant)));
      when(scopeConstraintDAO.getAllWithFilters(anyMap()))
          .thenReturn(Future.succeededFuture(List.of()));

      Future<List<JsonObject>> future =
          delegationService.getAllDelegationsOfDelegate(delegateId.toString());

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getString("delegate_id")).isEqualTo(delegateId.toString());
            assertThat(result.get(0).containsKey("constraints")).isTrue();
          });
    }
  }

  @Nested
  @DisplayName("getDelegationGrantById")
  class GetDelegationById {

    @Test
    @DisplayName("should return delegation when found")
    void getDelegationById_success(VertxTestContext ctx) {
      UUID delegationId = UUID.randomUUID();
      UUID delegatorId = UUID.randomUUID();
      UUID delegateId = UUID.randomUUID();

      DelegationGrant grant = buildGrant(delegationId, delegatorId, delegateId);

      when(delegationGrantDAO.get(delegationId)).thenReturn(Future.succeededFuture(grant));

      Future<JsonObject> future =
          delegationService.getDelegationGrantById(delegationId.toString());

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isNotNull();
            assertThat(result.getString("delegation_id")).isEqualTo(delegationId.toString());
          });
    }

    @Test
    @DisplayName("should fail when delegation not found")
    void getDelegationById_notFound(VertxTestContext ctx) {
      UUID delegationId = UUID.randomUUID();

      when(delegationGrantDAO.get(delegationId))
          .thenReturn(Future.failedFuture(new DxNotFoundException("Not found")));

      Future<JsonObject> future =
          delegationService.getDelegationGrantById(delegationId.toString());

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(DxNotFoundException.class);
          });
    }
  }

  @Nested
  @DisplayName("deleteDelegation")
  class DeleteDelegation {

    @Test
    @DisplayName("should delete delegation successfully when user is delegator")
    void deleteDelegation_success(VertxTestContext ctx) {
      UUID delegationId = UUID.randomUUID();
      UUID delegatorId = UUID.randomUUID();
      UUID delegateId = UUID.randomUUID();

      DelegationGrant grant = buildGrant(delegationId, delegatorId, delegateId);

      when(delegationGrantDAO.get(delegationId)).thenReturn(Future.succeededFuture(grant));
      when(scopeConstraintDAO.getAllWithFilters(anyMap()))
          .thenReturn(Future.succeededFuture(List.of()));
      when(keycloakUserService.clearDelegationScopes(any(UUID.class), any()))
          .thenReturn(Future.succeededFuture(true));
      when(delegationGrantDAO.delete(delegationId))
          .thenReturn(Future.succeededFuture(true));

      Future<Boolean> future =
          delegationService.deleteDelegation(delegationId.toString(), delegatorId.toString());

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isTrue();
            verify(delegationGrantDAO).delete(delegationId);
          });
    }

    @Test
    @DisplayName("should fail when delegation not found for delete")
    void deleteDelegation_notFound(VertxTestContext ctx) {
      UUID delegationId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();

      when(delegationGrantDAO.get(delegationId))
          .thenReturn(Future.failedFuture(new DxNotFoundException("Not found")));

      Future<Boolean> future =
          delegationService.deleteDelegation(delegationId.toString(), userId.toString());

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isNotNull();
          });
    }
  }
}
