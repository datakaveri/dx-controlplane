package org.cdpg.dx.aaa.email.util;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.credit.models.ComputeRole;
import org.cdpg.dx.aaa.credit.models.Status;
import org.cdpg.dx.aaa.credit.service.CreditService;
import org.cdpg.dx.aaa.organization.models.OrganizationCreateRequest;
import org.cdpg.dx.aaa.organization.models.OrganizationHierarchyDefinitionRequest;
import org.cdpg.dx.aaa.organization.models.OrganizationJoinRequest;
import org.cdpg.dx.aaa.organization.models.OrganizationUser;
import org.cdpg.dx.aaa.organization.models.ProposedDepartmentNode;
import org.cdpg.dx.aaa.organization.models.ProviderRoleRequest;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.aaa.organization.util.DepartmentPaths;
import org.cdpg.dx.aaa.user.service.UserService;
import org.cdpg.dx.email.service.EmailService;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

/**
 * Composes and sends domain-specific notification emails using {@link EmailTemplateBuilder}.
 *
 * <p>Each method gathers domain-specific data (looking up users, orgs, etc.) and delegates the
 * actual template loading, variable substitution, and sending to {@link EmailTemplateBuilder}.
 */
public class EmailComposer {
  private static final Logger LOGGER = LogManager.getLogger(EmailComposer.class);

  private final EmailService emailService;
  private final KeycloakUserService keycloakUserService;
  private final JsonObject config;
  private final OrganizationService organizationService;
  private final UserService userService;
  private final CreditService creditService;

  // Config keys cached at construction time
  private final String senderEmail;
  private final String cosAdminEmailId;
  private final String adminPortalUrl;
  private final String senderName;
  private final String platformName;
  private final String envSuffix;

  public EmailComposer(
      EmailService emailService,
      KeycloakUserService keycloakUserService,
      JsonObject config,
      OrganizationService organizationService,
      UserService userService,
      CreditService creditService) {
    this.emailService = emailService;
    this.keycloakUserService = keycloakUserService;
    this.config = config;
    this.organizationService = organizationService;
    this.userService = userService;
    this.creditService = creditService;

    this.senderEmail = config.getString("emailSender");
    this.cosAdminEmailId = config.getString("cosAdminEmailId");
    this.adminPortalUrl = config.getString("TGDxUrl");
    this.senderName = config.getString("senderName");
    this.platformName = config.getString("platformName");
    this.envSuffix = config.getString("envSuffix");
  }

  // ────────────────────────── REQUEST EMAILS ──────────────────────────

  public Future<Void> sendEmailForCreatingOrg(
      OrganizationCreateRequest request, User user) {
    LOGGER.info("Sending email for organization creation request: {}", request);
    String subject =
        "Organization Creation Request"
            + ((envSuffix == null || envSuffix.isBlank()) ? "" : " [" + envSuffix + "]");

    return newEmail()
        .template("templates/request-create-organization.html")
        .to(cosAdminEmailId)
        .subject(subject)
        .variable("USER_FIRST_NAME", request.userName())
        .variable("USER_EMAIL_ID", user.principal().getString("email"))
        .variable("ORGANIZATION_NAME", request.name())
        .variable("ADMIN_FIRST_NAME", "Admin")
        .variable("ADMIN_LAST_NAME", "")
        .variable("ADMIN_PORTAL_URL", adminPortalUrl)
        .variable("SENDER_NAME", senderName)
        .variable("DETAILS_MESSAGE", detailsMessage())
        .send();
  }

  /**
   * Tells cos_admin that an org_admin proposed departments (an organisation's first departments,
   * or more added later) and what they are.
   */
  public Future<Void> sendEmailForDepartmentRequest(
      OrganizationHierarchyDefinitionRequest request, User user) {
    String subject = "Department Request" + envSuffixTag();
    UUID orgId = request.organizationId();

    Future<String> orgName =
        organizationService
            .getOrganizationById(orgId)
            .map(org -> org == null ? orgId.toString() : org.orgName())
            .otherwise(orgId.toString());

    return orgName.compose(
        name ->
            departmentLabel(orgId, request.parentNodeId())
                .compose(
                    parentLabel -> {
                      Map<String, String> rows = new LinkedHashMap<>();
                      rows.put("Organisation", name);
                      rows.put(
                          "Add under",
                          request.parentNodeId() == null
                              ? "Top level of the organisation"
                              : parentLabel == null ? "An existing department" : parentLabel);
                      String details =
                          detailsHtml(rows) + departmentTreeHtml(request.departments());

                      return newEmail()
                          .template("templates/request-department-change.html")
                          .to(cosAdminEmailId)
                          .subject(subject)
                          .variable("ADMIN_FIRST_NAME", "Admin")
                          .variable("ADMIN_LAST_NAME", "")
                          .variable("USER_FIRST_NAME", escapeHtml(principalName(user)))
                          .variable(
                              "USER_EMAIL_ID",
                              escapeHtml(user.principal().getString("email", "")))
                          .variable("ORGANIZATION_NAME", escapeHtml(name))
                          .variable("REQUEST_DETAILS", details)
                          .variable("ADMIN_PORTAL_URL", adminPortalUrl)
                          .variable("SENDER_NAME", senderName)
                          .variable("DETAILS_MESSAGE", detailsMessage())
                          .send();
                    }));
  }

