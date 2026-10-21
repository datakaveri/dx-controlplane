package org.cdpg.dx.aaa.organization.service.impl;

import static org.cdpg.dx.common.util.DateTimeHelper.FORMATTER;

import io.vertx.core.Future;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.cdpg.dx.aaa.organization.config.Constants;
import org.cdpg.dx.aaa.organization.dao.OrganizationDAO;
import org.cdpg.dx.aaa.organization.dao.OrganizationHierarchyDefinitionRequestDAO;
import org.cdpg.dx.aaa.organization.models.OrganizationHierarchyDefinitionRequest;
import org.cdpg.dx.aaa.organization.models.ProposedDepartmentNode;
import org.cdpg.dx.aaa.organization.models.Status;
import org.cdpg.dx.aaa.organization.service.OrganizationHierarchyDefinitionService;
import org.cdpg.dx.aaa.organization.service.OrganizationHierarchyService;
import org.cdpg.dx.common.exception.DxConflictException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.exception.DxValidationException;

public class OrganizationHierarchyDefinitionServiceImpl
    implements OrganizationHierarchyDefinitionService {

  private final OrganizationHierarchyDefinitionRequestDAO definitionRequestDAO;
  private final OrganizationDAO organizationDAO;
  private final OrganizationHierarchyService hierarchyService;

  public OrganizationHierarchyDefinitionServiceImpl(
      OrganizationHierarchyDefinitionRequestDAO definitionRequestDAO,
      OrganizationDAO organizationDAO,
      OrganizationHierarchyService hierarchyService) {
    this.definitionRequestDAO = definitionRequestDAO;
    this.organizationDAO = organizationDAO;
    this.hierarchyService = hierarchyService;
  }

  @Override
  public Future<OrganizationHierarchyDefinitionRequest> createDefinitionRequest(
      UUID organizationId, List<ProposedDepartmentNode> departments) {

    if (departments == null || departments.isEmpty()) {
      return Future.failedFuture(
          new DxValidationException("Proposed department tree cannot be empty"));
    }

    return organizationDAO
        .get(organizationId)
        .compose(
            organization -> {
              if (organization == null) {
                return Future.failedFuture(new DxNotFoundException("Organization not found"));
              }

              return hierarchyService.hasHierarchy(organizationId);
            })
        .compose(
            hasHierarchy -> {
              if (hasHierarchy) {
                return Future.failedFuture(
                    new DxConflictException(
                        "Organization already has a department hierarchy defined"));
              }

              return definitionRequestDAO.getAllWithFilters(
                  Map.of(
                      Constants.ORGANIZATION_ID, organizationId.toString(),
                      Constants.STATUS, Status.PENDING.getStatus()));
            })
        .compose(
            pendingRequests -> {
              if (!pendingRequests.isEmpty()) {
                return Future.failedFuture(
                    new DxConflictException(
                        "A hierarchy definition request is already pending for this"
                            + " organization"));
              }

              OrganizationHierarchyDefinitionRequest request =
                  new OrganizationHierarchyDefinitionRequest(
                      null,
                      organizationId,
                      departments,
                      Status.PENDING.getStatus(),
                      null,
                      null,
                      null);

              return definitionRequestDAO.create(request);
            });
  }

  @Override
  public Future<OrganizationHierarchyDefinitionRequest> getDefinitionRequestById(UUID requestId) {
    return definitionRequestDAO.get(requestId);
  }

  @Override
  public Future<List<OrganizationHierarchyDefinitionRequest>> getAllDefinitionRequests(
      UUID organizationId) {
    return definitionRequestDAO.getAllWithFilters(
        Map.of(Constants.ORGANIZATION_ID, organizationId.toString()));
  }

  @Override
  public Future<OrganizationHierarchyDefinitionRequest> updateDefinitionRequestStatus(
      UUID requestId, Status status, String remarks) {

    return definitionRequestDAO
        .get(requestId)
        .compose(
            request -> {
              if (request == null) {
                return Future.failedFuture(
                    new DxNotFoundException("Hierarchy definition request not found"));
              }

              if (!Status.PENDING.getStatus().equalsIgnoreCase(request.status())) {
                return Future.failedFuture(
                    new DxConflictException(
                        "Request has already been " + request.status()));
              }

              Map<String, Object> updateFields =
                  remarks != null
                      ? Map.of(
                          Constants.STATUS, status.getStatus(),
                          Constants.REMARKS, remarks,
                          Constants.UPDATED_AT, LocalDateTime.now().format(FORMATTER))
                      : Map.of(
                          Constants.STATUS, status.getStatus(),
                          Constants.UPDATED_AT, LocalDateTime.now().format(FORMATTER));

              return definitionRequestDAO
                  .update(Map.of("id", requestId.toString()), updateFields)
                  .compose(
                      updatedRequest -> {
                        if (status != Status.GRANTED) {
                          return Future.succeededFuture(updatedRequest);
                        }

                        return hierarchyService
                            .bulkCreateTree(request.organizationId(), request.departments())
                            .map(createdNodes -> updatedRequest);
                      });
            });
  }
}
