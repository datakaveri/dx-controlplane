package org.cdpg.dx.aaa.organization.service;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.cdpg.dx.aaa.organization.models.*;
import org.cdpg.dx.aaa.organization.config.Constants;
import org.cdpg.dx.aaa.organization.dao.*;
import org.cdpg.dx.auth.authorization.model.DxRole;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.request.PaginationRequestBuilder;
import org.cdpg.dx.common.util.PaginationInfo;
import org.cdpg.dx.database.postgres.models.PaginatedResult;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import io.vertx.core.Future;
import static org.mockito.Mockito.when;



import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith({VertxExtension.class,MockitoExtension.class})
class OrganizationServiceTest {

  @Mock
  private OrganizationDAOFactory factory;

  @Mock
  private OrganizationCreateRequestDAO createRequestDAO;

  @Mock
  private OrganizationJoinRequestDAO joinRequestDAO;

  @Mock
  private OrganizationUserDAO orgUserDAO;

  @Mock
  private OrganizationDAO orgDAO;

  @Mock
  private KeycloakUserService keycloakUserService;

  private OrganizationServiceImpl organizationService;


  @BeforeEach
  public void setupOrganizationServiceTest(VertxTestContext vertxTestContext) {
    // Create real dependencies that don't need mocking
    Vertx vertx = Vertx.vertx();

    // Define factory behavior
    when(factory.organizationCreateRequest()).thenReturn(createRequestDAO);
    when(factory.organizationUserDAO()).thenReturn(orgUserDAO);
    when(factory.organizationDAO()).thenReturn(orgDAO);
    when(factory.organizationJoinRequestDAO()).thenReturn(joinRequestDAO);
//    when(factory.providerRoleRequestDAO()).thenReturn(providerRequestDAO);

    // Create the service under test
    organizationService = new OrganizationServiceImpl(factory, keycloakUserService);

    vertxTestContext.completeNow();
  }


  @Test
  public void testCreateOrganizationRequest(VertxTestContext vertxTestContext) {

    OrganizationCreateRequest request = new OrganizationCreateRequest(
      UUID.randomUUID(), // requestId,
      UUID.randomUUID(), // userId
      "Test Organization", // orgName
      "name",
      "entityType",
      "name",
      "name",
      "name",
      "name",
      "name",
      "name",
      "name",
      "name",
      "name",
      "name",
      "name",
      "name",
      "name",
      null,
      null
    );

    Future<OrganizationCreateRequest> expectedFuture = Future.succeededFuture(request);

    // Mock DAO behavior
    when(createRequestDAO.create(request)).thenReturn(expectedFuture);

    // Execute and verify
    Future<OrganizationCreateRequest> result = organizationService.createOrganizationRequest(request);

    result.onComplete(ar -> {
      if (ar.succeeded()) {
        verify(createRequestDAO).create(request);
        assertEquals(request, ar.result());
        vertxTestContext.completeNow();
      } else {
        vertxTestContext.failNow(ar.cause());
      }
    });
  }


