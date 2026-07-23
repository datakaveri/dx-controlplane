package org.cdpg.dx.aaa.email.util;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;

import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.credit.models.ComputeRole;
import org.cdpg.dx.aaa.credit.models.Status;
import org.cdpg.dx.aaa.credit.service.CreditService;
import org.cdpg.dx.aaa.organization.models.OrganizationCreateRequest;
import org.cdpg.dx.aaa.organization.models.OrganizationJoinRequest;
import org.cdpg.dx.aaa.organization.models.OrganizationUser;
import org.cdpg.dx.aaa.organization.models.ProviderRoleRequest;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
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

  public Future<Void> sendEmailForJoiningOrg(
      OrganizationJoinRequest request, User user) {
    String subject =
        "Join Organization Request"
            + ((envSuffix == null || envSuffix.isBlank()) ? "" : " [" + envSuffix + "]");
    return getOrgAdminEmail(request.organizationId())
        .compose(orgAdminEmail ->
            newEmail()
                .template("templates/request-join-organization.html")
                .to(orgAdminEmail)
                .subject(subject)
                .variable("ADMIN_FIRST_NAME", "Admin")
                .variable("ADMIN_LAST_NAME", "")
                .variable("USER_FIRST_NAME", request.userName())
                .variable("USER_EMAIL_ID", user.principal().getString("email"))
                .variable("ADMIN_PORTAL_URL", adminPortalUrl)
                .variable("SENDER_NAME", senderName)
                .variable("DETAILS_MESSAGE", detailsMessage())
                .send());
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

  public Future<Void> sendEmailForProviderRole(ProviderRoleRequest request, User user) {
    String subject =
        "Provider Role Request"
            + ((envSuffix == null || envSuffix.isBlank()) ? "" : " [" + envSuffix + "]");
    return getOrgAdminEmail(request.orgId())
        .compose(orgAdminEmail ->
            newEmail()
                .template("templates/request-provider-role.html")
                .to(orgAdminEmail)
                .subject(subject)
                .variable("ADMIN_FIRST_NAME", "Admin")
                .variable("ADMIN_LAST_NAME", "")
                .variable("USER_FIRST_NAME", user.principal().getString("name"))
                .variable("USER_EMAIL_ID", user.principal().getString("email"))
                .variable("ADMIN_PORTAL_URL", adminPortalUrl)
                .variable("SENDER_NAME", senderName)
                .variable("DETAILS_MESSAGE", detailsMessage())
                .send());
  }

  public Future<Void> sendEmailForPlatformProviderRole(ProviderRoleRequest request, User user) {
    return newEmail()
        .template("templates/request-provider-role.html")
        .to(cosAdminEmailId)
        .subject("Platform Provider Role Request")
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
                String subject =
                    "Compute Role Access Request – "
                        + status.getStatus()
                        + " | "
                        + platformName
                        + " Platform"
                        + ((envSuffix == null || envSuffix.isBlank()) ? "" : " [" + envSuffix + "]");
                String adminPortalUrl = config.getString("TGDxUrl");
                String senderName = config.getString("senderName");

                String approvedMessage = "";
                if (status.equals(Status.GRANTED)) {
                  approvedMessage =
                    "To proceed, please complete the 'Credit Request Form' available within your account:<br/><br/>"
                      + "<strong>Profile &rarr; Dashboard &rarr; My Projects</strong><br/><br/>"
                      + "Once submitted, your request will be reviewed and the allocated credits will be confirmed through a separate notification email.<br/><br/>"
                      + "For guidance on navigating the platform, please refer to the User Manual:<br/>"
                      + "<a href=\"https://mahaagx.maharashtra.gov.in/user-manual\">https://mahaagx.maharashtra.gov.in/user-manual</a><br/><br/>"
                      + "For any queries related to the platform or its datasets, please reach out to us via:<br/>"
                      + "<a href=\"https://mahaagx.maharashtra.gov.in/contact-us\">https://mahaagx.maharashtra.gov.in/contact-us</a><br/><br/>"
                      + "Thank you for your interest in the " + platformName + " platform. We look forward to supporting your work on the platform.";
                } else if (status.equals(Status.REJECTED)) {
                  approvedMessage =
                    "For any queries related to the platform or its datasets, please reach out to us via:<br/>"
                      + "<a href=\"https://mahaagx.maharashtra.gov.in/contact-us\">https://mahaagx.maharashtra.gov.in/contact-us</a>";
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

  /** Create a new builder pre-configured with the sender email. */
  private EmailTemplateBuilder newEmail() {
    return new EmailTemplateBuilder(emailService, senderEmail);
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