  /**
   * Tells everyone who can approve the join request: the org_admin, and in an organisation with
   * departments every dept_admin of the chosen department or above it (strictly above for an
   * Admin request). Includes the department path, requested role and any additionalInfo fields.
   */
  public Future<Void> sendEmailForJoiningOrg(
      OrganizationJoinRequest request, User user) {
    String subject = "Join Organization Request" + envSuffixTag();
    UUID orgId = request.organizationId();

    return departmentLabel(orgId, request.requestedNodeId())
        .compose(
            department -> {
              Map<String, String> rows = new LinkedHashMap<>();
              if (department != null) {
                rows.put("Department", department);
                rows.put("Requested role", request.requestsDeptAdmin() ? "Admin" : "User");
              }
              // Free-form details from the UI (e.g. a purpose), one row per field.
              if (request.additionalInfo() != null) {
                request
                    .additionalInfo()
                    .forEach(
                        entry -> {
                          if (entry.getValue() != null) {
                            rows.put(humanizeKey(entry.getKey()), String.valueOf(entry.getValue()));
                          }
                        });
              }
              String details = detailsHtml(rows);

              return sendToApprovers(
                  orgId,
                  request.requestedNodeId(),
                  request.requestsDeptAdmin(),
                  approverEmail ->
                      newEmail()
                          .template("templates/request-join-organization.html")
                          .to(approverEmail)
                          .subject(subject)
                          .variable("ADMIN_FIRST_NAME", "Admin")
                          .variable("ADMIN_LAST_NAME", "")
                          .variable("USER_FIRST_NAME", request.userName())
                          .variable("USER_EMAIL_ID", user.principal().getString("email"))
                          .variable("REQUEST_DETAILS", details)
                          .variable("ADMIN_PORTAL_URL", adminPortalUrl)
                          .variable("SENDER_NAME", senderName)
                          .variable("DETAILS_MESSAGE", detailsMessage()));
            });
  }

  public Future<Void> sendEmailForComputeRole(ComputeRole computeRole, User user) {
    String subject =
        "Compute Role Request"
            + ((envSuffix == null || envSuffix.isBlank()) ? "" : " [" + envSuffix + "]");
    return newEmail()
        .template("templates/request-compute-role.html")
        .to(cosAdminEmailId)
        .subject(subject)
        .variable("ADMIN_FIRST_NAME", "Admin")
        .variable("ADMIN_LAST_NAME", "")
        .variable("USER_FIRST_NAME", computeRole.userName())
        .variable("USER_EMAIL_ID", user.principal().getString("email"))
        .variable("ADMIN_PORTAL_URL", adminPortalUrl)
        .variable("SENDER_NAME", senderName)
        .variable("DETAILS_MESSAGE", detailsMessage())
        .send();
  }

  /**
   * Tells everyone who can approve the provider role request: the org_admin and, when it is
   * scoped to a department, every dept_admin of that department or above it.
   */
  public Future<Void> sendEmailForProviderRole(ProviderRoleRequest request, User user) {
    String subject = "Provider Role Request" + envSuffixTag();
    UUID orgId = request.orgId();

    return departmentLabel(orgId, request.requestedNodeId())
        .compose(
            department -> {
              String details =
                  department == null ? "" : detailsHtml(Map.of("Department", department));
              return sendToApprovers(
                  orgId,
                  request.requestedNodeId(),
                  false,
                  approverEmail ->
                      newEmail()
                          .template("templates/request-provider-role.html")
                          .to(approverEmail)
                          .subject(subject)
                          .variable("ADMIN_FIRST_NAME", "Admin")
                          .variable("ADMIN_LAST_NAME", "")
                          .variable("USER_FIRST_NAME", user.principal().getString("name"))
                          .variable("USER_EMAIL_ID", user.principal().getString("email"))
                          .variable("REQUEST_DETAILS", details)
                          .variable("ADMIN_PORTAL_URL", adminPortalUrl)
                          .variable("SENDER_NAME", senderName)
                          .variable("DETAILS_MESSAGE", detailsMessage()));
            });
  }

  public Future<Void> sendEmailForPlatformProviderRole(ProviderRoleRequest request, User user) {
    return newEmail()
        .template("templates/request-provider-role.html")
        .to(cosAdminEmailId)
        .subject("Platform Provider Role Request")
        .variable("REQUEST_DETAILS", "")
        .variable("ADMIN_FIRST_NAME", "Admin")
        .variable("ADMIN_LAST_NAME", "")
        .variable("USER_FIRST_NAME", user.principal().getString("name"))
        .variable("USER_EMAIL_ID", user.principal().getString("email"))
        .variable("ADMIN_PORTAL_URL", adminPortalUrl)
        .variable("SENDER_NAME", senderName)
        .variable("DETAILS_MESSAGE", detailsMessage())
        .send();
  }