  @Test
  @DisplayName("Success - get both pending and granted organization requests by userId")
  public void testGetOrganizationCreateRequestsByUserId_success(VertxTestContext testContext) {
    // Arrange
    UUID userId = UUID.randomUUID();

    // Create mock request objects
    OrganizationCreateRequest pendingReq = mock(OrganizationCreateRequest.class);
    OrganizationCreateRequest grantedReq = mock(OrganizationCreateRequest.class);

    List<OrganizationCreateRequest> pendingList = List.of(pendingReq);
    List<OrganizationCreateRequest> grantedList = List.of(grantedReq);

    // Set up expected filters
    Map<String, Object> pendingFilter = Map.of(
      Constants.REQUESTED_BY, userId.toString(),
      Constants.STATUS, Status.PENDING.getStatus()
    );

    Map<String, Object> grantedFilter = Map.of(
      Constants.REQUESTED_BY, userId.toString(),
      Constants.STATUS, Status.GRANTED.getStatus()
    );

    // Mock DAO responses
    when(createRequestDAO.getAllWithFilters(pendingFilter)).thenReturn(Future.succeededFuture(pendingList));
    when(createRequestDAO.getAllWithFilters(grantedFilter)).thenReturn(Future.succeededFuture(grantedList));

    // Act
    organizationService.getOrganizationCreateRequestsByUserId(userId).onComplete(ar -> {
      if (ar.succeeded()) {
        List<OrganizationCreateRequest> result = ar.result();

        // Assert
        verify(createRequestDAO).getAllWithFilters(pendingFilter);
        verify(createRequestDAO).getAllWithFilters(grantedFilter);
        assertEquals(2, result.size());
        assertTrue(result.contains(pendingReq));
        assertTrue(result.contains(grantedReq));

        testContext.completeNow();
      } else {
        testContext.failNow(ar.cause());
      }
    });
  }


  @Test
  @DisplayName("Success - get all pending and granted organization requests")
  public void testGetAllPendingGrantedOrganizationCreateRequests_success(VertxTestContext testContext) {
    // Arrange
    OrganizationCreateRequest pendingReq = mock(OrganizationCreateRequest.class);
    OrganizationCreateRequest grantedReq = mock(OrganizationCreateRequest.class);

    List<OrganizationCreateRequest> pendingList = List.of(pendingReq);
    List<OrganizationCreateRequest> grantedList = List.of(grantedReq);

    Map<String, Object> pendingFilter = Map.of(
      Constants.STATUS, Status.PENDING.getStatus()
    );
    Map<String, Object> grantedFilter = Map.of(
      Constants.STATUS, Status.GRANTED.getStatus()
    );

    // Mock DAO responses
    when(createRequestDAO.getAllWithFilters(pendingFilter)).thenReturn(Future.succeededFuture(pendingList));
    when(createRequestDAO.getAllWithFilters(grantedFilter)).thenReturn(Future.succeededFuture(grantedList));

    // Act
    organizationService.getAllPendingGrantedOrganizationCreateRequests().onComplete(ar -> {
      if (ar.succeeded()) {
        // Assert
        List<OrganizationCreateRequest> result = ar.result();
        verify(createRequestDAO).getAllWithFilters(pendingFilter);
        verify(createRequestDAO).getAllWithFilters(grantedFilter);
        assertEquals(2, result.size());
        assertTrue(result.contains(pendingReq));
        assertTrue(result.contains(grantedReq));
        testContext.completeNow();
      } else {
        testContext.failNow(ar.cause());
      }
    });
  }


  @Test
  @DisplayName("Success - get all organization create requests with pagination")
  public void testGetAllOrganizationCreateRequestsWithPagination_success(VertxTestContext testContext) {
    // Arrange
    PaginatedRequest paginatedRequest = mock(PaginatedRequest.class);

    OrganizationCreateRequest req1 = mock(OrganizationCreateRequest.class);
    OrganizationCreateRequest req2 = mock(OrganizationCreateRequest.class);
    List<OrganizationCreateRequest> requestList = List.of(req1, req2);

    PaginationInfo paginationInfo = mock(PaginationInfo.class);
    PaginatedResult<OrganizationCreateRequest> expectedResult = new PaginatedResult<>(paginationInfo, requestList);

    // Mock DAO behavior
    when(createRequestDAO.getAllWithFilters(paginatedRequest))
      .thenReturn(Future.succeededFuture(expectedResult));

    // Act
    organizationService.getAllOrganizationCreateRequests(paginatedRequest).onComplete(ar -> {
      if (ar.succeeded()) {
        // Assert
        verify(createRequestDAO).getAllWithFilters(paginatedRequest);
        assertEquals(expectedResult, ar.result());
        testContext.completeNow();
      } else {
        testContext.failNow(ar.cause());
      }
    });

  }


