package org.cdpg.dx.aaa.organization.handler;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.email.util.EmailComposer;
import org.cdpg.dx.aaa.organization.audit.OrganizationAuditHelper;
import org.cdpg.dx.aaa.organization.models.OrganisationAuditOperation;
import org.cdpg.dx.aaa.organization.models.OrganizationCreateRequest;
import org.cdpg.dx.aaa.organization.models.Status;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.auditing.v2.model.UserActivityAuditLogBuilder;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.common.util.RoutingContextHelper;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.exception.DxConflictException;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.request.PaginationRequestBuilder;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.common.util.CpRoutingContextHelper;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

import static org.cdpg.dx.aaa.common.Constants.ID;
import static org.cdpg.dx.aaa.organization.config.Constants.*;
import static org.cdpg.dx.aaa.organization.config.Constants.API_TO_DB_ORG_CREATE_REQUEST;
import static org.cdpg.dx.aaa.organization.config.Constants.CREATED_AT;
import static org.cdpg.dx.database.postgres.util.Constants.DEFAULT_SORTING_ORDER;

public class OrganizationCreateRequestHandler {
  private static final Logger LOGGER = LogManager.getLogger(OrganizationCreateRequestHandler.class);

  private final OrganizationService organizationService;
  private final KeycloakUserService keycloakUserService;
  private final URNGenerator urnGenerator;
  private final EmailComposer emailComposer;

  public OrganizationCreateRequestHandler(
      OrganizationService organizationService,
      KeycloakUserService keycloakUserService,
      EmailComposer emailComposer,
      URNGenerator urnGenerator) {
    this.organizationService = organizationService;
    this.urnGenerator = urnGenerator;
    this.keycloakUserService = keycloakUserService;
    this.emailComposer = emailComposer;
  }

  public void createOrganisationRequest(RoutingContext ctx) {

    if (ctx.body() == null || ctx.body().isEmpty() || ctx.body().asJsonObject() == null) {
      ctx.fail(new DxBadRequestException("Request Body is required and must be valid JSON."));
      return;
    }

    JsonObject orgRequestJson = ctx.body().asJsonObject();
    DxUser dxUser = RoutingContextHelper.fromPrincipal(ctx);
    UUID userId = dxUser.sub();
    User user = ctx.user();

    String orgName = orgRequestJson.getString("name");

    keycloakUserService.getUserById(userId)
      .compose(keycloakUser -> {

        // enrich request with Keycloak data
        orgRequestJson.put("requested_by", userId.toString());
        orgRequestJson.put("user_name", keycloakUser.name());

        OrganizationCreateRequest organizationCreateRequest =
          OrganizationCreateRequest.fromJson(orgRequestJson);

        return organizationService
          .getOrganizationCreateRequestsByUserId(userId)
          .compose(createRequests -> {

            // check if user already has a request
            for (OrganizationCreateRequest request : createRequests) {
              if (request.requestedBy().equals(userId)) {
                return Future.failedFuture(
                  new DxConflictException(
                    "Organisation create request already granted/ pending for this user"));
              }
            }

            return organizationService
              .getAllPendingGrantedOrganizationCreateRequests()
              .compose(requests -> {

                // check org name uniqueness
                for (OrganizationCreateRequest request : requests) {
                  if (request.name().equalsIgnoreCase(orgName)) {
                    return Future.failedFuture(
                      new DxConflictException(
                        "Organisation name already exists/ under review"));
                  }
                }

                // check manager email uniqueness
                for (OrganizationCreateRequest request : requests) {
                  if (request.managerEmail()
                    .equalsIgnoreCase(organizationCreateRequest.managerEmail())) {
                    return Future.failedFuture(
                      new DxConflictException(
                        "Manager email is already in use for another organisation request"));
                  }
                }

                return organizationService
                  .createOrganizationRequest(organizationCreateRequest);
              });
          });
      })
      .onSuccess(requests -> {

        UserActivityAuditLogBuilder auditLogBuilder =
          OrganizationAuditHelper.buildOrganisationAudit(
            ctx, requests.toJson(), OrganisationAuditOperation.REQUEST_ORG_CREATE);

        CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

        ResponseBuilder.sendSuccess(ctx, requests, urnGenerator);
        emailComposer.sendEmailForCreatingOrg(requests, user);
      })
      .onFailure(ctx::fail);
  }