  public Future<Void> sendEmailForCreditRequest(User user) {
    String subject =
        "Credit Request"
            + ((envSuffix == null || envSuffix.isBlank()) ? "" : " [" + envSuffix + "]");
    return newEmail()
        .template("templates/request-credit.html")
        .to(cosAdminEmailId)
        .subject(subject)
        .variable("ADMIN_FIRST_NAME", "Admin")
        .variable("ADMIN_LAST_NAME", "")
        .variable("USER_FIRST_NAME", user.principal().getString("name"))
        .variable("USER_EMAIL_ID", user.principal().getString("email"))
        .variable("ADMIN_PORTAL_URL", adminPortalUrl)
        .variable("SENDER_NAME", senderName)
        .variable("DETAILS_MESSAGE", detailsMessage())
        .send();
  }

  /**
   * Notify the appropriate admin when a provider creates a new asset.
   *
   * <p>If the creator belongs to an organization ({@code orgId} present) the org admin is notified;
   * otherwise (a platform provider) the COS/platform admin is notified.
   */
  public Future<Void> sendEmailForItemCreation(User user, String orgId, String itemName) {
    if (orgId != null && !orgId.isBlank()) {
      return getOrgAdminEmail(UUID.fromString(orgId))
          .compose(orgAdminEmail -> itemCreationEmail(orgAdminEmail, user, itemName).send());
    }
    return itemCreationEmail(cosAdminEmailId, user, itemName).send();
  }

  private EmailTemplateBuilder itemCreationEmail(String recipient, User user, String itemName) {
    String subject =
        "New Asset Created"
            + ((envSuffix == null || envSuffix.isBlank()) ? "" : " [" + envSuffix + "]");
    return newEmail()
        .template("templates/request-item-creation.html")
        .to(recipient)
        .subject(subject)
        .variable("ADMIN_FIRST_NAME", "Admin")
        .variable("ADMIN_LAST_NAME", "")
        .variable("USER_FIRST_NAME", user.principal().getString("name"))
        .variable("USER_EMAIL_ID", user.principal().getString("email"))
        .variable("ITEM_NAME", itemName == null ? "" : itemName)
        .variable("ADMIN_PORTAL_URL", adminPortalUrl)
        .variable("SENDER_NAME", senderName)
        .variable("DETAILS_MESSAGE", detailsMessage());
  }

    public Future<Void> sendEmailForAssetRequest(User user) {
    String subject =
        "Asset Request"
            + ((envSuffix == null || envSuffix.isBlank()) ? "" : " [" + envSuffix + "]");
        return newEmail()
                .template("templates/request-asset.html")
                .to(cosAdminEmailId)
                .subject(subject)
                .variable("ADMIN_FIRST_NAME", "Admin")
                .variable("ADMIN_LAST_NAME", "")
                .variable("USER_FIRST_NAME", user.principal().getString("name"))
                .variable("USER_EMAIL_ID", user.principal().getString("email"))
                .variable("ADMIN_PORTAL_URL", adminPortalUrl)
                .variable("SENDER_NAME", senderName)
                .variable("DETAILS_MESSAGE", detailsMessage())
                .send();
    }

  // ────────────────────────── APPROVAL EMAILS ──────────────────────────

  public Future<Void> sendUserEmailForOrgJoinRequestApproval(
      UUID reqId, org.cdpg.dx.aaa.organization.models.Status status) {

    return organizationService.getOrganizationJoinRequestById(reqId)
        .compose(joinReq -> userService.getUserInfoByID(joinReq.userId())
            .compose(userInfo -> {
              String subject =
                  "Organization Join Request Status Update"
                      + ((envSuffix == null || envSuffix.isBlank()) ? "" : " [" + envSuffix + "]");
              String approvedMsg = status.equals(
                  org.cdpg.dx.aaa.organization.models.Status.GRANTED)
                  ? String.format(
                      "You can now access and use the %s platform as an Organization Member.%n%n",
                      platformName)
                  : "";

              return newEmail()
                  .template("templates/approved-join-organization.html")
                  .to(userInfo.email())
                  .subject(subject)
                  .variable("USER_FIRST_NAME", joinReq.userName())
                  .variable("ADMIN_PORTAL_URL", adminPortalUrl)
                  .variable("SENDER_NAME", senderName)
                  .variable("STATUS", status.getStatus())
                  .variable("APPROVED_MESSAGE", approvedMsg)
                  .variable("SUBJECT", subject)
                  .send();
            }));
  }

