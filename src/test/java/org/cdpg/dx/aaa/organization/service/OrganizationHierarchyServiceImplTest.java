package org.cdpg.dx.aaa.organization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.List;
import java.util.UUID;
import org.cdpg.dx.aaa.organization.dao.OrganizationDAO;
import org.cdpg.dx.aaa.organization.dao.OrganizationHierarchyNodeDAO;
import org.cdpg.dx.aaa.organization.models.Organization;
import org.cdpg.dx.aaa.organization.models.OrganizationHierarchyNode;
import org.cdpg.dx.aaa.organization.models.ProposedDepartmentNode;
import org.cdpg.dx.aaa.organization.service.impl.OrganizationHierarchyServiceImpl;
import org.cdpg.dx.common.exception.DxConflictException;
import org.cdpg.dx.common.exception.DxValidationException;
import org.cdpg.dx.testutil.VertxFutureAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
class OrganizationHierarchyServiceImplTest {

  @Mock private OrganizationDAO organizationDAO;
  @Mock private OrganizationHierarchyNodeDAO hierarchyNodeDAO;

  private OrganizationHierarchyServiceImpl service;
  private final UUID orgId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new OrganizationHierarchyServiceImpl(organizationDAO, hierarchyNodeDAO);
  }

  private void givenFlatOrganization() {
    when(organizationDAO.get(orgId)).thenReturn(Future.succeededFuture(mock(Organization.class)));
    when(hierarchyNodeDAO.existsByOrganizationId(orgId)).thenReturn(Future.succeededFuture(false));
  }

  private static ProposedDepartmentNode dept(String name, ProposedDepartmentNode... subs) {
    return new ProposedDepartmentNode(name, List.of(subs));
  }

  @Test
  @DisplayName("bulkCreateTree treats the organization as the implicit top of the tree")
  @SuppressWarnings("unchecked")
  void buildsNestedTree(VertxTestContext ctx) {
    givenFlatOrganization();
    ArgumentCaptor<List<OrganizationHierarchyNode>> captor = ArgumentCaptor.forClass(List.class);
    when(hierarchyNodeDAO.insertTree(captor.capture()))
        .thenAnswer(inv -> Future.succeededFuture(inv.getArgument(0)));

    List<ProposedDepartmentNode> departments =
        ProposedDepartmentNode.listFromJson(
            new JsonArray(
                """
                [
                  { "name": "Data Platform", "subDepartments": [ { "name": "Ingest" } ] },
                  { "name": "Finance" }
                ]
                """));

    VertxFutureAssert.assertFutureSuccess(
        service.bulkCreateTree(orgId, departments),
        ctx,
        nodes -> {
          assertThat(nodes).extracting(OrganizationHierarchyNode::path)
              .containsExactly("data_platform", "data_platform.ingest", "finance");
          assertThat(nodes).extracting(OrganizationHierarchyNode::name)
              .containsExactly("Data Platform", "Ingest", "Finance");
          assertThat(nodes).extracting(OrganizationHierarchyNode::level)
              .containsExactly(1, 2, 1);
          assertThat(nodes).allSatisfy(n -> assertThat(n.id()).isNotNull());
          assertThat(nodes.get(0).parentId()).isNull();
          assertThat(nodes.get(1).parentId()).isEqualTo(nodes.get(0).id());
          assertThat(nodes.get(2).parentId()).isNull();
        });
  }

  @Test
  @DisplayName("bulkCreateTree places no limit on hierarchy depth")
  void allowsDeepTree(VertxTestContext ctx) {
    givenFlatOrganization();
    when(hierarchyNodeDAO.insertTree(any()))
        .thenAnswer(inv -> Future.succeededFuture(inv.getArgument(0)));

    List<ProposedDepartmentNode> departments =
        List.of(dept("a", dept("b", dept("c", dept("d", dept("e", dept("f")))))));

    VertxFutureAssert.assertFutureSuccess(
        service.bulkCreateTree(orgId, departments),
        ctx,
        nodes ->
            assertThat(nodes).extracting(OrganizationHierarchyNode::level)
                .containsExactly(1, 2, 3, 4, 5, 6));
  }

  @Test
  @DisplayName("bulkCreateTree rejects duplicate sibling names")
  void rejectsDuplicateSiblings(VertxTestContext ctx) {
    givenFlatOrganization();

    List<ProposedDepartmentNode> departments =
        List.of(dept("Ops", dept("Finance"), dept("finance ")));

    VertxFutureAssert.assertFutureFailureType(
        service.bulkCreateTree(orgId, departments), ctx, DxValidationException.class);
  }

  @Test
  @DisplayName("bulkCreateTree rejects duplicate top-level department names")
  void rejectsDuplicateTopLevelDepartments(VertxTestContext ctx) {
    givenFlatOrganization();

    List<ProposedDepartmentNode> departments = List.of(dept("Finance"), dept("FINANCE"));

    VertxFutureAssert.assertFutureFailureType(
        service.bulkCreateTree(orgId, departments), ctx, DxValidationException.class);
  }

  @Test
  @DisplayName("bulkCreateTree rejects an empty department list")
  void rejectsEmptyDepartments(VertxTestContext ctx) {
    VertxFutureAssert.assertFutureFailureType(
        service.bulkCreateTree(orgId, List.of()), ctx, DxValidationException.class);
  }

  @Test
  @DisplayName("bulkCreateTree refuses to run once the organization already has a hierarchy")
  void rejectsSecondDefinition(VertxTestContext ctx) {
    when(organizationDAO.get(orgId)).thenReturn(Future.succeededFuture(mock(Organization.class)));
    when(hierarchyNodeDAO.existsByOrganizationId(orgId)).thenReturn(Future.succeededFuture(true));

    VertxFutureAssert.assertFutureFailureType(
        service.bulkCreateTree(orgId, List.of(dept("Finance"))), ctx, DxConflictException.class);
  }
}
