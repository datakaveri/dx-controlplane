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
 * <p>All configurable concerns (template paths, subject lines, static variable values
 * such as the admin display name) are read from {@link EmailConfig}, which loads them
 * from the application config and falls back to built-in defaults.
 *
 * <p>Template variable <em>key names</em> (e.g. {@code "USER_FIRST_NAME"}) remain as
 * constants here — they are part of the template contract and rarely need to change.
 * Only the <em>values</em> that were previously hardcoded literals are configurable.
 */
public class EmailComposer {
  private static final Logger LOGGER = LogManager.getLogger(EmailComposer.class);

  // ── Template variable key names (template contract — not expected to change) ──
  private static final String KEY_USER_FIRST_NAME   = "USER_FIRST_NAME";
  private static final String KEY_USER_EMAIL_ID     = "USER_EMAIL_ID";
  private static final String KEY_ADMIN_FIRST_NAME  = "ADMIN_FIRST_NAME";
  private static final String KEY_ADMIN_LAST_NAME   = "ADMIN_LAST_NAME";
  private static final String KEY_ADMIN_PORTAL_URL  = "ADMIN_PORTAL_URL";
  private static final String KEY_SENDER_NAME       = "SENDER_NAME";
  private static final String KEY_DETAILS_MESSAGE   = "DETAILS_MESSAGE";
  private static final String KEY_ORGANIZATION_NAME = "ORGANIZATION_NAME";
  private static final String KEY_STATUS            = "STATUS";
  private static final String KEY_APPROVED_MESSAGE  = "APPROVED_MESSAGE";
  private static final String KEY_SUBJECT           = "SUBJECT";
  private static final String KEY_PLATFORM_NAME     = "PLATFORM_NAME";

  private final EmailService emailService;
  private final KeycloakUserService keycloakUserService;
  private final OrganizationService organizationService;
  private final UserService userService;
  private final CreditService creditService;

  // Scalar runtime values from config
  private final String senderEmail;
  private final String cosAdminEmailId;
  private final String adminPortalUrl;
  private final String senderName;
  private final String platformName;

  // All configurable email settings (templates, subjects, static values)
  private final EmailConfig emailConfig;

  public EmailComposer(
    EmailService emailService,
    KeycloakUserService keycloakUserService,
    JsonObject config,
    OrganizationService organizationService,
    UserService userService,
    CreditService creditService) {
    this.emailService        = emailService;
    this.keycloakUserService = keycloakUserService;
    this.organizationService = organizationService;
    this.userService         = userService;
    this.creditService       = creditService;

    this.senderEmail     = config.getString("emailSender");
    this.cosAdminEmailId = config.getString("cosAdminEmailId");
    this.adminPortalUrl  = config.getString("TGDxUrl");
    this.senderName      = config.getString("senderName");
    this.platformName    = config.getString("platformName");

    this.emailConfig = EmailConfig.fromConfig(config);
  }

  // ────────────────────────── REQUEST EMAILS ──────────────────────────

  public Future<Void> sendEmailForCreatingOrg(OrganizationCreateRequest request, User user) {
    LOGGER.info("Sending email for organization creation request: {}", request);

    return newEmail()
      .template(emailConfig.createOrgTemplate())
      .to(cosAdminEmailId)
      .subject(emailConfig.createOrgSubject())
      .variable(KEY_USER_FIRST_NAME,   request.userName())
      .variable(KEY_USER_EMAIL_ID,     user.principal().getString("email"))
      .variable(KEY_ORGANIZATION_NAME, request.name())
      .variable(KEY_ADMIN_FIRST_NAME,  emailConfig.adminFirstName())
      .variable(KEY_ADMIN_LAST_NAME,   emailConfig.adminLastName())
      .variable(KEY_ADMIN_PORTAL_URL,  adminPortalUrl)
      .variable(KEY_SENDER_NAME,       senderName)
      .variable(KEY_DETAILS_MESSAGE,   detailsMessage())
      .send();
  }

  public Future<Void> sendEmailForJoiningOrg(OrganizationJoinRequest request, User user) {
    return getOrgAdminEmail(request.organizationId())
      .compose(orgAdminEmail ->
        newEmail()
          .template(emailConfig.joinOrgTemplate())
          .to(orgAdminEmail)
          .subject(emailConfig.joinOrgSubject())
          .variable(KEY_ADMIN_FIRST_NAME, emailConfig.adminFirstName())
          .variable(KEY_ADMIN_LAST_NAME,  emailConfig.adminLastName())
          .variable(KEY_USER_FIRST_NAME,  request.userName())
          .variable(KEY_USER_EMAIL_ID,    user.principal().getString("email"))
          .variable(KEY_ADMIN_PORTAL_URL, adminPortalUrl)
          .variable(KEY_SENDER_NAME,      senderName)
          .variable(KEY_DETAILS_MESSAGE,  detailsMessage())
          .send());
  }