  public Future<Void> sendUserEmailForComputeRoleApproval(UUID reqId, Status status) {

    return creditService
      .getComputeRequestById(reqId)
      .compose(
        ar -> {
          UUID userId = ar.userId();

          if (userId == null) {
            return Future.failedFuture(
              "User ID is null for compute role request with ID: " + reqId);
          }

          return userService
            .getUserInfoByID(userId)
            .compose(
              userInfo -> {

                String emailId = userInfo.email();
                String userName = userInfo.name();
                String platformName = config.getString("platformName");
                  String subStatus =
                          status.getStatus().substring(0, 1).toUpperCase()
                                  + status.getStatus().substring(1).toLowerCase();

                  String subject =
                    "Compute Role Access Request – "
                        + subStatus
                        + " | "
                        + platformName
                        + " Platform"
                        + ((envSuffix == null || envSuffix.isBlank()) ? "" : " [" + envSuffix + "]");
                String adminPortalUrl = config.getString("TGDxUrl");
                String senderName = config.getString("senderName");

                String portalBaseUrl = adminPortalUrl.replaceAll("/+$", "");
                String userManualUrl = portalBaseUrl + "/user-manual";
                String contactUsUrl = portalBaseUrl + "/contact-us";

                String approvedMessage = "";
                if (status.equals(Status.GRANTED)) {
                  approvedMessage =
                    "To proceed, please complete the 'Credit Request Form' available within your account:<br/><br/>"
                      + "<strong>Profile &rarr; Dashboard &rarr; My Projects</strong><br/><br/>"
                      + "Once submitted, your request will be reviewed and the allocated credits will be confirmed through a separate notification email.<br/><br/>"
                      + "For guidance on navigating the platform, please refer to the User Manual:<br/>"
                      + "<a href=\"" + userManualUrl + "\">" + userManualUrl + "</a><br/><br/>"
                      + "For any queries related to the platform or its datasets, please reach out to us via:<br/>"
                      + "<a href=\"" + contactUsUrl + "\">" + contactUsUrl + "</a><br/><br/>"
                      + "Thank you for your interest in the " + platformName + " platform. We look forward to supporting your work on the platform.";
                } else if (status.equals(Status.REJECTED)) {
                  approvedMessage =
                    "For any queries related to the platform or its datasets, please reach out to us via:<br/>"
                      + "<a href=\"" + contactUsUrl + "\">" + contactUsUrl + "</a>";
                }

                return newEmail()
                  .template("templates/approved-compute-role.html")
                  .to(emailId)
                  .subject(subject)
                  .variable("USER_FIRST_NAME", userName)
                  .variable("ADMIN_PORTAL_URL", adminPortalUrl)
                  .variable("SENDER_NAME", senderName)
                  .variable("STATUS", status.getStatus())
                  .variable("APPROVED_MESSAGE", approvedMessage)
                  .variable("PLATFORM_NAME", platformName)
                  .variable("SUBJECT", subject)
                  .send();
              });
        });
  }

  public Future<Void> sendUserEmailForCreditApproval(UUID reqId, Status status) {
    return creditService.getCreditRequestById(reqId)
        .compose(creditReq -> {
          UUID userId = creditReq.userId();
          if (userId == null) {
            return Future.failedFuture(
                "User ID is null for credit request with ID: " + reqId);
          }
          return userService.getUserInfoByID(userId)
              .compose(userInfo -> {
                String subject =
                    "Credit Request Status Update"
                        + ((envSuffix == null || envSuffix.isBlank()) ? "" : " [" + envSuffix + "]");
                String approvedMsg = status.equals(Status.GRANTED)
                    ? String.format(
                        "You can now access the the %s platform with the credits.%n%n",
                        platformName)
                    : "";

                return newEmail()
                    .template("templates/approved-credit-request.html")
                    .to(userInfo.email())
                    .subject(subject)
                    .variable("USER_FIRST_NAME", userInfo.name())
                    .variable("ADMIN_PORTAL_URL", adminPortalUrl)
                    .variable("SENDER_NAME", senderName)
                    .variable("STATUS", status.getStatus())
                    .variable("APPROVED_MESSAGE", approvedMsg)
                    .variable("SUBJECT", subject)
                    .send();
              });
        });
  }

  public Future<Void> sendUserEmailForProviderRoleApproval(
      UUID reqId, org.cdpg.dx.aaa.organization.models.Status status) {

    return organizationService
        .getProviderRequestById(reqId)
        .compose(
            providerReq ->
                userService
                    .getUserInfoByID(providerReq.userId())
                    .compose(
                        userInfo -> {
                          boolean isPlatformProvider =
                              org.cdpg.dx.aaa.organization.config.Constants.PROVIDER_TYPE_PLATFORM
                                  .equalsIgnoreCase(providerReq.providerType());
                          String subject =
                              "Provider Role Request Status Update"
                                  + ((envSuffix == null || envSuffix.isBlank()) ? "" : " [" + envSuffix + "]");

                          String requestLine =
                              isPlatformProvider
                                  ? String.format(
                                      "Your provider role request for the Platform has been %s by the Platform Administrator.",
                                      status.getStatus())
                                  : String.format(
                                      "Your provider role request for the organization has been %s by your"
                                          + " Organization Manager.",
                                      status.getStatus());

                          String approvedMsg =
                              status.equals(org.cdpg.dx.aaa.organization.models.Status.GRANTED)
                                  ? String.format(
                                      "You can now access the %s platform as a Provider.%n%n",
                                      platformName)
                                  : "";

                          return newEmail()
                              .template("templates/approved-pending-role.html")
                              .to(userInfo.email())
                              .subject(subject)
                              .variable("USER_FIRST_NAME", userInfo.name())
                              .variable("ADMIN_PORTAL_URL", adminPortalUrl)
                              .variable("SENDER_NAME", senderName)
                              .variable("STATUS", status.getStatus())
                              .variable("REQUEST_LINE", requestLine)
                              .variable("APPROVED_MESSAGE", approvedMsg)
                              .variable("SUBJECT", subject)
                              .send();
                        }));
  }