  @Test
  @DisplayName("Success - get organization create request by ID")
  public void testGetOrganizationCreateRequestById_success(VertxTestContext testContext) {
    // Arrange
    UUID requestId = UUID.randomUUID();
    OrganizationCreateRequest expectedRequest = mock(OrganizationCreateRequest.class);

    // Mock DAO behavior
    when(createRequestDAO.get(requestId)).thenReturn(Future.succeededFuture(expectedRequest));

    // Act
    organizationService.getOrganizationCreateRequestById(requestId).onComplete(ar -> {
      if (ar.succeeded()) {
        // Assert
        verify(createRequestDAO).get(requestId);
        assertEquals(expectedRequest, ar.result());
        testContext.completeNow();
      } else {
        testContext.failNow(ar.cause());
      }
    });
  }

  @Test
  @DisplayName("Success - get organization create request by ID")
  public void testGetOrganizationJoinRequestById_success(VertxTestContext testContext) {
    // Arrange
    UUID requestId = UUID.randomUUID();
    OrganizationJoinRequest expectedRequest = mock(OrganizationJoinRequest.class);

    // Mock DAO behavior
    when(joinRequestDAO.get(requestId)).thenReturn(Future.succeededFuture(expectedRequest));

    // Act
    organizationService.getOrganizationJoinRequestById(requestId).onComplete(ar -> {
      if (ar.succeeded()) {
        // Assert
        verify(joinRequestDAO).get(requestId);
        assertEquals(expectedRequest, ar.result());
        testContext.completeNow();
      } else {
        testContext.failNow(ar.cause());
      }
    });
  }

//  @Test
//  @DisplayName("Success - Update org request status (not GRANTED)")
//  public void testUpdateOrganizationCreateRequestStatus_pending(VertxTestContext context) {
//    UUID requestId = UUID.randomUUID();
//    Status status = Status.PENDING;
//
//    Map<String, Object> condition = Map.of(Constants.ORG_CREATE_ID, requestId.toString());
//    Map<String, Object> updateMap = Map.of(Constants.STATUS, status.getStatus());
//
//    when(createRequestDAO.update(condition, updateMap)).thenReturn(Future.succeededFuture(true));
//
//    organizationService.updateOrganizationCreateRequestStatus(requestId, status).onComplete(ar -> {
//      if (ar.succeeded()) {
//        assertTrue(ar.result());
//        verify(createRequestDAO).update(condition, updateMap);
//        context.completeNow();
//      } else {
//        context.failNow(ar.cause());
//      }
//    });
//  }


  @Test
  @DisplayName("Success - create organization from request")
  void testCreateOrganizationFromRequest_success(VertxTestContext testContext) {
    // Arrange
    UUID requestId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    String orgName = "Test Org";

    // Mock OrganizationCreateRequest
    OrganizationCreateRequest request = mock(OrganizationCreateRequest.class);
    when(request.name()).thenReturn(orgName);
    when(request.logoPath()).thenReturn("logo.png");
    when(request.entityType()).thenReturn("entityType");
    when(request.orgSector()).thenReturn("sector");
    when(request.websiteLink()).thenReturn("website");
    when(request.address()).thenReturn("address");
    when(request.certificatePath()).thenReturn("cert.pdf");
    when(request.pancardPath()).thenReturn("pan.pdf");
    when(request.relevantDocPath()).thenReturn("doc.pdf");
    when(request.orgDocuments()).thenReturn("documents");
    when(request.requestedBy()).thenReturn(userId);
    when(request.requestedBy()).thenReturn(userId);
    when(request.userName()).thenReturn("user");
    when(request.jobTitle()).thenReturn("job");
    when(request.empId()).thenReturn("emp");
    when(request.orgManagerphoneNo()).thenReturn("1234567890");

    // Mock Organization
    Organization createdOrg = mock(Organization.class);
    UUID orgId = UUID.randomUUID();
    when(createdOrg.id()).thenReturn(orgId);
    when(createdOrg.orgName()).thenReturn(orgName);

    // Mock DAO/service responses
    when(createRequestDAO.get(requestId)).thenReturn(Future.succeededFuture(request));
    when(orgDAO.create(any())).thenReturn(Future.succeededFuture(createdOrg));
    when(keycloakUserService.addRoleToUser(userId, DxRole.ORG_ADMIN)).thenReturn(Future.succeededFuture(true));
    when(keycloakUserService.setOrganisationDetails(userId, orgId, orgName)).thenReturn(Future.succeededFuture(true));
    when(orgUserDAO.create(any())).thenReturn(Future.succeededFuture(mock(OrganizationUser.class)));

    // Act
    organizationService.createOrganizationFromRequest(requestId).onComplete(ar -> {
      // Assert
      if (ar.succeeded()) {
        assertTrue(ar.result());
        verify(createRequestDAO).get(requestId);
        verify(orgDAO).create(any());
        verify(keycloakUserService).addRoleToUser(userId, DxRole.ORG_ADMIN);
        verify(keycloakUserService).setOrganisationDetails(userId, orgId, orgName);
        verify(orgUserDAO).create(any());
        testContext.completeNow();
      } else {
        testContext.failNow(ar.cause());
      }
    });
  }