  public Future<Void> sendEmailForComputeRole(ComputeRole computeRole, User user) {
    return newEmail()
      .template(emailConfig.computeRoleTemplate())
      .to(cosAdminEmailId)
      .subject(emailConfig.computeRoleSubject())
      .variable(KEY_ADMIN_FIRST_NAME, emailConfig.adminFirstName())
      .variable(KEY_ADMIN_LAST_NAME,  emailConfig.adminLastName())
      .variable(KEY_USER_FIRST_NAME,  computeRole.userName())
      .variable(KEY_USER_EMAIL_ID,    user.principal().getString("email"))
      .variable(KEY_ADMIN_PORTAL_URL, adminPortalUrl)
      .variable(KEY_SENDER_NAME,      senderName)
      .variable(KEY_DETAILS_MESSAGE,  detailsMessage())
      .send();
  }

  public Future<Void> sendEmailForProviderRole(ProviderRoleRequest request, User user) {
    return getOrgAdminEmail(request.orgId())
      .compose(orgAdminEmail ->
        newEmail()
          .template(emailConfig.providerRoleTemplate())
          .to(orgAdminEmail)
          .subject(emailConfig.providerRoleSubject())
          .variable(KEY_ADMIN_FIRST_NAME, emailConfig.adminFirstName())
          .variable(KEY_ADMIN_LAST_NAME,  emailConfig.adminLastName())
          .variable(KEY_USER_FIRST_NAME,  user.principal().getString("name"))
          .variable(KEY_USER_EMAIL_ID,    user.principal().getString("email"))
          .variable(KEY_ADMIN_PORTAL_URL, adminPortalUrl)
          .variable(KEY_SENDER_NAME,      senderName)
          .variable(KEY_DETAILS_MESSAGE,  detailsMessage())
          .send());
  }

  public Future<Void> sendEmailForCreditRequest(User user) {
    return newEmail()
      .template(emailConfig.creditRequestTemplate())
      .to(cosAdminEmailId)
      .subject(emailConfig.creditRequestSubject())
      .variable(KEY_ADMIN_FIRST_NAME, emailConfig.adminFirstName())
      .variable(KEY_ADMIN_LAST_NAME,  emailConfig.adminLastName())
      .variable(KEY_USER_FIRST_NAME,  user.principal().getString("name"))
      .variable(KEY_USER_EMAIL_ID,    user.principal().getString("email"))
      .variable(KEY_ADMIN_PORTAL_URL, adminPortalUrl)
      .variable(KEY_SENDER_NAME,      senderName)
      .variable(KEY_DETAILS_MESSAGE,  detailsMessage())
      .send();
  }

  // ────────────────────────── APPROVAL EMAILS ──────────────────────────

  public Future<Void> sendUserEmailForOrgJoinRequestApproval(
    UUID reqId, org.cdpg.dx.aaa.organization.models.Status status) {

    return organizationService.getOrganizationJoinRequestById(reqId)
      .compose(joinReq -> userService.getUserInfoByID(joinReq.userId())
        .compose(userInfo -> {
          String resolvedStatus = status.getStatus();
          String subject = emailConfig.approvedJoinOrgSubject();
          String approvedMsg = status.equals(
            org.cdpg.dx.aaa.organization.models.Status.GRANTED)
            ? String.format(
            "You can now access and use the %s platform as an Organization Member.%n%n",
            platformName)
            : "";

          return newEmail()
            .template(emailConfig.approvedJoinOrgTemplate())
            .to(userInfo.email())
            .subject(subject)
            .variable(KEY_USER_FIRST_NAME,  joinReq.userName())
            .variable(KEY_ADMIN_PORTAL_URL, adminPortalUrl)
            .variable(KEY_SENDER_NAME,      senderName)
            .variable(KEY_STATUS,           resolvedStatus)
            .variable(KEY_APPROVED_MESSAGE, approvedMsg)
            .variable(KEY_SUBJECT,          subject)
            .send();
        }));
  }