  public void updateOrganisationRequest(RoutingContext ctx) {

    JsonObject OrgRequestJson = ctx.body().asJsonObject();

    UUID requestId = UUID.fromString(OrgRequestJson.getString("req_id"));
    Status status = Status.fromString(OrgRequestJson.getString("status"));

    JsonObject responseObject = OrgRequestJson.copy();
    responseObject.remove("status");

    organizationService
        .updateOrganizationCreateRequestStatus(requestId, status)
        .onSuccess(
            updated -> {
              UserActivityAuditLogBuilder auditLogBuilder =
                OrganizationAuditHelper.buildOrganisationAudit(
                  ctx, new JsonObject().put(ID,requestId.toString()), OrganisationAuditOperation.UPDATE_ORG_CREATE_REQUEST);

              CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

              ResponseBuilder.sendSuccess(ctx, "Updated Sucessfully", urnGenerator);
              Future<Void> future =
                  emailComposer.sendUserEmailForOrgCreateRequestApproval(requestId, status);
            })
        .onFailure(ctx::fail);
  }

  public void deleteOrganizationCreateRequest(RoutingContext ctx) {
    UUID requestId = UUID.fromString(ctx.pathParam("id"));
    DxUser dxUser = RoutingContextHelper.fromPrincipal(ctx);
    UUID userId = dxUser.sub();

    organizationService
        .getOrganizationCreateRequestById(requestId)
        .compose(
            request -> {
              if (request == null) {
                ctx.fail(new DxBadRequestException("Organization request not found"));
                return Future.failedFuture(
                    new DxBadRequestException("Organization request not found"));
              }

              if (!request.status().equals(Status.PENDING.getStatus())) {
                ctx.fail(new DxBadRequestException("Only pending requests can be deleted"));
                return Future.failedFuture(
                    new DxBadRequestException("Only pending requests can be deleted"));
              }

              if (!request.requestedBy().equals(userId)) {
                ctx.fail(new DxForbiddenException("User is not authorized to delete this request"));
                return Future.failedFuture(
                    new DxForbiddenException("User is not authorized to delete this request"));
              }

              return organizationService
                  .deleteOrganizationRequestById(requestId)
                  .compose(
                      deleted -> {
                        if (!deleted) {
                          return Future.failedFuture(
                              new DxNotFoundException(
                                  "Failed to delete organization request with ID: " + requestId));
                        }
                        UserActivityAuditLogBuilder auditLogBuilder =
                          OrganizationAuditHelper.buildOrganisationAudit(
                            ctx, new JsonObject().put(ID,requestId.toString()), OrganisationAuditOperation.DELETE_PENDING_ORG_CREATE_REQUEST);

                        CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

                        ResponseBuilder.sendSuccess(
                            ctx, "Organization request deleted successfully", urnGenerator);
                        return Future.succeededFuture(true);
                      });
            })
        .onFailure(ctx::fail);
  }

  public void getUserOrganisationRequest(RoutingContext ctx) {
    DxUser dxUser = RoutingContextHelper.fromPrincipal(ctx);
    UUID userId = dxUser.sub();

    organizationService
        .getOrganizationCreateRequestsByUserId(userId)
        .compose(
            requests -> {
              List<JsonObject> result =
                  requests.stream()
                      .map(OrganizationCreateRequest::toJson)
                      .collect(Collectors.toList());
              return Future.succeededFuture(result);
            })
        .onSuccess(
            result -> {
              UserActivityAuditLogBuilder auditLogBuilder =
                OrganizationAuditHelper.buildOrganisationAudit(
                  ctx, new JsonObject(), OrganisationAuditOperation.GET);
              CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);
              ResponseBuilder.sendSuccess(ctx, result, urnGenerator);
            })
        .onFailure(
            err -> {
              LOGGER.error(
                  "Failed to fetch organization requests for user {}: {}",
                  userId,
                  err.getMessage());
              ctx.fail(err);
            });
  }

  public void getAllOrganisationRequest(RoutingContext ctx) {

    PaginatedRequest request =
        PaginationRequestBuilder.from(ctx)
            .allowedFiltersDbMap(ALLOWED_FILTER_MAP_FOR_ORG_CREATE_REQUEST)
            .apiToDbMap(API_TO_DB_ORG_CREATE_REQUEST)
            .allowedTimeFields(Set.of(CREATED_AT))
            .defaultTimeField(CREATED_AT)
            .defaultSort(CREATED_AT, DEFAULT_SORTING_ORDER)
            .allowedSortFields(API_TO_DB_ORG_CREATE_REQUEST.keySet())
            .build();

    organizationService
        .getAllOrganizationCreateRequests(request)
        .onSuccess(
            res -> {
              UserActivityAuditLogBuilder auditLogBuilder =
                OrganizationAuditHelper.buildOrganisationAudit(
                  ctx, new JsonObject(), OrganisationAuditOperation.GET);
              CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);
              ResponseBuilder.sendSuccess(ctx, res.data(), res.paginationInfo(), urnGenerator);
            })
        .onFailure(ctx::fail);
  }
}