  public Future<Void> sendUserEmailForOrgCreateRequestApproval(
      UUID reqId, org.cdpg.dx.aaa.organization.models.Status status) {

    LOGGER.info("Sending email for org create request approval, reqId: {}", reqId);

    return organizationService.getOrganizationCreateRequestById(reqId)
        .compose(createReq -> userService.getUserInfoByID(createReq.requestedBy())
            .compose(userInfo -> {
              String subject =
                  "Organization Creation Status Update"
                      + ((envSuffix == null || envSuffix.isBlank()) ? "" : " [" + envSuffix + "]");
              String approvedMsg = status.equals(
                  org.cdpg.dx.aaa.organization.models.Status.GRANTED)
                  ? String.format(
                      "You can now manage your organisation and users in the %s platform  as an Org Admin.%n%n",
                      platformName)
                  : "";

              return newEmail()
                  .template("templates/approved-create-organization.html")
                  .to(userInfo.email())
                  .subject(subject)
                  .variable("USER_FIRST_NAME", createReq.userName())
                  .variable("ORGANIZATION_NAME", createReq.name())
                  .variable("ADMIN_PORTAL_URL", adminPortalUrl)
                  .variable("SENDER_NAME", senderName)
                  .variable("STATUS", status.getStatus())
                  .variable("APPROVED_MESSAGE", approvedMsg)
                  .variable("SUBJECT", subject)
                  .send();
            }));
  }

  public Future<Void> sendUserEmailForAssetRequestApproval(
      UUID providerUserId, org.cdpg.dx.aaa.asset.models.Status status) {

    if (providerUserId == null) {
      return Future.failedFuture("User ID is null for asset request approval email");
    }

    return userService.getUserInfoByID(providerUserId)
        .compose(userInfo -> {
          String subject =
              "Asset Request Status Update"
                  + ((envSuffix == null || envSuffix.isBlank()) ? "" : " [" + envSuffix + "]");
          String approvedMsg = status.equals(org.cdpg.dx.aaa.asset.models.Status.GRANTED)
              ? String.format(
                  "You can now access the requested asset on the %s platform.%n%n",
                  platformName)
              : "";

          return newEmail()
              .template("templates/approved-asset-request.html")
              .to(userInfo.email())
              .subject(subject)
              .variable("USER_FIRST_NAME", userInfo.name())
              .variable("ADMIN_PORTAL_URL", adminPortalUrl)
              .variable("SENDER_NAME", senderName)
              .variable("STATUS", status.getStatus())
              .variable("APPROVED_MESSAGE", approvedMsg)
              .variable("SUBJECT", subject)
              .send();
        });
  }

  public Future<Void> sendEmailForItemPublishStatus(
      UUID ownerUserId, String itemName, String publishStatus) {

    if (ownerUserId == null) {
      return Future.failedFuture("Owner user ID is null for item publish status email");
    }

    return userService.getUserInfoByID(ownerUserId)
        .compose(userInfo -> {
          String resolvedStatus = publishStatus == null ? "" : publishStatus.toLowerCase();
          String subject =
              "Asset Publish Status Update"
                  + ((envSuffix == null || envSuffix.isBlank()) ? "" : " [" + envSuffix + "]");
          String approvedMsg = "approved".equalsIgnoreCase(publishStatus)
              ? String.format(
                  "Your asset is now published and available on the %s platform.%n%n",
                  platformName)
              : "";

          return newEmail()
              .template("templates/item-publish-status.html")
              .to(userInfo.email())
              .subject(subject)
              .variable("USER_FIRST_NAME", userInfo.name())
              .variable("ITEM_NAME", itemName == null ? "" : itemName)
              .variable("STATUS", resolvedStatus)
              .variable("APPROVED_MESSAGE", approvedMsg)
              .variable("ADMIN_PORTAL_URL", adminPortalUrl)
              .variable("SENDER_NAME", senderName)
              .send();
        });
  }

  // ────────────────────────── USER STATUS EMAILS ──────────────────────────

  public Future<Void> sendEmailForUpdatingUserStatus(User user, String statusValue) {
    LOGGER.info("Inside email notification for user status update");

    String userName = user.principal().getString("name");
    String userEmailId = user.principal().getString("email");
    String resolvedStatus = resolveStatusLabel(statusValue);
    String subject =
        "Your account has been "
            + resolvedStatus
            + ((envSuffix == null || envSuffix.isBlank()) ? "" : " [" + envSuffix + "]");

    return newEmail()
        .template("templates/approved-user-status.html")
        .to(userEmailId)
        .subject(subject)
        .variable("USER_FIRST_NAME", userName)
        .variable("STATUS", resolvedStatus)
        .variable("USER_EMAIL_ID", userEmailId)
        .variable("ADMIN_PORTAL_URL", adminPortalUrl)
        .variable("SENDER_NAME", senderName)
        .send();
  }

