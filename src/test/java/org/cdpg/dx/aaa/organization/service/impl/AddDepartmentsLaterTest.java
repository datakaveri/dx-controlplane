package org.cdpg.dx.aaa.organization.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.cdpg.dx.aaa.organization.config.Constants;
import org.cdpg.dx.aaa.organization.dao.OrganizationDAO;
import org.cdpg.dx.aaa.organization.dao.OrganizationHierarchyDefinitionRequestDAO;
import org.cdpg.dx.aaa.organization.dao.OrganizationHierarchyNodeDAO;
import org.cdpg.dx.aaa.organization.models.Organization;
import org.cdpg.dx.aaa.organization.models.OrganizationHierarchyDefinitionRequest;
import org.cdpg.dx.aaa.organization.models.OrganizationHierarchyNode;
import org.cdpg.dx.aaa.organization.models.ProposedDepartmentNode;
import org.cdpg.dx.aaa.organization.models.Status;
import org.cdpg.dx.aaa.organization.service.OrganizationHierarchyService;
import org.cdpg.dx.common.exception.DxConflictException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.exception.DxValidationException;
import org.cdpg.dx.testutil.VertxFutureAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Adding departments after an organisation's tree was first defined. */
@ExtendWith({VertxExtension.class, MockitoExtension.class})
class AddDepartmentsLaterTest {

  private final UUID orgId = UUID.randomUUID();
  private final UUID finance = UUID.randomUUID();

  private static ProposedDepartmentNode dept(String name, ProposedDepartmentNode... subs) {
    return new ProposedDepartmentNode(name, List.of(subs));
  }

  private OrganizationHierarchyNode node(UUID id, UUID parent, String name, int level, String path) {
    return new OrganizationHierarchyNode(id, orgId, parent, name, level, path, null, null);
  }

  @Nested
  class Hierarchy {

    @Mock private OrganizationDAO organizationDAO;
    @Mock private OrganizationHierarchyNodeDAO nodeDAO;

    private OrganizationHierarchyServiceImpl service;

    @BeforeEach
    void setUp() {
      service = new OrganizationHierarchyServiceImpl(organizationDAO, nodeDAO);
    }

    @Test
    @SuppressWarnings("unchecked")
    void addsUnderAnExistingDepartmentWithCorrectLevelsAndPaths(VertxTestContext ctx) {
      when(nodeDAO.getByIdAndOrganizationId(finance, orgId))
          .thenReturn(Future.succeededFuture(node(finance, null, "Finance", 1, "finance")));
      when(nodeDAO.getChildren(orgId, finance))
          .thenReturn(Future.succeededFuture(
              List.of(node(UUID.randomUUID(), finance, "Audit", 2, "finance.audit"))));
      ArgumentCaptor<List<OrganizationHierarchyNode>> inserted = ArgumentCaptor.forClass(List.class);
      when(nodeDAO.insertTree(inserted.capture()))
          .thenAnswer(inv -> Future.succeededFuture(inv.getArgument(0)));

      VertxFutureAssert.assertFutureSuccess(
          service.addDepartments(orgId, finance, List.of(dept("Payments", dept("Refunds")))), ctx,
          nodes -> {
            List<OrganizationHierarchyNode> created = inserted.getValue();
            assertThat(created).extracting(OrganizationHierarchyNode::name)
                .containsExactly("Payments", "Refunds");
            assertThat(created.get(0).parentId()).isEqualTo(finance);
            assertThat(created.get(0).level()).isEqualTo(2);
            assertThat(created.get(0).path()).isEqualTo("finance.payments");
            assertThat(created.get(1).parentId()).isEqualTo(created.get(0).id());
            assertThat(created.get(1).level()).isEqualTo(3);
            assertThat(created.get(1).path()).isEqualTo("finance.payments.refunds");
          });
    }

    @Test
    void addsTopLevelDepartmentsToAnExistingTree(VertxTestContext ctx) {
      when(nodeDAO.getChildren(orgId, null))
          .thenReturn(Future.succeededFuture(List.of(node(finance, null, "Finance", 1, "finance"))));
      when(nodeDAO.insertTree(any()))
          .thenAnswer(inv -> Future.succeededFuture(inv.getArgument(0)));

      VertxFutureAssert.assertFutureSuccess(
          service.addDepartments(orgId, null, List.of(dept("Legal"))), ctx,
          nodes -> {
            assertThat(nodes).hasSize(1);
            assertThat(nodes.getFirst().level()).isEqualTo(1);
            assertThat(nodes.getFirst().parentId()).isNull();
          });
    }

    @Test
    void nameClashWithAnExistingSiblingIsAConflict(VertxTestContext ctx) {
      when(nodeDAO.getChildren(orgId, null))
          .thenReturn(Future.succeededFuture(List.of(node(finance, null, "Finance", 1, "finance"))));

      VertxFutureAssert.assertFutureFailure(
          service.addDepartments(orgId, null, List.of(dept("  finance "))), ctx,
          err -> {
            assertThat(err).isInstanceOf(DxConflictException.class);
            verify(nodeDAO, never()).insertTree(any());
          });
    }

    @Test
    void unknownParentIsNotFound(VertxTestContext ctx) {
      when(nodeDAO.getByIdAndOrganizationId(finance, orgId)).thenReturn(Future.succeededFuture(null));

      VertxFutureAssert.assertFutureFailureType(
          service.validateAddition(orgId, finance, List.of(dept("Payments"))), ctx,
          DxNotFoundException.class);
    }

