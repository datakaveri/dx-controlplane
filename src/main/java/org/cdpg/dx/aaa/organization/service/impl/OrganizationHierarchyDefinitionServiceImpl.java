package org.cdpg.dx.aaa.organization.service.impl;

import static org.cdpg.dx.common.util.DateTimeHelper.FORMATTER;

import io.vertx.core.Future;
import java.time.LocalDateTime;
import java.util.HashMap;
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
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.database.postgres.models.PaginatedResult;

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
      UUID organizationId, UUID parentNodeId, List<ProposedDepartmentNode> departments) {

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

              return definitionRequestDAO.getAllWithFilters(
                  Map.of(
                      Constants.ORGANIZATION_ID, organizationId.toString(),
                      Constants.STATUS, Status.PENDING.getStatus()));
            })
        .compose(
            pendingRequests -> {
              // One proposal at a time keeps cos_admin's review simple and avoids
              // two pending proposals adding the same department name.
              if (!pendingRequests.isEmpty()) {
                return Future.failedFuture(
                    new DxConflictException(
                        "A department request is already pending for this organization"));
              }

              // Checked now so the org_admin hears about a bad parent or a name
              // clash immediately; checked again when cos_admin approves.
              return hierarchyService.validateAddition(organizationId, parentNodeId, departments);
            })
        .compose(
            valid ->
                definitionRequestDAO.create(
                    new OrganizationHierarchyDefinitionRequest(
                        null,
                        organizationId,
                        parentNodeId,
                        departments,
                        Status.PENDING.getStatus(),
                        null,
                        null,
                        null)));
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
  public Future<PaginatedResult<OrganizationHierarchyDefinitionRequest>> getDefinitionRequests(
      PaginatedRequest request) {
    return definitionRequestDAO.getAllWithFilters(request);
  }

  @Override
  public Future<OrganizationHierarchyDefinitionRequest> updateDefinitionRequestStatus(
      UUID requestId, Status status, String remarks) {

    if (status != Status.GRANTED && status != Status.REJECTED) {
      return Future.failedFuture(
          new DxValidationException("Status must be either granted or rejected"));
    }

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
                    new DxConflictException("Request has already been " + request.status()));
              }

              // Add the departments first: if that fails (e.g. a name now clashes)
              // the request stays pending instead of being marked granted with
              // nothing applied.
              Future<?> applied =
                  status == Status.GRANTED
                      ? hierarchyService.addDepartments(
                          request.organizationId(), request.parentNodeId(), request.departments())
                      : Future.succeededFuture();

              Map<String, Object> updateFields = new HashMap<>();
              updateFields.put(Constants.STATUS, status.getStatus());
              updateFields.put(Constants.UPDATED_AT, LocalDateTime.now().format(FORMATTER));
              if (remarks != null) {
                updateFields.put(Constants.REMARKS, remarks);
              }

              return applied.compose(
                  ignored ->
                      definitionRequestDAO.update(
                          Map.of(
                              "id", requestId.toString(),
                              Constants.STATUS, Status.PENDING.getStatus()),
                          updateFields));
            });
  }
}