  @Test
  @DisplayName("Success - get organization by ID")
  void testGetOrganizationById_success(VertxTestContext testContext) {
    UUID orgId = UUID.randomUUID();
    Organization expectedOrg = mock(Organization.class);

    when(orgDAO.get(orgId)).thenReturn(Future.succeededFuture(expectedOrg));

    organizationService.getOrganizationById(orgId).onComplete(ar -> {
      if (ar.succeeded()) {
        assertEquals(expectedOrg, ar.result());
        verify(orgDAO).get(orgId);
        testContext.completeNow();
      } else {
        testContext.failNow(ar.cause());
      }
    });
  }

  @Test
  @DisplayName("Success - get all organizations")
  void testGetOrganizations_success(VertxTestContext testContext) {
    List<Organization> orgList = List.of(mock(Organization.class), mock(Organization.class));

    when(orgDAO.getAll()).thenReturn(Future.succeededFuture(orgList));

    organizationService.getOrganizations().onComplete(ar -> {
      if (ar.succeeded()) {
        assertEquals(orgList, ar.result());
        verify(orgDAO).getAll();
        testContext.completeNow();
      } else {
        testContext.failNow(ar.cause());
      }
    });
  }

  @Test
  @DisplayName("Success - get organizations with pagination")
  void testGetOrganizationsWithPagination_success(VertxTestContext testContext) {
    PaginatedRequest paginatedRequest = mock(PaginatedRequest.class);
    PaginatedResult<Organization> paginatedResult = mock(PaginatedResult.class);

    when(orgDAO.getAll(paginatedRequest)).thenReturn(Future.succeededFuture(paginatedResult));

    organizationService.getOrganizations(paginatedRequest).onComplete(ar -> {
      if (ar.succeeded()) {
        assertEquals(paginatedResult, ar.result());
        verify(orgDAO).getAll(paginatedRequest);
        testContext.completeNow();
      } else {
        testContext.failNow(ar.cause());
      }
    });
  }