  public Future<Void> sendUserEmailForComputeRoleApproval(UUID reqId, Status status) {
    return creditService.getComputeRequestById(reqId)
      .compose(ar -> {
        UUID userId = ar.userId();
        if (userId == null) {
          return Future.failedFuture(
            "User ID is null for compute role request with ID: " + reqId);
        }
        return userService.getUserInfoByID(userId)
          .compose(userInfo -> {
            String resolvedStatus = status.getStatus();
            String subject = resolveSubject(
              emailConfig.approvedComputeRoleSubject(),
              KEY_STATUS,        resolvedStatus,
              KEY_PLATFORM_NAME, platformName);

            return newEmail()
              .template(emailConfig.approvedComputeRoleTemplate())
              .to(userInfo.email())
              .subject(subject)
              .variable(KEY_USER_FIRST_NAME,  userInfo.name())
              .variable(KEY_ADMIN_PORTAL_URL, adminPortalUrl)
              .variable(KEY_SENDER_NAME,      senderName)
              .variable(KEY_STATUS,           resolvedStatus)
              .variable(KEY_APPROVED_MESSAGE, buildComputeRoleApprovedMessage(status))
              .variable(KEY_PLATFORM_NAME,    platformName)
              .variable(KEY_SUBJECT,          subject)
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
            String subject = emailConfig.approvedCreditRequestSubject();
            String approvedMsg = status.equals(Status.GRANTED)
              ? String.format(
              "You can now access the the %s platform with the credits.%n%n",
              platformName)
              : "";

            return newEmail()
              .template(emailConfig.approvedCreditRequestTemplate())
              .to(userInfo.email())
              .subject(subject)
              .variable(KEY_USER_FIRST_NAME,  userInfo.name())
              .variable(KEY_ADMIN_PORTAL_URL, adminPortalUrl)
              .variable(KEY_SENDER_NAME,      senderName)
              .variable(KEY_STATUS,           status.getStatus())
              .variable(KEY_APPROVED_MESSAGE, approvedMsg)
              .variable(KEY_SUBJECT,          subject)
              .send();
          });
      });
  }

  public Future<Void> sendUserEmailForProviderRoleApproval(
    UUID reqId, org.cdpg.dx.aaa.organization.models.Status status) {

    return organizationService.getProviderRequestById(reqId)
      .compose(providerReq -> userService.getUserInfoByID(providerReq.userId())
        .compose(userInfo -> {
          String subject = emailConfig.approvedProviderRoleSubject();
          String approvedMsg = status.equals(
            org.cdpg.dx.aaa.organization.models.Status.GRANTED)
            ? String.format(
            "You can now access the the %s platform as a Provider.%n%n",
            platformName)
            : "";

          return newEmail()
            .template(emailConfig.approvedProviderRoleTemplate())
            .to(userInfo.email())
            .subject(subject)
            .variable(KEY_USER_FIRST_NAME,  userInfo.name())
            .variable(KEY_ADMIN_PORTAL_URL, adminPortalUrl)
            .variable(KEY_SENDER_NAME,      senderName)
            .variable(KEY_STATUS,           status.getStatus())
            .variable(KEY_APPROVED_MESSAGE, approvedMsg)
            .variable(KEY_SUBJECT,          subject)
            .send();
        }));
  }

  public Future<Void> sendUserEmailForOrgCreateRequestApproval(
    UUID reqId, org.cdpg.dx.aaa.organization.models.Status status) {

    LOGGER.info("Sending email for org create request approval, reqId: {}", reqId);

    return organizationService.getOrganizationCreateRequestById(reqId)
      .compose(createReq -> userService.getUserInfoByID(createReq.requestedBy())
        .compose(userInfo -> {
          String subject = emailConfig.approvedCreateOrgSubject();
          String approvedMsg = status.equals(
            org.cdpg.dx.aaa.organization.models.Status.GRANTED)
            ? String.format(
            "You can now manage your organisation and users in the %s platform as an Org Admin.%n%n",
            platformName)
            : "";

          return newEmail()
            .template(emailConfig.approvedCreateOrgTemplate())
            .to(userInfo.email())
            .subject(subject)
            .variable(KEY_USER_FIRST_NAME,   createReq.userName())
            .variable(KEY_ORGANIZATION_NAME, createReq.name())
            .variable(KEY_ADMIN_PORTAL_URL,  adminPortalUrl)
            .variable(KEY_SENDER_NAME,       senderName)
            .variable(KEY_STATUS,            status.getStatus())
            .variable(KEY_APPROVED_MESSAGE,  approvedMsg)
            .variable(KEY_SUBJECT,           subject)
            .send();
        }));
  }

  // ────────────────────────── USER STATUS EMAILS ──────────────────────────

  public Future<Void> sendEmailForUpdatingUserStatus(User user, String statusValue) {
    LOGGER.info("Inside email notification for user status update");

    String userName       = user.principal().getString("name");
    String userEmailId    = user.principal().getString("email");
    String resolvedStatus = resolveStatusLabel(statusValue);
    String subject        = resolveSubject(
      emailConfig.userStatusSubject(), KEY_STATUS, resolvedStatus);

    return newEmail()
      .template(emailConfig.userStatusTemplate())
      .to(userEmailId)
      .subject(subject)
      .variable(KEY_USER_FIRST_NAME,  userName)
      .variable(KEY_STATUS,           resolvedStatus)
      .variable(KEY_USER_EMAIL_ID,    userEmailId)
      .variable(KEY_ADMIN_PORTAL_URL, adminPortalUrl)
      .variable(KEY_SENDER_NAME,      senderName)
      .send();
  }