  public Future<Void> sendEmailForUpdatingUserStatusByAdmin(UUID userId, String statusValue) {
    return userService.getUserInfoByID(userId)
        .compose(userInfo -> {
          String userEmailId = userInfo.email();
          String userName = userInfo.name();
          String resolvedStatus = resolveStatusLabel(statusValue);
          String subject =
              "Your account has been "
                  + resolvedStatus
                  + ((envSuffix == null || envSuffix.isBlank()) ? "" : " [" + envSuffix + "]");

          // Send to user
          Future<Void> userEmail = newEmail()
              .template("templates/approved-user-status.html")
              .to(userEmailId)
              .subject(subject)
              .variable("USER_FIRST_NAME", userName)
              .variable("STATUS", resolvedStatus)
              .variable("USER_EMAIL_ID", userEmailId)
              .variable("ADMIN_PORTAL_URL", adminPortalUrl)
              .variable("SENDER_NAME", senderName)
              .send();

          // Then send to admin
          return userEmail.compose(v -> newEmail()
              .template("templates/approved-user-status-by-admin.html")
              .to(cosAdminEmailId)
              .subject("User account " + userEmailId + " has been " + resolvedStatus)
              .variable("USER_FIRST_NAME", "admin")
              .variable("STATUS", resolvedStatus)
              .variable("USER_EMAIL_ID", userEmailId)
              .variable("ADMIN_PORTAL_URL", adminPortalUrl)
              .variable("SENDER_NAME", senderName)
              .send());
        });
  }

  public Future<Void> sendEmailForDelegationCreated(UUID delegateId, UUID delegatorId) {

    return keycloakUserService
        .getUserById(delegateId)
        .compose(
            delegateUser ->
                keycloakUserService
                    .getUserById(delegatorId)
                    .compose(
                        delegatorUser -> {
                          String subject =
                              "Delegation Access Granted"
                                  + ((envSuffix == null || envSuffix.isBlank())
                                      ? ""
                                      : " [" + envSuffix + "]");

                          String portalBaseUrl = adminPortalUrl.replaceAll("/+$", "");
                          String contactUsUrl = portalBaseUrl + "/contact-us";

                          return newEmail()
                              .template("templates/delegation-created.html")
                              .to(delegateUser.email())
                              .subject(subject)
                              .variable("DELEGATE_FIRST_NAME", delegateUser.givenName())
                              .variable("DELEGATE_LAST_NAME", delegateUser.familyName())
                              .variable("DELEGATOR_FIRST_NAME", delegatorUser.givenName())
                              .variable("DELEGATOR_LAST_NAME", delegatorUser.familyName())
                              .variable("PLATFORM_NAME", platformName)
                              .variable("PLATFORM_SHORT_NAME", platformName)
                              .variable("ADMIN_PORTAL_URL", adminPortalUrl)
                              .variable("CONTACT_US_URL", contactUsUrl)
                              .variable("SENDER_NAME", senderName)
                              .send();
                        }));
  }

  public Future<Void> sendEmailForDelegationRejected(UUID delegatorId, UUID delegateId) {

    return keycloakUserService
        .getUserById(delegatorId)
        .compose(
            delegatorUser ->
                keycloakUserService
                    .getUserById(delegateId)
                    .compose(
                        delegateUser -> {
                          String subject =
                              "Delegation Rejected"
                                  + ((envSuffix == null || envSuffix.isBlank())
                                      ? ""
                                      : " [" + envSuffix + "]");

                          String portalBaseUrl = adminPortalUrl.replaceAll("/+$", "");
                          String contactUsUrl = portalBaseUrl + "/contact-us";

                          return newEmail()
                              .template("templates/delegation-rejected.html")
                              .to(delegatorUser.email())
                              .subject(subject)
                              .variable("DELEGATOR_FIRST_NAME", delegatorUser.givenName())
                              .variable("DELEGATOR_LAST_NAME", delegatorUser.familyName())
                              .variable("DELEGATE_FIRST_NAME", delegateUser.givenName())
                              .variable("DELEGATE_LAST_NAME", delegateUser.familyName())
                              .variable("PLATFORM_NAME", platformName)
                              .variable("PLATFORM_SHORT_NAME", platformName)
                              .variable("ADMIN_PORTAL_URL", adminPortalUrl)
                              .variable("CONTACT_US_URL", contactUsUrl)
                              .variable("SENDER_NAME", senderName)
                              .send();
                        }));
  }

  public Future<Void> sendEmailForDelegationConstraintsAppended(UUID delegateId, UUID delegatorId) {

    return keycloakUserService
        .getUserById(delegateId)
        .compose(
            delegateUser ->
                keycloakUserService
                    .getUserById(delegatorId)
                    .compose(
                        delegatorUser -> {
                          String subject =
                              "Delegation Permissions Updated"
                                  + ((envSuffix == null || envSuffix.isBlank())
                                      ? ""
                                      : " [" + envSuffix + "]");

                          String portalBaseUrl = adminPortalUrl.replaceAll("/+$", "");
                          String contactUsUrl = portalBaseUrl + "/contact-us";

                          return newEmail()
                              .template("templates/delegation-constraints-appended.html")
                              .to(delegateUser.email())
                              .subject(subject)
                              .variable("DELEGATE_FIRST_NAME", delegateUser.givenName())
                              .variable("DELEGATE_LAST_NAME", delegateUser.familyName())
                              .variable("DELEGATOR_FIRST_NAME", delegatorUser.givenName())
                              .variable("DELEGATOR_LAST_NAME", delegatorUser.familyName())
                              .variable("PLATFORM_NAME", platformName)
                              .variable("PLATFORM_SHORT_NAME", platformName)
                              .variable("ADMIN_PORTAL_URL", adminPortalUrl)
                              .variable("CONTACT_US_URL", contactUsUrl)
                              .variable("SENDER_NAME", senderName)
                              .send();
                        }));
  }