  //  @Test
//  @DisplayName("Success - update organization by ID")
//  void testUpdateOrganizationById_success(VertxTestContext testContext) {
//    UUID orgId = UUID.randomUUID();
//    UpdateOrgDTO updateOrgDTO = mock(UpdateOrgDTO.class);
//    Map<String, Object> updateMap = Map.of("name", "Updated Org");
//    Organization updatedOrg = mock(Organization.class);
//
//    when(updateOrgDTO.toNonEmptyFieldsMap()).thenReturn(updateMap);
//    when(orgDAO.update(anyMap(), eq(updateMap))).thenReturn(Future.succeededFuture(true));
//    when(orgDAO.get(orgId)).thenReturn(Future.succeededFuture(updatedOrg));
//
//    organizationService.updateOrganizationById(orgId, updateOrgDTO).onComplete(ar -> {
//      if (ar.succeeded()) {
//        assertEquals(updatedOrg, ar.result());
//        verify(orgDAO).update(anyMap(), eq(updateMap));
//        verify(orgDAO).get(orgId);
//        testContext.completeNow();
//      } else {
//        testContext.failNow(ar.cause());
//      }
//    });
//  }
//
//  @Test
//  @DisplayName("Success - delete organization by ID")
//  void testDeleteOrganization_success(VertxTestContext testContext) {
//    UUID orgId = UUID.randomUUID();
//
//    when(orgDAO.delete(orgId)).thenReturn(Future.succeededFuture(true));
//
//    organizationService.deleteOrganization(orgId).onComplete(ar -> {
//      if (ar.succeeded()) {
//        assertTrue(ar.result());
//        verify(orgDAO).delete(orgId);
//        testContext.completeNow();
//      } else {
//        testContext.failNow(ar.cause());
//      }
//    });
//  }
//
  @Test
  @DisplayName("Success - join organization request")
  void testJoinOrganizationRequest_success(VertxTestContext testContext) {
    OrganizationJoinRequest joinRequest = mock(OrganizationJoinRequest.class);
    OrganizationJoinRequest createdRequest = mock(OrganizationJoinRequest.class);

    when(joinRequestDAO.create(joinRequest)).thenReturn(Future.succeededFuture(createdRequest));

    organizationService.joinOrganizationRequest(joinRequest).onComplete(ar -> {
      if (ar.succeeded()) {
        assertEquals(createdRequest, ar.result());
        verify(joinRequestDAO).create(joinRequest);
        testContext.completeNow();
      } else {
        testContext.failNow(ar.cause());
      }
    });
  }


  @Test
  @DisplayName("Success - add user to organization from join request")
  void testAddUserToOrganizationFromRequest_success(VertxTestContext testContext) {
    UUID requestId = UUID.randomUUID();
    UUID orgId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    String orgName = "Test Org";

    // Mock join request
    OrganizationJoinRequest joinRequest = mock(OrganizationJoinRequest.class);
    when(joinRequest.organizationId()).thenReturn(orgId);
    when(joinRequest.userId()).thenReturn(userId);
    when(joinRequest.userName()).thenReturn("user");
    when(joinRequest.jobTitle()).thenReturn("job");
    when(joinRequest.empId()).thenReturn("emp");

    // Mock organization
    Organization organization = mock(Organization.class);
    when(organization.orgName()).thenReturn(orgName);

    // Mock created org user
    OrganizationUser createdUser = mock(OrganizationUser.class);

    // Mock DAO/service responses
    when(joinRequestDAO.get(requestId)).thenReturn(Future.succeededFuture(joinRequest));
    when(orgDAO.get(orgId)).thenReturn(Future.succeededFuture(organization));
    when(orgUserDAO.create(any())).thenReturn(Future.succeededFuture(createdUser));
    when(keycloakUserService.setOrganisationDetails(userId, orgId, orgName)).thenReturn(Future.succeededFuture(true));

    // Use reflection or make the method package-private for testing
    organizationService.addUserToOrganizationFromRequest(requestId).onComplete(ar -> {
      if (ar.succeeded()) {
        assertTrue(ar.result());
        verify(joinRequestDAO).get(requestId);
        verify(orgDAO).get(orgId);
        verify(orgUserDAO).create(any());
        verify(keycloakUserService).setOrganisationDetails(userId, orgId, orgName);
        testContext.completeNow();
      } else {
        testContext.failNow(ar.cause());
      }
    });
  }