    @Test
    void blankNameIsABadRequestNotAConflict(VertxTestContext ctx) {
      VertxFutureAssert.assertFutureFailure(
          service.validateAddition(orgId, null, List.of(dept(" "))), ctx,
          err -> {
            assertThat(err).isInstanceOf(DxValidationException.class);
            verify(nodeDAO, never()).getChildren(any(), any());
          });
    }
  }

  @Nested
  class Definition {

    @Mock private OrganizationHierarchyDefinitionRequestDAO requestDAO;
    @Mock private OrganizationDAO organizationDAO;
    @Mock private OrganizationHierarchyService hierarchyService;

    private OrganizationHierarchyDefinitionServiceImpl service;
    private final UUID requestId = UUID.randomUUID();
    private final List<ProposedDepartmentNode> payments = List.of(dept("Payments"));

    @BeforeEach
    void setUp() {
      service = new OrganizationHierarchyDefinitionServiceImpl(requestDAO, organizationDAO, hierarchyService);
    }

    private OrganizationHierarchyDefinitionRequest request(Status status) {
      return new OrganizationHierarchyDefinitionRequest(
          requestId, orgId, finance, payments, status.getStatus(), null, null, null);
    }

    @Test
    void hierarchicalOrganisationCanProposeMoreDepartments(VertxTestContext ctx) {
      when(organizationDAO.get(orgId)).thenReturn(Future.succeededFuture(mock(Organization.class)));
      when(requestDAO.getAllWithFilters(anyMap())).thenReturn(Future.succeededFuture(List.of()));
      when(hierarchyService.validateAddition(orgId, finance, payments))
          .thenReturn(Future.succeededFuture());
      when(requestDAO.create(any()))
          .thenAnswer(inv -> Future.succeededFuture(inv.getArgument(0)));

      VertxFutureAssert.assertFutureSuccess(
          service.createDefinitionRequest(orgId, finance, payments), ctx,
          created -> {
            assertThat(created.parentNodeId()).isEqualTo(finance);
            assertThat(created.status()).isEqualTo("pending");
            verify(hierarchyService, never()).hasHierarchy(any());
          });
    }

    @Test
    void onlyOnePendingProposalAtATime(VertxTestContext ctx) {
      when(organizationDAO.get(orgId)).thenReturn(Future.succeededFuture(mock(Organization.class)));
      when(requestDAO.getAllWithFilters(anyMap()))
          .thenReturn(Future.succeededFuture(List.of(request(Status.PENDING))));

      VertxFutureAssert.assertFutureFailure(
          service.createDefinitionRequest(orgId, null, payments), ctx,
          err -> {
            assertThat(err).isInstanceOf(DxConflictException.class);
            verify(requestDAO, never()).create(any());
          });
    }

    @Test
    void approvalAddsDepartmentsBeforeMarkingGranted(VertxTestContext ctx) {
      when(requestDAO.get(requestId)).thenReturn(Future.succeededFuture(request(Status.PENDING)));
      when(hierarchyService.addDepartments(orgId, finance, payments))
          .thenReturn(Future.succeededFuture(List.of()));
      when(requestDAO.update(
              org.mockito.ArgumentMatchers.eq(
                  Map.of("id", requestId.toString(), Constants.STATUS, "pending")),
              anyMap()))
          .thenReturn(Future.succeededFuture(request(Status.GRANTED)));

      VertxFutureAssert.assertFutureSuccess(
          service.updateDefinitionRequestStatus(requestId, Status.GRANTED, null), ctx,
          updated -> assertThat(updated.status()).isEqualTo("granted"));
    }

    @Test
    void failedAdditionLeavesTheRequestPending(VertxTestContext ctx) {
      when(requestDAO.get(requestId)).thenReturn(Future.succeededFuture(request(Status.PENDING)));
      when(hierarchyService.addDepartments(orgId, finance, payments))
          .thenReturn(Future.failedFuture(new DxConflictException("name taken")));

      VertxFutureAssert.assertFutureFailure(
          service.updateDefinitionRequestStatus(requestId, Status.GRANTED, null), ctx,
          err -> {
            assertThat(err).isInstanceOf(DxConflictException.class);
            verify(requestDAO, never()).update(any(), any());
          });
    }

    @Test
    void rejectionAddsNothing(VertxTestContext ctx) {
      when(requestDAO.get(requestId)).thenReturn(Future.succeededFuture(request(Status.PENDING)));
      when(requestDAO.update(anyMap(), anyMap()))
          .thenReturn(Future.succeededFuture(request(Status.REJECTED)));

      VertxFutureAssert.assertFutureSuccess(
          service.updateDefinitionRequestStatus(requestId, Status.REJECTED, "not needed"), ctx,
          updated -> verify(hierarchyService, never()).addDepartments(any(), any(), any()));
    }

    @Test
    void onlyGrantedOrRejectedAreAccepted(VertxTestContext ctx) {
      VertxFutureAssert.assertFutureFailure(
          service.updateDefinitionRequestStatus(requestId, Status.WITHDRAWN, null), ctx,
          err -> {
            assertThat(err).isInstanceOf(DxValidationException.class);
            verify(requestDAO, never()).get(any());
          });
    }
  }
}
