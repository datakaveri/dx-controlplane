package org.cdpg.dx.aaa.admin.handler;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.audit.util.AuditingHelper;
import org.cdpg.dx.aaa.credit.service.CreditService;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.aaa.user.service.UserService;
import org.cdpg.dx.auditing.model.AuditLog;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.request.PaginationRequestBuilder;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.common.util.RequestHelper;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.common.util.RoutingContextHelper;
import org.cdpg.dx.keycloak.config.KeycloakConstants;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

import java.util.*;

public class AdminHandler {

  private static final Logger LOGGER = LogManager.getLogger(AdminHandler.class);
  private final UserService userService;
  private final KeycloakUserService keycloakUserService;
  private final CreditService creditService;
  private final OrganizationService organizationService;

  public AdminHandler(UserService userService, KeycloakUserService keycloakUserService,
                      CreditService creditService, OrganizationService organizationService) {
    this.userService = userService;
    this.keycloakUserService = keycloakUserService;
    this.creditService = creditService;
    this.organizationService = organizationService;
  }

  public void getDxUserInfo(RoutingContext ctx) {
    User user = ctx.user();
    System.out.println("User ID: " + user.subject());

    userService.getUserInfoByID(UUID.fromString(user.subject()))
      .compose(userService::getUserInfo)
      .onSuccess(response -> {
        AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
          RoutingContextHelper.getRequestPath(ctx), "GET", "Get DxUser Info");
        RoutingContextHelper.setAuditingLog(ctx, auditLog);
        ResponseBuilder.sendSuccess(ctx, response);
      })
      .onFailure(err -> {
        LOGGER.error("Failed to get DxUser info: {}", err.getMessage(), err);
        ctx.fail(err);
      });
  }

  public void getDxUserFromKeycloak(RoutingContext ctx) {
    UUID userId = RequestHelper.getPathParamAsUUID(ctx, "id");

    userService.getUserInfoByID(userId)
      .compose(userService::getUserInfo)
      .onSuccess( response -> {
        AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
          RoutingContextHelper.getRequestPath(ctx), "GET", "Get User Info by ID");
        RoutingContextHelper.setAuditingLog(ctx, auditLog);
        ResponseBuilder.sendSuccess(ctx, response);
      })
      .onFailure(err -> {
        LOGGER.error("Failed to get DxUser info: {}", err.getMessage(), err);
        ctx.fail(err);
      });
  }


  public void getAllDxUsersKeycloak(RoutingContext ctx) {

    PaginatedRequest request = PaginationRequestBuilder.from(ctx).build();
    String name = ctx.queryParam("search_term").stream().findFirst().orElse(null);

    keycloakUserService.getUsers(request.page(), request.size(), name)
      .compose(users -> {
        List<Future> futures = new ArrayList<>();
        for (DxUser user : users) {
          futures.add(userService.getUserInfo(user).map(DxUser::toJson));
        }
        return CompositeFuture.all(futures)
          .map(cf -> {
            JsonArray array = new JsonArray();
            for (int i = 0; i < cf.size(); i++) {
              array.add(cf.resultAt(i));
            }
            return array;
          });
      })
      .onSuccess(response -> {
        AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
          RoutingContextHelper.getRequestPath(ctx), "GET", "Get All DxUsers");
        RoutingContextHelper.setAuditingLog(ctx, auditLog);
        ResponseBuilder.sendSuccess(ctx, response);
      })
      .onFailure(err -> {
        LOGGER.error("Failed to get all DxUsers: {}", err.getMessage(), err);
        ctx.fail(err);
      });
  }

  public void updateDxUserInfo(RoutingContext ctx) {
    User user = ctx.user();
    Map<String, String> attributes = new HashMap<>();

    JsonObject requestBody = ctx.body().asJsonObject();

    String firstName = requestBody.getString("first_name");
    String lastName = requestBody.getString("last_name");

    if (requestBody.getString("twitter_account") != null) {
      attributes.put("twitter_account", requestBody.getString("twitter_account"));
    }
    if (requestBody.getString("linkedin_account") != null) {
      attributes.put("linkedin_account", requestBody.getString("linkedin_account"));
    }
    if (requestBody.getString("github_account") != null) {
      attributes.put("github_account", requestBody.getString("github_account"));
    }

    keycloakUserService.updateUserAttributes(UUID.fromString(user.subject()), attributes, firstName, lastName)
      .onSuccess(response -> {
        AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
          RoutingContextHelper.getRequestPath(ctx), "POST", "Update User Info");
        RoutingContextHelper.setAuditingLog(ctx, auditLog);
        ResponseBuilder.sendSuccess(ctx, "User info updated successfully");
      })
      .onFailure(err -> {
        LOGGER.error("Failed to update DxUser info: {}", err.getMessage(), err);
        ctx.fail(err);
      });
  }

  public void updatePassword(RoutingContext ctx) {
    User user = ctx.user();

    JsonObject requestBody = ctx.body().asJsonObject();
    String newPassword = requestBody.getString("new_password");

    keycloakUserService.updateUserPassword(UUID.fromString(user.subject()), newPassword)
      .onSuccess(response -> {
        AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
          RoutingContextHelper.getRequestPath(ctx), "POST", "Update User Password");
        RoutingContextHelper.setAuditingLog(ctx, auditLog);
        ResponseBuilder.sendSuccess(ctx, "User password updated successfully");
      })
      .onFailure(err -> {
        LOGGER.error("Failed to update Password info: {}", err.getMessage(), err);
        ctx.fail(err);
      });
  }

  public void updateUserStatus(RoutingContext ctx) {
    User user = ctx.user();

    JsonObject status = ctx.body().asJsonObject();
    String statusValue = status.getString("status");


    if(statusValue == null || (!statusValue.equalsIgnoreCase("activate") && !statusValue.equalsIgnoreCase("deactivate"))) {
      ctx.fail(new DxBadRequestException("Invalid status value. Must be 'activate' or 'deactivate'."));
      return;
    }

    if(statusValue.equalsIgnoreCase("deactivate")) {
      keycloakUserService.disableUser(UUID.fromString(user.subject()))
        .onSuccess(response -> {
          AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
            RoutingContextHelper.getRequestPath(ctx), "POST", "Deactivate User");
          RoutingContextHelper.setAuditingLog(ctx, auditLog);
          ResponseBuilder.sendSuccess(ctx, "User deactivated successfully");
        })
        .onFailure(err -> {
          LOGGER.error("Failed to deactivate DxUser: {}", err.getMessage(), err);
          ctx.fail(err);
        });
    }
    else {
      keycloakUserService.enableUser(UUID.fromString(user.subject()))
        .onSuccess(response -> {
          AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
            RoutingContextHelper.getRequestPath(ctx), "POST", "Activate User");
          RoutingContextHelper.setAuditingLog(ctx, auditLog);
          ResponseBuilder.sendSuccess(ctx, "User activated successfully");
        })
        .onFailure(err -> {
          LOGGER.error("Failed to activate DxUser: {}", err.getMessage(), err);
          ctx.fail(err);
        });
    }

  }

  public void deleteDxUser(RoutingContext ctx) {
    UUID userId = RequestHelper.getPathParamAsUUID(ctx, "id");

    userService.getUserInfoByID(userId).onComplete(ar -> {
      if (ar.failed() || ar.result() == null) {
        ctx.fail(new IllegalArgumentException("User not found"));
        return;
      }

      var userInfo = ar.result();
      UUID orgId = UUID.fromString(userInfo.organisationId());

      if (userInfo.roles().contains(KeycloakConstants.ADMIN_ROLE)) {
        ctx.fail(new DxBadRequestException("Cannot delete admin user"));
      } else {
        organizationService.deleteOrganizationUser(orgId, userId)
          .onFailure(err -> {
            LOGGER.error("Failed to delete organization user : {}", err.getMessage(), err);
            ctx.fail(err);
          })
          .compose(p->organizationService.deleteOrganizationJoinRequest(orgId, userId))
          .onFailure(err -> {
            LOGGER.error("Failed to delete organization join request: {}", err.getMessage(), err);
            ctx.fail(err);
          })
          .compose(q->creditService.deleteCreditRequest(userId))
          .onFailure(err -> {
            LOGGER.error("Failed to delete credit request: {}", err.getMessage(), err);
            ctx.fail(err);
          })
          .compose(r->creditService.deleteComputeRoleRequest(userId))
          .onFailure(err -> {
            LOGGER.error("Failed to delete compute request: {}", err.getMessage(), err);
            ctx.fail(err);
          })
          .compose(s -> keycloakUserService.deleteUser(userId))
          .onFailure(err -> {
            LOGGER.error("Failed to delete user in Keycloak: {}", err.getMessage(), err);
            ctx.fail(err);
          })
          .onSuccess(response -> {
            LOGGER.info("User {} deleted from organization {}", userId, orgId);
            AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
              RoutingContextHelper.getRequestPath(ctx), "DELETE", "Delete User");
            RoutingContextHelper.setAuditingLog(ctx, auditLog);
            ResponseBuilder.sendSuccess(ctx, "User deleted successfully from Keycloak and DB");
          })
          .onFailure(err -> {
            LOGGER.error("Failed to delete DxUser: {}", err.getMessage(), err);
            ctx.fail(err);
          });
      }
    });
  }

  public void updateDxUserStatusById(RoutingContext ctx) {
    UUID userId = RequestHelper.getPathParamAsUUID(ctx, "id");

    JsonObject status = ctx.body().asJsonObject();

    String statusValue = status.getString("status");


    if(statusValue == null || (!statusValue.equalsIgnoreCase("activate") && !statusValue.equalsIgnoreCase("deactivate"))) {
      ctx.fail(new DxBadRequestException("Invalid status value. Must be 'activate' or 'deactivate'."));
      return;
    }

    if(statusValue.equalsIgnoreCase("activate")) {
      keycloakUserService.enableUser(userId)
        .onSuccess(response -> {
          AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
            RoutingContextHelper.getRequestPath(ctx), "POST", "Activate User");
          RoutingContextHelper.setAuditingLog(ctx, auditLog);
          ResponseBuilder.sendSuccess(ctx, "User activated successfully");
        })
        .onFailure(err -> {
          LOGGER.error("Failed to activate DxUser: {}", err.getMessage(), err);
          ctx.fail(err);
        });
    }
    else {
      keycloakUserService.disableUser(userId)
        .onSuccess(response -> {
          AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
            RoutingContextHelper.getRequestPath(ctx), "POST", "Deactivate User");
          RoutingContextHelper.setAuditingLog(ctx, auditLog);
          ResponseBuilder.sendSuccess(ctx, "User deactivated successfully");
        })
        .onFailure(err -> {
          LOGGER.error("Failed to deactivate DxUser: {}", err.getMessage(), err);
          ctx.fail(err);
        });
    }
  }
}
