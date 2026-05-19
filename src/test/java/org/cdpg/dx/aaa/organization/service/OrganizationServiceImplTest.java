package org.cdpg.dx.aaa.organization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.organization.config.Constants;
import org.cdpg.dx.aaa.organization.dao.OrganizationCreateRequestDAO;
import org.cdpg.dx.aaa.organization.dao.OrganizationDAO;
import org.cdpg.dx.aaa.organization.dao.OrganizationDAOFactory;
import org.cdpg.dx.aaa.organization.dao.OrganizationJoinRequestDAO;
import org.cdpg.dx.aaa.organization.dao.OrganizationUserDAO;
import org.cdpg.dx.aaa.organization.dao.ProviderRoleRequestDAO;
import org.cdpg.dx.aaa.organization.models.Organization;
import org.cdpg.dx.aaa.organization.models.OrganizationCreateRequest;
import org.cdpg.dx.aaa.organization.models.OrganizationJoinRequest;
import org.cdpg.dx.aaa.organization.models.OrganizationUser;
import org.cdpg.dx.aaa.organization.models.Role;
import org.cdpg.dx.aaa.organization.models.Status;
import org.cdpg.dx.auth.model.DxRole;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.util.PaginationInfo;
import org.cdpg.dx.database.postgres.models.PaginatedResult;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
import org.cdpg.dx.testutil.TestDataFactory;
import org.cdpg.dx.testutil.VertxFutureAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
class OrganizationServiceImplTest {

  @Mock private OrganizationDAOFactory factory;
  @Mock private OrganizationCreateRequestDAO createRequestDAO;
  @Mock private OrganizationJoinRequestDAO joinRequestDAO;
  @Mock private OrganizationUserDAO orgUserDAO;
  @Mock private OrganizationDAO orgDAO;
  @Mock private ProviderRoleRequestDAO providerRequestDAO;
  @Mock private KeycloakUserService keycloakUserService;
  @Mock private ItemService itemService;

  private OrganizationServiceImpl service;