  public Future<Void> sendEmailForDelegationConstraintsRemoved(UUID delegateId, UUID delegatorId) {

    return keycloakUserService
        .getUserById(delegateId)
        .compose(
            delegateUser ->
                keycloakUserService
                    .getUserById(delegatorId)
                    .compose(
                        delegatorUser -> {
                          String subject =
                              "Delegation Permissions Updated"
                                  + ((envSuffix == null || envSuffix.isBlank())
                                      ? ""
                                      : " [" + envSuffix + "]");

                          String portalBaseUrl = adminPortalUrl.replaceAll("/+$", "");
                          String contactUsUrl = portalBaseUrl + "/contact-us";

                          return newEmail()
                              .template("templates/delegation-constraints-removed.html")
                              .to(delegateUser.email())
                              .subject(subject)
                              .variable("DELEGATE_FIRST_NAME", delegateUser.givenName())
                              .variable("DELEGATE_LAST_NAME", delegateUser.familyName())
                              .variable("DELEGATOR_FIRST_NAME", delegatorUser.givenName())
                              .variable("DELEGATOR_LAST_NAME", delegatorUser.familyName())
                              .variable("PLATFORM_NAME", platformName)
                              .variable("PLATFORM_SHORT_NAME", platformName)
                              .variable("ADMIN_PORTAL_URL", adminPortalUrl)
                              .variable("CONTACT_US_URL", contactUsUrl)
                              .variable("SENDER_NAME", senderName)
                              .send();
                        }));
  }

  public Future<Void> sendConversationMessageEmail(
      String recipientEmail, String recipientFirstName, String messageContent, String subject) {

    String finalSubject =
        subject + ((envSuffix == null || envSuffix.isBlank()) ? "" : " [" + envSuffix + "]");

    String portalBaseUrl = adminPortalUrl.replaceAll("/+$", "");
    String contactUsUrl = portalBaseUrl + "/contact-us";

    return newEmail()
        .template("templates/conversation-message.html")
        .to(recipientEmail)
        .subject(finalSubject)
        .variable("RECIPIENT_FIRST_NAME", recipientFirstName)
        .variable("MESSAGE_CONTENT", messageContent)
        .variable("PLATFORM_NAME", platformName)
        .variable("PLATFORM_SHORT_NAME", platformName)
        .variable("CONTACT_US_URL", contactUsUrl)
        .variable("ADMIN_PORTAL_URL", adminPortalUrl)
        .variable("SENDER_NAME", senderName)
        .variable("SUBJECT", finalSubject)
        .send();
  }

  public Future<Void> sendConversationAcknowledgementEmail(
      String recipientEmail, String recipientFirstName, String messageContent) {

    String subject =
        "Request Conversation Update"
            + ((envSuffix == null || envSuffix.isBlank()) ? "" : " [" + envSuffix + "]");

    String portalBaseUrl = adminPortalUrl.replaceAll("/+$", "");
    String contactUsUrl = portalBaseUrl + "/contact-us";

    return newEmail()
        .template("templates/conversation-request-received.html")
        .to(recipientEmail)
        .subject(subject)
        .variable("RECIPIENT_FIRST_NAME", recipientFirstName)
        .variable("MESSAGE_CONTENT", messageContent)
        .variable("PLATFORM_NAME", platformName)
        .variable("PLATFORM_SHORT_NAME", platformName)
        .variable("CONTACT_US_URL", contactUsUrl)
        .variable("ADMIN_PORTAL_URL", adminPortalUrl)
        .variable("SENDER_NAME", senderName)
        .variable("SUBJECT", subject)
        .send();
  }

  // ────────────────────────── HELPERS ──────────────────────────

  /** Look up the email address of the org admin for the given organization. */
  public Future<String> getOrgAdminEmail(UUID orgId) {
    if (orgId == null) {
      return Future.failedFuture("Organization ID cannot be null");
    }

    return organizationService.getOrganisationAdminId(orgId)
        .compose(res -> {
          if (res == null || res.isEmpty()) {
            return Future.failedFuture("No admin found for organization: " + orgId);
          }
          OrganizationUser organizationUser = res.get(0);
          UUID adminUserId = organizationUser.userId();
          if (adminUserId == null) {
            return Future.failedFuture("Invalid user ID for organization admin");
          }
          return userService.getUserInfoByID(adminUserId)
              .compose(user -> {
                if (user == null || user.email() == null || user.email().trim().isEmpty()) {
                  return Future.failedFuture("No valid email found for admin user");
                }
                return Future.succeededFuture(user.email());
              });
        })
        .recover(throwable -> {
          LOGGER.error("Failed to get organization admin email for orgId: {}", orgId, throwable);
          return Future.failedFuture(
              "Failed to retrieve organization admin email: " + throwable.getMessage());
        });
  }

  /**
   * Create a new builder pre-configured with the sender email and {@code ENV_SUFFIX}, which
   * every template title references.
   */
  private EmailTemplateBuilder newEmail() {
    return new EmailTemplateBuilder(emailService, senderEmail)
        .variable("ENV_SUFFIX", envSuffixTag());
  }