  @Test
  @DisplayName("Success - get pending organization join requests")
  void testGetOrganizationPendingJoinRequests_success(VertxTestContext testContext) {
    PaginatedRequest paginatedRequest = mock(PaginatedRequest.class);
    PaginatedResult<OrganizationJoinRequest> expectedResult = mock(PaginatedResult.class);

    when(joinRequestDAO.getAllWithFilters(paginatedRequest)).thenReturn(Future.succeededFuture(expectedResult));

    organizationService.getOrganizationPendingJoinRequests(paginatedRequest).onComplete(ar -> {
      if (ar.succeeded()) {
        assertEquals(expectedResult, ar.result());
        verify(joinRequestDAO).getAllWithFilters(paginatedRequest);
        testContext.completeNow();
      } else {
        testContext.failNow(ar.cause());
      }
    });
  }

  @Test
  @DisplayName("Success - get organization users")
  void testGetOrganizationUsers_success(VertxTestContext testContext) {
    PaginatedRequest paginatedRequest = mock(PaginatedRequest.class);
    PaginatedResult<OrganizationUser> expectedResult = mock(PaginatedResult.class);

    when(orgUserDAO.getAllWithFilters(paginatedRequest)).thenReturn(Future.succeededFuture(expectedResult));

    organizationService.getOrganizationUsers(paginatedRequest).onComplete(ar -> {
      if (ar.succeeded()) {
        assertEquals(expectedResult, ar.result());
        verify(orgUserDAO).getAllWithFilters(paginatedRequest);
        testContext.completeNow();
      } else {
        testContext.failNow(ar.cause());
      }
    });

  }


//    @Test
//    @DisplayName("Success - update user role in organization")
//    void testUpdateUserRole_success(VertxTestContext testContext){
//      UUID orgId = UUID.randomUUID();
//      UUID userId = UUID.randomUUID();
//      Role role = Role.USER;
//
//      Map<String, Object> conditionMap = Map.of(
//        Constants.ORGANIZATION_ID, orgId.toString(),
//        Constants.USER_ID, userId.toString()
//      );
//      Map<String, Object> updateDataMap = Map.of(
//        Constants.ROLE, role.getRoleName()
//      );
//
//      when(orgUserDAO.update(conditionMap, updateDataMap)).thenReturn(Future.succeededFuture(true));
//
//      organizationService.updateUserRole(orgId, userId, role).onComplete(ar -> {
//        if (ar.succeeded()) {
//          assertTrue(ar.result());
//          verify(orgUserDAO).update(conditionMap, updateDataMap);
//          testContext.completeNow();
//        } else {
//          testContext.failNow(ar.cause());
//        }
//      });
//    }
//
//    @Test
//    @DisplayName("Success - check if user is org admin")
//    public void testIsOrgAdmin_success (VertxTestContext testContext){
//      UUID orgId = UUID.randomUUID();
//      UUID userId = UUID.randomUUID();
//
//      when(orgUserDAO.isOrgAdmin(orgId, userId)).thenReturn(Future.succeededFuture(true));
//
//      organizationService.isOrgAdmin(orgId, userId).onComplete(ar -> {
//        if (ar.succeeded()) {
//          assertTrue(ar.result());
//          verify(orgUserDAO).isOrgAdmin(orgId, userId);
//          testContext.completeNow();
//        } else {
//          testContext.failNow(ar.cause());
//        }
//      });
//    }
//
//    @Test
//    @DisplayName("Success - delete organization user")
//    void testDeleteOrganizationUser_success (VertxTestContext testContext){
//      UUID orgId = UUID.randomUUID();
//      UUID userId = UUID.randomUUID();
//
//      when(orgUserDAO.deleteUserByOrgId(orgId, userId)).thenReturn(Future.succeededFuture(true));
//
//      organizationService.deleteOrganizationUser(orgId, userId).onComplete(ar -> {
//        if (ar.succeeded()) {
//          assertTrue(ar.result());
//          verify(orgUserDAO).deleteUserByOrgId(orgId, userId);
//          testContext.completeNow();
//        } else {
//          testContext.failNow(ar.cause());
//        }
//      });
//    }


  }