  @BeforeEach
  void setUp(VertxTestContext ctx) {
    when(factory.organizationCreateRequest()).thenReturn(createRequestDAO);
    when(factory.organizationUserDAO()).thenReturn(orgUserDAO);
    when(factory.organizationDAO()).thenReturn(orgDAO);
    when(factory.organizationJoinRequestDAO()).thenReturn(joinRequestDAO);
    when(factory.providerRoleRequestDAO()).thenReturn(providerRequestDAO);

    service = new OrganizationServiceImpl(factory, keycloakUserService, itemService);
    ctx.completeNow();
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private static Organization anOrganization(UUID orgId, String name) {
    return new Organization(
        orgId, name, "logo.png", "Private", "Tech",
        "https://example.com", "123 Main St",
        "cert.pdf", "pan.pdf", "doc.pdf", "documents",
        null, null);
  }

  // ---------------------------------------------------------------------------
  // 1. createOrganizationRequest
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("createOrganizationRequest")
  class CreateOrganizationRequestTests {

    @Test
    @DisplayName("should delegate to createRequestDAO.create and return the created request")
    void success(VertxTestContext ctx) {
      OrganizationCreateRequest request = TestDataFactory.anOrgCreateRequest();
      when(createRequestDAO.create(request)).thenReturn(Future.succeededFuture(request));

      Future<OrganizationCreateRequest> result = service.createOrganizationRequest(request);

      VertxFutureAssert.assertFutureSuccess(result, ctx, created -> {
        assertThat(created).isEqualTo(request);
        verify(createRequestDAO).create(request);
      });
    }
  }

  // ---------------------------------------------------------------------------
  // 2. getOrganizationCreateRequestsByUserId
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("getOrganizationCreateRequestsByUserId")
  class GetOrganizationCreateRequestsByUserIdTests {

    @Test
    @DisplayName("should combine pending and granted results into a single list")
    void success(VertxTestContext ctx) {
      UUID userId = UUID.randomUUID();

      OrganizationCreateRequest pendingReq = mock(OrganizationCreateRequest.class);
      OrganizationCreateRequest grantedReq = mock(OrganizationCreateRequest.class);

      Map<String, Object> pendingFilter = Map.of(
          Constants.REQUESTED_BY, userId.toString(),
          Constants.STATUS, Status.PENDING.getStatus());
      Map<String, Object> grantedFilter = Map.of(
          Constants.REQUESTED_BY, userId.toString(),
          Constants.STATUS, Status.GRANTED.getStatus());
      Map<String, Object> rejectedFilter = Map.of(
          Constants.REQUESTED_BY, userId.toString(),
          Constants.STATUS, Status.REJECTED.getStatus());

      when(createRequestDAO.getAllWithFilters(pendingFilter))
          .thenReturn(Future.succeededFuture(List.of(pendingReq)));
      when(createRequestDAO.getAllWithFilters(grantedFilter))
          .thenReturn(Future.succeededFuture(List.of(grantedReq)));
      when(createRequestDAO.getAllWithFilters(rejectedFilter))
          .thenReturn(Future.succeededFuture(Collections.emptyList()));

      Future<List<OrganizationCreateRequest>> result =
          service.getOrganizationCreateRequestsByUserId(userId);

      VertxFutureAssert.assertFutureSuccess(result, ctx, merged -> {
        assertThat(merged).hasSize(2);
        assertThat(merged).contains(pendingReq, grantedReq);
        verify(createRequestDAO).getAllWithFilters(pendingFilter);
        verify(createRequestDAO).getAllWithFilters(grantedFilter);
      });
    }
  }

  // ---------------------------------------------------------------------------
  // 3. getAllPendingGrantedOrganizationCreateRequests
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("getAllPendingGrantedOrganizationCreateRequests")
  class GetAllPendingGrantedOrganizationCreateRequestsTests {

    @Test
    @DisplayName("should merge pending and granted lists from the DAO")
    void success(VertxTestContext ctx) {
      OrganizationCreateRequest pendingReq = mock(OrganizationCreateRequest.class);
      OrganizationCreateRequest grantedReq = mock(OrganizationCreateRequest.class);

      Map<String, Object> pendingFilter = Map.of(Constants.STATUS, Status.PENDING.getStatus());
      Map<String, Object> grantedFilter = Map.of(Constants.STATUS, Status.GRANTED.getStatus());

      when(createRequestDAO.getAllWithFilters(pendingFilter))
          .thenReturn(Future.succeededFuture(List.of(pendingReq)));
      when(createRequestDAO.getAllWithFilters(grantedFilter))
          .thenReturn(Future.succeededFuture(List.of(grantedReq)));

      Future<List<OrganizationCreateRequest>> result =
          service.getAllPendingGrantedOrganizationCreateRequests();

      VertxFutureAssert.assertFutureSuccess(result, ctx, merged -> {
        assertThat(merged).hasSize(2);
        assertThat(merged).contains(pendingReq, grantedReq);
      });
    }
  }

  // ---------------------------------------------------------------------------
  // 4. getAllOrganizationCreateRequests (paginated)
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("getAllOrganizationCreateRequests")
  class GetAllOrganizationCreateRequestsTests {

    @Test
    @DisplayName("should return paginated result from the DAO")
    void paginatedSuccess(VertxTestContext ctx) {
      PaginatedRequest paginatedRequest = mock(PaginatedRequest.class);

      OrganizationCreateRequest req1 = mock(OrganizationCreateRequest.class);
      OrganizationCreateRequest req2 = mock(OrganizationCreateRequest.class);
      PaginationInfo pageInfo = PaginationInfo.from(1, 10, 2);
      PaginatedResult<OrganizationCreateRequest> expectedResult =
          new PaginatedResult<>(pageInfo, List.of(req1, req2));

      when(createRequestDAO.getAllWithFilters(paginatedRequest))
          .thenReturn(Future.succeededFuture(expectedResult));

      Future<PaginatedResult<OrganizationCreateRequest>> result =
          service.getAllOrganizationCreateRequests(paginatedRequest);

      VertxFutureAssert.assertFutureSuccess(result, ctx, paginated -> {
        assertThat(paginated).isEqualTo(expectedResult);
        assertThat(paginated.data()).hasSize(2);
        verify(createRequestDAO).getAllWithFilters(paginatedRequest);
      });
    }
  }

  // ---------------------------------------------------------------------------
  // 5. getOrganizationCreateRequestById
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("getOrganizationCreateRequestById")
  class GetOrganizationCreateRequestByIdTests {

    @Test
    @DisplayName("should return the request when it exists")
    void success(VertxTestContext ctx) {
      UUID requestId = UUID.randomUUID();
      OrganizationCreateRequest expected = mock(OrganizationCreateRequest.class);

      when(createRequestDAO.get(requestId)).thenReturn(Future.succeededFuture(expected));

      Future<OrganizationCreateRequest> result =
          service.getOrganizationCreateRequestById(requestId);

      VertxFutureAssert.assertFutureSuccess(result, ctx, req -> {
        assertThat(req).isEqualTo(expected);
        verify(createRequestDAO).get(requestId);
      });
    }

    @Test
    @DisplayName("should propagate failure when DAO returns a failed future")
    void notFound(VertxTestContext ctx) {
      UUID requestId = UUID.randomUUID();

      when(createRequestDAO.get(requestId))
          .thenReturn(Future.failedFuture(new DxNotFoundException("not found")));

      Future<OrganizationCreateRequest> result =
          service.getOrganizationCreateRequestById(requestId);

      VertxFutureAssert.assertFutureFailure(result, ctx, err -> {
        assertThat(err).isInstanceOf(DxNotFoundException.class);
        assertThat(err.getMessage()).contains("not found");
      });
    }
  }

  // ---------------------------------------------------------------------------
  // 6. createOrganizationFromRequest
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("createOrganizationFromRequest")
  class CreateOrganizationFromRequestTests {

    @Test
    @DisplayName("should create org, assign ORG_ADMIN and PROVIDER roles, set org details, and create org user")
    void success(VertxTestContext ctx) {
      UUID requestId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      UUID orgId = UUID.randomUUID();
      String orgName = "Test Org";

      OrganizationCreateRequest request = mock(OrganizationCreateRequest.class);
      when(request.name()).thenReturn(orgName);
      when(request.logoPath()).thenReturn("logo.png");
      when(request.entityType()).thenReturn("Private");
      when(request.orgSector()).thenReturn("Tech");
      when(request.websiteLink()).thenReturn("https://example.com");
      when(request.address()).thenReturn("123 Main St");
      when(request.certificatePath()).thenReturn("cert.pdf");
      when(request.pancardPath()).thenReturn("pan.pdf");
      when(request.relevantDocPath()).thenReturn("doc.pdf");
      when(request.orgDocuments()).thenReturn("documents");
      when(request.requestedBy()).thenReturn(userId);
      when(request.userName()).thenReturn("testuser");
      when(request.jobTitle()).thenReturn("Developer");
      when(request.empId()).thenReturn("EMP001");
      when(request.orgManagerphoneNo()).thenReturn("9876543210");
      when(request.managerEmail()).thenReturn("manager@example.com");

      Organization createdOrg = mock(Organization.class);
      when(createdOrg.id()).thenReturn(orgId);
      when(createdOrg.orgName()).thenReturn(orgName);

      when(createRequestDAO.get(requestId)).thenReturn(Future.succeededFuture(request));
      when(orgDAO.create(any(Organization.class))).thenReturn(Future.succeededFuture(createdOrg));
      when(keycloakUserService.addRoleToUser(userId, DxRole.ORG_ADMIN))
          .thenReturn(Future.succeededFuture(true));
      when(keycloakUserService.addRoleToUser(userId, DxRole.PROVIDER))
          .thenReturn(Future.succeededFuture(true));
      when(keycloakUserService.setOrganisationDetails(userId, orgId, orgName))
          .thenReturn(Future.succeededFuture(true));
      when(orgUserDAO.create(any(OrganizationUser.class)))
          .thenReturn(Future.succeededFuture(mock(OrganizationUser.class)));

      Future<Boolean> result = service.createOrganizationFromRequest(requestId);

      VertxFutureAssert.assertFutureSuccess(result, ctx, success -> {
        assertThat(success).isTrue();
        verify(createRequestDAO).get(requestId);
        verify(orgDAO).create(any(Organization.class));
        verify(keycloakUserService).addRoleToUser(userId, DxRole.ORG_ADMIN);
        verify(keycloakUserService).addRoleToUser(userId, DxRole.PROVIDER);
        verify(keycloakUserService).setOrganisationDetails(userId, orgId, orgName);
        verify(orgUserDAO).create(any(OrganizationUser.class));
      });
    }
  }

  // ---------------------------------------------------------------------------
  // 7. getOrganizationById
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("getOrganizationById")
  class GetOrganizationByIdTests {

    @Test
    @DisplayName("should return the organization when it exists")
    void success(VertxTestContext ctx) {
      UUID orgId = UUID.randomUUID();
      Organization expected = anOrganization(orgId, "Test Org");

      when(orgDAO.get(orgId)).thenReturn(Future.succeededFuture(expected));

      Future<Organization> result = service.getOrganizationById(orgId);

      VertxFutureAssert.assertFutureSuccess(result, ctx, org -> {
        assertThat(org).isEqualTo(expected);
        assertThat(org.orgName()).isEqualTo("Test Org");
        verify(orgDAO).get(orgId);
      });
    }
  }

  // ---------------------------------------------------------------------------
  // 8. getOrganizations
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("getOrganizations")
  class GetOrganizationsTests {

    @Test
    @DisplayName("should return a list of all organizations")
    void successList(VertxTestContext ctx) {
      Organization org1 = anOrganization(UUID.randomUUID(), "Org 1");
      Organization org2 = anOrganization(UUID.randomUUID(), "Org 2");
      List<Organization> orgList = List.of(org1, org2);

      when(orgDAO.getAll()).thenReturn(Future.succeededFuture(orgList));

      Future<List<Organization>> result = service.getOrganizations();

      VertxFutureAssert.assertFutureSuccess(result, ctx, orgs -> {
        assertThat(orgs).hasSize(2);
        assertThat(orgs).containsExactly(org1, org2);
        verify(orgDAO).getAll();
      });
    }

    @Test
    @DisplayName("should return paginated organizations")
    void successPaginated(VertxTestContext ctx) {
      PaginatedRequest paginatedRequest = mock(PaginatedRequest.class);
      @SuppressWarnings("unchecked")
      PaginatedResult<Organization> paginatedResult = mock(PaginatedResult.class);

      when(orgDAO.getAll(paginatedRequest)).thenReturn(Future.succeededFuture(paginatedResult));

      Future<PaginatedResult<Organization>> result = service.getOrganizations(paginatedRequest);

      VertxFutureAssert.assertFutureSuccess(result, ctx, paginated -> {
        assertThat(paginated).isEqualTo(paginatedResult);
        verify(orgDAO).getAll(paginatedRequest);
      });
    }
  }

  // ---------------------------------------------------------------------------
  // 9. joinOrganizationRequest
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("joinOrganizationRequest")
  class JoinOrganizationRequestTests {

    @Test
    @DisplayName("should delegate to joinRequestDAO.create and return the created join request")
    void success(VertxTestContext ctx) {
      UUID orgId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      OrganizationJoinRequest joinRequest = TestDataFactory.anOrgJoinRequest(orgId, userId);
      OrganizationJoinRequest created = mock(OrganizationJoinRequest.class);

      when(joinRequestDAO.create(joinRequest)).thenReturn(Future.succeededFuture(created));

      Future<OrganizationJoinRequest> result = service.joinOrganizationRequest(joinRequest);

      VertxFutureAssert.assertFutureSuccess(result, ctx, res -> {
        assertThat(res).isEqualTo(created);
        verify(joinRequestDAO).create(joinRequest);
      });
    }
  }

  // ---------------------------------------------------------------------------
  // 10. addUserToOrganizationFromRequest
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("addUserToOrganizationFromRequest")
  class AddUserToOrganizationFromRequestTests {

    @Test
    @DisplayName("should fetch join request, create org user, and set keycloak org details")
    void success(VertxTestContext ctx) {
      UUID requestId = UUID.randomUUID();
      UUID orgId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      String orgName = "Test Org";

      OrganizationJoinRequest joinRequest = mock(OrganizationJoinRequest.class);
      when(joinRequest.organizationId()).thenReturn(orgId);
      when(joinRequest.userId()).thenReturn(userId);
      when(joinRequest.userName()).thenReturn("testuser");
      when(joinRequest.jobTitle()).thenReturn("Developer");
      when(joinRequest.empId()).thenReturn("EMP001");
      when(joinRequest.officialEmail()).thenReturn("user@example.com");

      Organization organization = mock(Organization.class);
      when(organization.orgName()).thenReturn(orgName);

      OrganizationUser createdUser = mock(OrganizationUser.class);

      when(joinRequestDAO.get(requestId)).thenReturn(Future.succeededFuture(joinRequest));
      when(orgDAO.get(orgId)).thenReturn(Future.succeededFuture(organization));
      when(orgUserDAO.create(any(OrganizationUser.class)))
          .thenReturn(Future.succeededFuture(createdUser));
      when(keycloakUserService.setOrganisationDetails(userId, orgId, orgName))
          .thenReturn(Future.succeededFuture(true));

      Future<Boolean> result = service.addUserToOrganizationFromRequest(requestId);

      VertxFutureAssert.assertFutureSuccess(result, ctx, success -> {
        assertThat(success).isTrue();
        verify(joinRequestDAO).get(requestId);
        verify(orgDAO).get(orgId);
        verify(orgUserDAO).create(any(OrganizationUser.class));
        verify(keycloakUserService).setOrganisationDetails(userId, orgId, orgName);
      });
    }
  }

  // ---------------------------------------------------------------------------
  // 11. getOrganizationPendingJoinRequests
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("getOrganizationPendingJoinRequests")
  class GetOrganizationPendingJoinRequestsTests {

    @Test
    @DisplayName("should return paginated pending join requests")
    void paginatedSuccess(VertxTestContext ctx) {
      PaginatedRequest paginatedRequest = mock(PaginatedRequest.class);
      @SuppressWarnings("unchecked")
      PaginatedResult<OrganizationJoinRequest> expectedResult = mock(PaginatedResult.class);

      when(joinRequestDAO.getAllWithFilters(paginatedRequest))
          .thenReturn(Future.succeededFuture(expectedResult));

      Future<PaginatedResult<OrganizationJoinRequest>> result =
          service.getOrganizationPendingJoinRequests(paginatedRequest);

      VertxFutureAssert.assertFutureSuccess(result, ctx, paginated -> {
        assertThat(paginated).isEqualTo(expectedResult);
        verify(joinRequestDAO).getAllWithFilters(paginatedRequest);
      });
    }
  }

  // ---------------------------------------------------------------------------
  // 12. getOrganizationUsers
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("getOrganizationUsers")
  class GetOrganizationUsersTests {

    @Test
    @DisplayName("should return paginated organization users")
    void paginatedSuccess(VertxTestContext ctx) {
      PaginatedRequest paginatedRequest = mock(PaginatedRequest.class);
      @SuppressWarnings("unchecked")
      PaginatedResult<OrganizationUser> expectedResult = mock(PaginatedResult.class);

      when(orgUserDAO.getAllWithFilters(paginatedRequest))
          .thenReturn(Future.succeededFuture(expectedResult));

      Future<PaginatedResult<OrganizationUser>> result =
          service.getOrganizationUsers(paginatedRequest);

      VertxFutureAssert.assertFutureSuccess(result, ctx, paginated -> {
        assertThat(paginated).isEqualTo(expectedResult);
        verify(orgUserDAO).getAllWithFilters(paginatedRequest);
      });
    }
  }

  // ---------------------------------------------------------------------------
  // 13. getOrganizationJoinRequestById
  // ---------------------------------------------------------------------------
  @Nested
  @DisplayName("getOrganizationJoinRequestById")
  class GetOrganizationJoinRequestByIdTests {

    @Test
    @DisplayName("should return the join request when it exists")
    void success(VertxTestContext ctx) {
      UUID requestId = UUID.randomUUID();
      OrganizationJoinRequest expected = mock(OrganizationJoinRequest.class);

      when(joinRequestDAO.get(requestId)).thenReturn(Future.succeededFuture(expected));

      Future<OrganizationJoinRequest> result =
          service.getOrganizationJoinRequestById(requestId);

      VertxFutureAssert.assertFutureSuccess(result, ctx, req -> {
        assertThat(req).isEqualTo(expected);
        verify(joinRequestDAO).get(requestId);
      });
    }
  }
}