  /**
   * Environment tag appended to email titles, e.g. {@code " [staging]"}; empty when
   * {@code envSuffix} is not configured. Never null -- template substitution rejects nulls.
   */
  private String envSuffixTag() {
    return (envSuffix == null || envSuffix.isBlank()) ? "" : " [" + envSuffix + "]";
  }

  /**
   * Sends {@code email} (built per recipient) to every user who may approve a request for {@code
   * nodeId}. Falls back to the org_admin alone if the approvers cannot be resolved; one recipient
   * failing does not stop the others.
   */
  private Future<Void> sendToApprovers(
      UUID orgId,
      UUID nodeId,
      boolean strict,
      Function<String, EmailTemplateBuilder> email) {
    return approverEmails(orgId, nodeId, strict)
        .compose(
            recipients ->
                Future.join(recipients.stream().map(to -> email.apply(to).send()).toList())
                    .<Void>mapEmpty());
  }

  /** Email addresses of the request's approvers; the org_admin's if they cannot be resolved. */
  Future<List<String>> approverEmails(UUID orgId, UUID nodeId, boolean strict) {
    return organizationService
        .getRequestApproverUserIds(orgId, nodeId, strict)
        .compose(
            userIds ->
                Future.join(
                        userIds.stream()
                            .map(
                                id ->
                                    userService
                                        .getUserInfoByID(id)
                                        .map(u -> u == null ? null : u.email())
                                        .otherwise((String) null))
                            .toList())
                    .map(
                        all ->
                            all.<String>list().stream()
                                .filter(e -> e != null && !e.isBlank())
                                .distinct()
                                .toList()))
        .compose(
            emails ->
                emails.isEmpty()
                    ? getOrgAdminEmail(orgId).map(List::of)
                    : Future.succeededFuture(emails))
        .recover(
            err -> {
              LOGGER.warn(
                  "Could not resolve approvers for org {} department {}: {}",
                  orgId, nodeId, err.getMessage());
              return getOrgAdminEmail(orgId).map(List::of);
            });
  }

  /** The department's "A / B / C" path, or null for none (or when it cannot be resolved). */
  private Future<String> departmentLabel(UUID orgId, UUID nodeId) {
    if (nodeId == null) {
      return Future.succeededFuture(null);
    }
    return organizationService
        .getHierarchyNodesByOrganizationIds(List.of(orgId))
        .map(
            nodesByOrg ->
                new DepartmentPaths(nodesByOrg.getOrDefault(orgId, List.of())).label(nodeId))
        .otherwise((String) null);
  }

  /** "Label: value" lines, values HTML-escaped (they include user-entered text). */
  static String detailsHtml(Map<String, String> rows) {
    if (rows.isEmpty()) {
      return "";
    }
    StringBuilder html = new StringBuilder("<p>");
    rows.forEach(
        (label, value) ->
            html.append("<strong>")
                .append(escapeHtml(label))
                .append(":</strong> ")
                .append(escapeHtml(value))
                .append("<br/>"));
    return html.append("</p>").toString();
  }

  /** "purpose" / "teamSize" / "team_size" → "Purpose" / "Team size" / "Team size". */
  static String humanizeKey(String key) {
    if (key == null || key.isBlank()) {
      return "Info";
    }
    String spaced = key.replace('_', ' ').replaceAll("([a-z0-9])([A-Z])", "$1 $2").trim();
    return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1).toLowerCase();
  }

  /** The proposed departments as a nested list, names HTML-escaped. */
  static String departmentTreeHtml(List<ProposedDepartmentNode> departments) {
    if (departments == null || departments.isEmpty()) {
      return "";
    }
    StringBuilder html = new StringBuilder("<ul>");
    for (ProposedDepartmentNode department : departments) {
      html.append("<li>")
          .append(escapeHtml(department.name()))
          .append(departmentTreeHtml(department.subDepartments()))
          .append("</li>");
    }
    return html.append("</ul>").toString();
  }

  static String escapeHtml(String value) {
    if (value == null) {
      return "";
    }
    StringBuilder out = new StringBuilder(value.length());
    for (char c : value.toCharArray()) {
      switch (c) {
        case '&' -> out.append("&amp;");
        case '<' -> out.append("&lt;");
        case '>' -> out.append("&gt;");
        case '"' -> out.append("&quot;");
        case '\'' -> out.append("&#39;");
        default -> out.append(c);
      }
    }
    return out.toString();
  }

  private static String principalName(User user) {
    String name = user.principal().getString("name");
    if (name == null || name.isBlank()) {
      name = user.principal().getString("preferred_username", "An organisation admin");
    }
    return name;
  }

  /** Construct the standard "review and take action" details message. */
  private String detailsMessage() {
    return String.format(
        "You can review and take action on this request by logging into the %s platform.%n%n",
        platformName);
  }

  /** Resolve "activate"/"deactivate" to past tense form. */
  private static String resolveStatusLabel(String statusValue) {
    if ("activate".equalsIgnoreCase(statusValue)) return "activated";
    if ("deactivate".equalsIgnoreCase(statusValue)) return "deactivated";
    return statusValue;
  }
}
