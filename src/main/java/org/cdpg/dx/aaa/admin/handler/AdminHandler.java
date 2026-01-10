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
import org.cdpg.dx.aaa.email.util.EmailComposer;
import org.cdpg.dx.aaa.organization.models.ProviderRoleRequest;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.aaa.user.service.UserService;
import org.cdpg.dx.auditing.model.AuditLog;
import org.cdpg.dx.auth.authentication.util.AccessValidator;
import org.cdpg.dx.auth.authorization.model.DxRole;
import org.cdpg.dx.auth.authorization.model.DxScope;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.model.UserInfo;
import org.cdpg.dx.common.request.PaginatedRequest;
import org.cdpg.dx.common.request.PaginationRequestBuilder;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.common.util.PaginationInfo;
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
  private final URNGenerator urnGenerator;
  private final EmailComposer emailComposer;

  public AdminHandler(UserService userService, KeycloakUserService keycloakUserService,
                      CreditService creditService, OrganizationService organizationService,URNGenerator urnGenerator,EmailComposer emailComposer) {
    this.userService = userService;
    this.keycloakUserService = keycloakUserService;
    this.creditService = creditService;
    this.organizationService = organizationService;
    this.urnGenerator = urnGenerator;
    this.emailComposer = emailComposer;
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
        ResponseBuilder.sendSuccess(ctx, response,urnGenerator);
      })
      .onFailure(err -> {
        LOGGER.error("Failed to get DxUser info: {}", err.getMessage(), err);
        ctx.fail(err);
      });
  }

  public void getDxUserFromKeycloak(RoutingContext ctx) {
    UUID userId = RequestHelper.getPathParamAsUUID(ctx, "id");

    User user = ctx.user();
    JsonObject userJson = user.principal();

    AccessValidator.validate(
      userJson,
      List.of( // primary roles (no scope check)
        DxRole.COS_ADMIN.getRole()),
      List.of(DxScope.COS_ADMIN.getScope())
    );

    userService.getUserInfoByID(userId)
      .compose(userService::getUserInfo)
      .onSuccess(response -> {
        AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
          RoutingContextHelper.getRequestPath(ctx), "GET", "Get User Info by ID");
        RoutingContextHelper.setAuditingLog(ctx, auditLog);
        ResponseBuilder.sendSuccess(ctx, response,urnGenerator);
      })
      .onFailure(err -> {
        LOGGER.error("Failed to get DxUser info: {}", err.getMessage(), err.getCause());
        ctx.fail(err);
      });
  }


  public void getAllDxUsersKeycloak(RoutingContext ctx) {

    User user1 = ctx.user();
    JsonObject userJson = user1.principal();

    AccessValidator.validate(
      userJson,
      List.of( // primary roles (no scope check)
        DxRole.COS_ADMIN.getRole()),
      List.of(DxScope.COS_ADMIN.getScope())
    );

    PaginatedRequest request = PaginationRequestBuilder.from(ctx).build();
    String name = ctx.queryParam("search_term").stream().findFirst().orElse(null);

    keycloakUserService.getTotalCount(name).compose(totalCount -> keycloakUserService.getUsers(request.page(), request.size(), name)
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

            int totalPages = (int) Math.ceil((double) totalCount / request.size());
            boolean hasNext = request.page() < totalPages;
            boolean hasPrevious = request.page() > 1;

            PaginationInfo paginationInfo = new PaginationInfo(request.page(),request.size(),totalCount,totalPages,hasNext,hasPrevious);

            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("result", array);
            resultMap.put("paginationInfo", paginationInfo);
            return resultMap;
          });
      })).onSuccess(response -> {
      AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
        RoutingContextHelper.getRequestPath(ctx), "GET", "Get DxUser Info");
      RoutingContextHelper.setAuditingLog(ctx, auditLog);
      ResponseBuilder.sendSuccess(ctx, response.get("result"), (PaginationInfo) response.get("paginationInfo"),urnGenerator);
    }).onFailure(ctx::fail);
  }

  public void getAllUsersInfoKeycloak(RoutingContext ctx) {

    PaginatedRequest request = PaginationRequestBuilder.from(ctx).build();
    String name = ctx.queryParam("search_term").stream().findFirst().orElse(null);

    keycloakUserService.getTotalCount(name)
      .compose(totalCount ->
        keycloakUserService
          .getUsersInfo(request.page(), request.size(), name) // returns List<UserInfo>
          .map(users -> {

            JsonArray array = new JsonArray();
            for (UserInfo user : users) {
              array.add(user.toJson());
            }

            int totalPages = (int) Math.ceil((double) totalCount / request.size());
            boolean hasNext = request.page() < totalPages;
            boolean hasPrevious = request.page() > 1;

            PaginationInfo paginationInfo = new PaginationInfo(
              request.page(),
              request.size(),
              totalCount,
              totalPages,
              hasNext,
              hasPrevious
            );

            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("result", array);
            resultMap.put("paginationInfo", paginationInfo);

            return resultMap;
          })
      )
      .onSuccess(response -> {

        AuditLog auditLog = AuditingHelper.createAuditLog(
          ctx.user(),
          RoutingContextHelper.getRequestPath(ctx),
          "GET",
          "Get User Info"
        );

        RoutingContextHelper.setAuditingLog(ctx, auditLog);

        ResponseBuilder.sendSuccess(
          ctx,
          response.get("result"),
          (PaginationInfo) response.get("paginationInfo"),
          urnGenerator
        );
      })
      .onFailure(ctx::fail);
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
        ResponseBuilder.sendSuccess(ctx, "User info updated successfully",urnGenerator);
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
        ResponseBuilder.sendSuccess(ctx, "User password updated successfully",urnGenerator);
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
    UUID userId = UUID.fromString(user.subject());



    if (statusValue == null || (!statusValue.equalsIgnoreCase("activate") && !statusValue.equalsIgnoreCase("deactivate"))) {
      ctx.fail(new DxBadRequestException("Invalid status value. Must be 'activate' or 'deactivate'."));
      return;
    }

    if (statusValue.equalsIgnoreCase("deactivate")) {
      keycloakUserService.disableUser(userId)
        .onSuccess(response -> {
          LOGGER.info("User {} deactivated successfully in Keycloak", user.subject());
          AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
            RoutingContextHelper.getRequestPath(ctx), "POST", "Deactivate User");
          RoutingContextHelper.setAuditingLog(ctx, auditLog);
          ResponseBuilder.sendSuccess(ctx, "User deactivated successfully",urnGenerator);
          emailComposer.sendEmailForUpdatingUserStatus(user,statusValue);
        })
        .onFailure(err -> {
          LOGGER.error("Failed to deactivate DxUser: {}", err.getMessage(), err.getCause());
          ctx.fail(err);
        });
    } else {
      keycloakUserService.enableUser(userId)
        .onSuccess(response -> {
          LOGGER.info("User {} activated successfully in Keycloak", user.subject());
          AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
            RoutingContextHelper.getRequestPath(ctx), "POST", "Activate User");
          RoutingContextHelper.setAuditingLog(ctx, auditLog);
          ResponseBuilder.sendSuccess(ctx, "User activated successfully",urnGenerator);
          emailComposer.sendEmailForUpdatingUserStatus(user,statusValue);
        })
        .onFailure(err -> {
          LOGGER.error("Failed to activate DxUser: {}", err.getMessage(), err.getCause());
          ctx.fail(err);
        });
    }

  }
  public void deleteDxUser(RoutingContext ctx) {
    User user = ctx.user();
    UUID userId = UUID.fromString(user.subject());

    userService.getUserInfoByID(userId).compose(userInfo -> {
        if (userInfo == null) {
          return Future.failedFuture(new IllegalArgumentException("User not found"));
        }

        if (userInfo.roles().contains(KeycloakConstants.ORG_ADMIN_ROLE)) {
          return Future.failedFuture(new DxBadRequestException("Cannot delete org admin user"));
        }

        if (userInfo.roles().contains(KeycloakConstants.PF_ADMIN_ROLE)) {
          return Future.failedFuture(new DxBadRequestException("Cannot delete cos admin user"));
        }

        LOGGER.info("Organization ID is : {}", userInfo.organisationId());

        if (userInfo.organisationId() != null && !userInfo.organisationId().isEmpty()) {
          UUID orgId = UUID.fromString(userInfo.organisationId());

          return organizationService.getUserOrgAdminId(orgId).compose(orgAdminId -> {
            if (orgAdminId == null) {
              return Future.failedFuture(new IllegalArgumentException("Organization admin not found for organization: " + orgId));
            }

            if (orgAdminId.equals(userId)) {
              return Future.failedFuture(new DxBadRequestException("Cannot delete organization admin user"));
            }

            Future<Boolean> chain = Future.succeededFuture();

            if (userInfo.roles().contains(KeycloakConstants.PROVIDER_ROLE)) {
              LOGGER.info("Deleting provider user with ID: {}", userId);
              chain = chain.compose(v -> organizationService.deleteProviderUser(userId, orgAdminId, orgId)
                .mapEmpty());
            } else {
              chain = chain.compose(v -> organizationService.deleteOrganizationUser(orgId, userId)
                .mapEmpty());
            }

            chain = chain
              .compose(v -> organizationService.deleteOrganizationJoinRequest(orgId, userId)
                .recover(err -> {
                  LOGGER.warn("Failed to delete join request for user {}: {}", userId, err.getMessage());
                  return Future.succeededFuture();
                })
              )
              .compose(v -> organizationService.deleteProviderRoleRequest(orgId, userId)
                .recover(err -> {
                  LOGGER.warn("Failed to delete provider role request for user {}: {}", userId, err.getMessage());
                  return Future.succeededFuture();
                }));
            return chain;
          });
        }

        return keycloakUserService.deleteUser(userId)
          .onSuccess(r -> LOGGER.info("User {} deleted from Keycloak", userId))
          .mapEmpty()
          .compose(v -> creditService.deleteCreditRequest(userId)
            .recover(err -> {
              LOGGER.warn("Failed to delete credit request for user {}: {}", userId, err.getMessage());
              return Future.succeededFuture();
            })
          )
          .compose(v -> creditService.deleteComputeRoleRequest(userId)
            .recover(err -> {
              LOGGER.warn("Failed to delete compute request for user {}: {}", userId, err.getMessage());
              return Future.succeededFuture();
            }));
      })
      .onSuccess(v -> {
        AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
          RoutingContextHelper.getRequestPath(ctx), "DELETE", "Delete User");
        RoutingContextHelper.setAuditingLog(ctx, auditLog);
        ResponseBuilder.sendSuccess(ctx, "User deleted successfully from Keycloak and DB",urnGenerator);
      })
      .onFailure(err -> {
        LOGGER.error("Failed to delete user: {}", err.getMessage(), err);
        ctx.fail(err);
      });
  }



  public void updateDxUserStatusById(RoutingContext ctx) {
    UUID userId = RequestHelper.getPathParamAsUUID(ctx, "id");

    User dxUser = ctx.user();
    JsonObject userJson = dxUser.principal();

    AccessValidator.validate(
      userJson,
      List.of( // primary roles (no scope check)
        DxRole.COS_ADMIN.getRole()),
      List.of(DxScope.COS_ADMIN.getScope())
    );

    JsonObject status = ctx.body().asJsonObject();

    String statusValue = status.getString("status");


    if (statusValue == null || (!statusValue.equalsIgnoreCase("activate") && !statusValue.equalsIgnoreCase("deactivate"))) {
      ctx.fail(new DxBadRequestException("Invalid status value. Must be 'activate' or 'deactivate'."));
      return;
    }

    if (statusValue.equalsIgnoreCase("activate")) {
      keycloakUserService.enableUser(userId)
        .onSuccess(response -> {
          LOGGER.info("User {} activated successfully by PF Admin in Keycloak", userId);
          AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
            RoutingContextHelper.getRequestPath(ctx), "POST", "Activate User");
          RoutingContextHelper.setAuditingLog(ctx, auditLog);
          ResponseBuilder.sendSuccess(ctx, "User activated successfully",urnGenerator);
          emailComposer.sendEmailForUpdatingUserStatusByAdmin(userId,statusValue);
        })
        .onFailure(err -> {
          LOGGER.error("Failed to activate DxUser: {}", err.getMessage(), err.getCause());
          ctx.fail(err);
        });
    } else {
      keycloakUserService.disableUser(userId)
        .onSuccess(response -> {
          LOGGER.info("User {} deactivated successfully by PF Admin in Keycloak", userId);
          AuditLog auditLog = AuditingHelper.createAuditLog(ctx.user(),
            RoutingContextHelper.getRequestPath(ctx), "POST", "Deactivate User");
          RoutingContextHelper.setAuditingLog(ctx, auditLog);
          ResponseBuilder.sendSuccess(ctx, "User deactivated successfully",urnGenerator);
          emailComposer.sendEmailForUpdatingUserStatusByAdmin(userId,statusValue);
        })
        .onFailure(err -> {
          LOGGER.error("Failed to deactivate DxUser: {}", err.getMessage(), err.getCause());
          ctx.fail(err);
        });
    }
  }
}