  public Future<Void> sendEmailForUpdatingUserStatusByAdmin(UUID userId, String statusValue) {
    return userService.getUserInfoByID(userId)
      .compose(userInfo -> {
        String userEmailId    = userInfo.email();
        String userName       = userInfo.name();
        String resolvedStatus = resolveStatusLabel(statusValue);

        String userSubject = resolveSubject(
          emailConfig.userStatusSubject(),
          KEY_STATUS, resolvedStatus);

        String adminSubject = resolveSubject(
          emailConfig.userStatusByAdminSubject(),
          KEY_USER_EMAIL_ID, userEmailId,
          KEY_STATUS,        resolvedStatus);

        return newEmail()
          .template(emailConfig.userStatusTemplate())
          .to(userEmailId)
          .subject(userSubject)
          .variable(KEY_USER_FIRST_NAME,  userName)
          .variable(KEY_STATUS,           resolvedStatus)
          .variable(KEY_USER_EMAIL_ID,    userEmailId)
          .variable(KEY_ADMIN_PORTAL_URL, adminPortalUrl)
          .variable(KEY_SENDER_NAME,      senderName)
          .send()
          .compose(v -> newEmail()
            .template(emailConfig.userStatusByAdminTemplate())
            .to(cosAdminEmailId)
            .subject(adminSubject)
            .variable(KEY_USER_FIRST_NAME,  emailConfig.adminFirstName())
            .variable(KEY_STATUS,           resolvedStatus)
            .variable(KEY_USER_EMAIL_ID,    userEmailId)
            .variable(KEY_ADMIN_PORTAL_URL, adminPortalUrl)
            .variable(KEY_SENDER_NAME,      senderName)
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

  /** Resolve "activate"/"deactivate" to past-tense form. */
  private static String resolveStatusLabel(String statusValue) {
    if ("activate".equalsIgnoreCase(statusValue))   return "activated";
    if ("deactivate".equalsIgnoreCase(statusValue)) return "deactivated";
    return statusValue;
  }

  /**
   * Replaces {@code {TOKEN}} placeholders in a subject template string.
   *
   * <p>Tokens and values are provided as alternating key/value pairs:
   * <pre>{@code resolveSubject(template, "STATUS", "Approved", "PLATFORM_NAME", "MahaAGX")}</pre>
   *
   * @param template        subject string possibly containing {@code {TOKEN}} placeholders
   * @param tokenValuePairs alternating token name / replacement value pairs
   * @return subject with all matching tokens replaced
   * @throws IllegalArgumentException if an odd number of arguments is supplied
   */
  private static String resolveSubject(String template, String... tokenValuePairs) {
    if (tokenValuePairs.length % 2 != 0) {
      throw new IllegalArgumentException(
        "tokenValuePairs must be provided as alternating key/value pairs");
    }
    String result = template;
    for (int i = 0; i < tokenValuePairs.length; i += 2) {
      result = result.replace("{" + tokenValuePairs[i] + "}", tokenValuePairs[i + 1]);
    }
    return result;
  }

  /**
   * Build the approved-message body for compute role emails.
   * Extracted for readability and testability.
   */
  private String buildComputeRoleApprovedMessage(Status status) {
    if (status.equals(Status.GRANTED)) {
      return "To proceed, please complete the 'Credit Request Form' available within your account:<br/><br/>"
        + "<strong>Profile &rarr; Dashboard &rarr; My Projects</strong><br/><br/>"
        + "Once submitted, your request will be reviewed and the allocated credits will be confirmed through a separate notification email.<br/><br/>"
        + "For guidance on navigating the platform, please refer to the User Manual:<br/>"
        + "<a href=\"https://mahaagx.maharashtra.gov.in/user-manual\">https://mahaagx.maharashtra.gov.in/user-manual</a><br/><br/>"
        + "For any queries related to the platform or its datasets, please reach out to us via:<br/>"
        + "<a href=\"https://mahaagx.maharashtra.gov.in/contact-us\">https://mahaagx.maharashtra.gov.in/contact-us</a><br/><br/>"
        + "Thank you for your interest in the " + platformName
        + " platform. We look forward to supporting your work on the platform.";
    }
    if (status.equals(Status.REJECTED)) {
      return "For any queries related to the platform or its datasets, please reach out to us via:<br/>"
        + "<a href=\"https://mahaagx.maharashtra.gov.in/contact-us\">https://mahaagx.maharashtra.gov.in/contact-us</a>";
    }
    return "";
  }
}
