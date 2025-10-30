package org.cdpg.dx.aaa.email.util;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.mail.MailMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.credit.models.ComputeRole;
import org.cdpg.dx.aaa.credit.models.Status;
import org.cdpg.dx.aaa.credit.service.CreditService;
import org.cdpg.dx.aaa.organization.models.*;
import org.cdpg.dx.aaa.organization.service.OrganizationService;
import org.cdpg.dx.aaa.user.service.UserService;

import org.cdpg.dx.email.service.EmailService;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.UUID;

import static org.cdpg.dx.aaa.organization.config.Constants.*;
import static org.cdpg.dx.database.postgres.util.Constants.DEFAULT_SORTING_ORDER;

public class EmailComposer {
  private static final Logger LOGGER = LogManager.getLogger(EmailComposer.class);
  private final EmailService emailService;
  private final KeycloakUserService keycloakUserService;
  private final JsonObject config;
  private final OrganizationService  organizationService;
  private final UserService userService;
  private final CreditService creditService;

  public EmailComposer(EmailService emailService, KeycloakUserService keycloakUserService, JsonObject config, OrganizationService organizationService, UserService userService, CreditService creditService
  ) {
    this.emailService = emailService;
    this.keycloakUserService = keycloakUserService;
    this.config = config;
    this.organizationService = organizationService;
    this.userService = userService;
    this.creditService = creditService;

  }
  /**
   * Loads an HTML email template from the resources folder.
   *
   * @param resourcePath The path to the HTML template file in the resources folder.
   * @return The content of the HTML template as a String.
   */
  public static String loadTemplate(String resourcePath) {
    try (InputStream inputStream = EmailComposer.class.getClassLoader().getResourceAsStream(resourcePath);
         Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8)) {
      return scanner.useDelimiter("\\A").next();
    } catch (Exception e) {
      throw new RuntimeException("Failed to load template: " + resourcePath, e);
    }
  }

  public Future<Void> sendEmailForCreatingOrg(OrganizationCreateRequest organizationCreateRequest, User user) {

    LOGGER.info("Sending email for organization creation request: {}", organizationCreateRequest);
    String orgName = organizationCreateRequest.name();
    String orgSector = organizationCreateRequest.orgSector();
    String orgEntityType = organizationCreateRequest.entityType();
    String orgWebsite = organizationCreateRequest.websiteLink();
    String userName = organizationCreateRequest.userName();
    String emailId = user.principal().getString("email");


    String senderEmail = config.getString("emailSender"); // e.g., no-reply@domain.com
    String emailTemplate = loadTemplate("templates/request-create-organization.html"); // Path to HTML template
    String adminPortalUrl = config.getString("TGDxUrl"); // Admin portal URL
    String cosAdminEmailId = config.getString("cosAdminEmailId"); // Email of COS admin
    String senderName = config.getString("senderName");
    String platformName = config.getString("platformName");

    String detailsMessage = String.format(
        "You can review and take action on this request by logging into the %s platform.%n%n",
        platformName
      );



    Map<String, String> emailDetails = Map.of(
      "USER_FIRST_NAME", userName,
      "USER_EMAIL_ID", emailId,
      "ORGANIZATION_NAME", orgName,
      "ADMIN_FIRST_NAME", "Admin",
      "ADMIN_LAST_NAME", "",
      "ADMIN_PORTAL_URL", adminPortalUrl,
      "SENDER_NAME", senderName ,
      "DETAILS_MESSAGE" , detailsMessage
    );

    String htmlBody = getHtmlBody(emailTemplate, emailDetails);

    MailMessage mailMessage = createMailMessage(
      senderEmail,
      cosAdminEmailId,
      htmlBody,
      "Organization Creation Request"
    );

    return emailService.sendEmail(mailMessage).onComplete(res -> {
      if (res.succeeded()) {
        LOGGER.info("Organization creation request email sent to {}", cosAdminEmailId);
      } else {
        LOGGER.error("Failed to send organization creation email: {}", res.cause().getMessage());
      }
    }).recover(failure -> {
      LOGGER.error("Failed to handle email for organization creation: {}", failure.getMessage());
      return Future.failedFuture(failure);
    });
  }


  public Future<Void> sendEmailForJoiningOrg(OrganizationJoinRequest organizationJoinRequest,User user) {

    UUID orgId = organizationJoinRequest.organizationId();
    String userName = organizationJoinRequest.userName();
    String employeeId = organizationJoinRequest.empId();
    String jobTitle = organizationJoinRequest.jobTitle();
    String emailId = user.principal().getString("email");
    String senderName = config.getString("senderName");

    String senderEmail = config.getString("emailSender"); // no-org-reply
    String emailTemplate = loadTemplate("templates/request-join-organization.html");
    String adminPortalUrl = config.getString("TGDxUrl");
    String platformName = config.getString("platformName");

    String detailsMessage = String.format(
      "You can review and take action on this request by logging into the %s platform.%n%n",
      platformName
    );

    Map<String, String> emailDetails = Map.of(
      "ADMIN_FIRST_NAME", "Admin",
      "ADMIN_LAST_NAME", "",
      "USER_FIRST_NAME", userName,
      "USER_EMAIL_ID", emailId,
      "ADMIN_PORTAL_URL", adminPortalUrl,
      "SENDER_NAME", senderName,
      "DETAILS_MESSAGE" , detailsMessage
    );


    return getOrgAdminEmail(orgId).compose(orgAdminEmail -> {
      String htmlBody = getHtmlBody(emailTemplate, emailDetails);
      LOGGER.info("Org Admin Email Id is : {}", orgAdminEmail);

      MailMessage mailMessage = createMailMessage(senderEmail, orgAdminEmail, htmlBody,"Join Organization Request");
      return emailService.sendEmail(mailMessage).onComplete(res -> {
        if (res.succeeded()) {
          LOGGER.info("Email sent successfully to {}", orgAdminEmail);
        } else {
          LOGGER.error("Failed to send email: {}", res.cause().getMessage());
        }
      }).recover(failure -> {
        LOGGER.error("Failed to retrieve provider user details for user {}: {}", userName, failure.getMessage());
        return Future.failedFuture(failure);
      });

    });
  }

  public Future<Void> sendEmailForComputeRole(ComputeRole computeRole,User user) {


    UUID userId = computeRole.userId();
    String userName = computeRole.userName();
    String emailId = user.principal().getString("email");

    String senderEmail = config.getString("emailSender"); // no-org-reply
    String emailTemplate = loadTemplate("templates/request-compute-role.html");
    String adminPortalUrl = config.getString("TGDxUrl");
    String cosAdminEmailId = config.getString("cosAdminEmailId"); // Email of COS admin
    String senderName = config.getString("senderName");
    String platformName = config.getString("platformName");

    String detailsMessage = String.format(
      "You can review and take action on this request by logging into the %s platform.%n%n",
      platformName
    );


    Map<String, String> emailDetails = Map.of(
      "ADMIN_FIRST_NAME", "Admin",
      "ADMIN_LAST_NAME", "",
      "USER_FIRST_NAME", userName,
      "USER_EMAIL_ID", emailId,
      "ADMIN_PORTAL_URL", adminPortalUrl,
      "SENDER_NAME", senderName,
      "DETAILS_MESSAGE", detailsMessage
    );

    String htmlBody = getHtmlBody(emailTemplate, emailDetails);

    MailMessage mailMessage = createMailMessage(
      senderEmail,
      cosAdminEmailId,
      htmlBody,
      "Compute Role Request"
    );

    return emailService.sendEmail(mailMessage).onComplete(res -> {
      if (res.succeeded()) {
        LOGGER.info("Compute Role request email sent to {}", cosAdminEmailId);
      } else {
        LOGGER.error("Failed to send compute role email: {}", res.cause().getMessage());
      }
    }).recover(failure -> {
      LOGGER.error("Failed to handle email for compute role creation: {}", failure.getMessage());
      return Future.failedFuture(failure);
    });
  }

  public Future<Void> sendEmailForProviderRole(ProviderRoleRequest providerRoleRequest, User user) {


    UUID userId = providerRoleRequest.userId();
    UUID orgId = providerRoleRequest.orgId();
    String userName = user.principal().getString("name");
    String emailId = user.principal().getString("email");

    String senderEmail = config.getString("emailSender"); // no-org-reply
    String emailTemplate = loadTemplate("templates/request-provider-role.html");
    String adminPortalUrl = config.getString("TGDxUrl");
    String senderName = config.getString("senderName");
    String platformName = config.getString("platformName");

    String detailsMessage = String.format(
      "You can review and take action on this request by logging into the %s platform.%n%n",
      platformName
    );


    Map<String, String> emailDetails = Map.of(
      "ADMIN_FIRST_NAME", "Admin",
      "ADMIN_LAST_NAME", "",
      "USER_FIRST_NAME", userName,
      "USER_EMAIL_ID", emailId,
      "ADMIN_PORTAL_URL", adminPortalUrl,
      "SENDER_NAME", senderName,
      "DETAILS_MESSAGE", detailsMessage
    );


    return getOrgAdminEmail(orgId).compose(orgAdminEmail -> {
      String htmlBody = getHtmlBody(emailTemplate, emailDetails);
      LOGGER.info("Org Admin Email Id is : {}", orgAdminEmail);

      MailMessage mailMessage = createMailMessage(senderEmail, orgAdminEmail, htmlBody,"Provider Role Request");
      return emailService.sendEmail(mailMessage).onComplete(res -> {
        if (res.succeeded()) {
          LOGGER.info("Email sent successfully to {}", orgAdminEmail);
        } else {
          LOGGER.error("Failed to send email: {}", res.cause().getMessage());
        }
      }).recover(failure -> {
        LOGGER.error("Failed to retrieve provider user details for user {}: {}", userName, failure.getMessage());
        return Future.failedFuture(failure);
      });

    });
  }

  //**** APPROVAL EMAILS ****//

  public Future<Void> sendUserEmailForOrgJoinRequestApproval(UUID reqId, org.cdpg.dx.aaa.organization.models.Status status) {

    return organizationService.getOrganizationJoinRequestById(reqId).compose(ar-> {

      String userName = ar.userName();
      UUID userId = ar.userId();

      return userService.getUserInfoByID(userId).compose(userInfo-> {
        String emailId = userInfo.email();
        String subject = "Organization Join Request Status Update";
        String senderEmail = config.getString("emailSender");
        String adminPortalUrl = config.getString("TGDxUrl");
        String platformName = config.getString("platformName");
        String senderName = config.getString("senderName");


        String approvedMessage="";
        if (status.equals(org.cdpg.dx.aaa.organization.models.Status.GRANTED)) {
          approvedMessage = String.format(
            "You can now access and use the %s platform as an Organization Member.%n%n",
            platformName
          );
        }


        Map<String, String> emailDetails = Map.of(
            "USER_FIRST_NAME", userName,
            "ADMIN_PORTAL_URL", adminPortalUrl,
            "SENDER_NAME", senderName,
            "STATUS", status.getStatus(),
            "APPROVED_MESSAGE", approvedMessage,
            "SUBJECT", subject);


        String emailTemplate = loadTemplate("templates/approved-join-organization.html"); // Path to HTML template
        String htmlBody = getHtmlBody(emailTemplate, emailDetails);

        MailMessage mailMessage = createMailMessage(
          senderEmail,
          emailId,
          htmlBody,
          subject
        );

        return emailService.sendEmail(mailMessage).onComplete(res -> {
          if (res.succeeded()) {
            LOGGER.info("Approved email sent to {}", emailId);
          } else {
            LOGGER.error("Failed to send approved email: {}", res.cause().getMessage());
          }
        }).recover(failure -> {
          LOGGER.error("Failed to handle email for approval: {}", failure.getMessage());
          return Future.failedFuture(failure);
        });
      });
    });

  }

  public Future<Void> sendEmailForCreditRequest(User user)
  {
    String userName = user.principal().getString("name");
    String cosAdminEmailId = config.getString("cosAdminEmailId");
    String emailId = user.principal().getString("email");

    String senderEmail = config.getString("emailSender"); // no-org-reply
    String emailTemplate = loadTemplate("templates/request-credit.html");
    String adminPortalUrl = config.getString("TGDxUrl");
    String senderName = config.getString("senderName");
    String platformName = config.getString("platformName");

    String detailsMessage = String.format(
      "You can review and take action on this request by logging into the %s platform.%n%n",
      platformName
    );


    Map<String, String> emailDetails = Map.of(
      "ADMIN_FIRST_NAME", "Admin",
      "ADMIN_LAST_NAME", "",
      "USER_FIRST_NAME", userName,
      "USER_EMAIL_ID", emailId,
      "ADMIN_PORTAL_URL", adminPortalUrl,
      "SENDER_NAME", senderName,
      "DETAILS_MESSAGE" , detailsMessage

    );

    String htmlBody = getHtmlBody(emailTemplate, emailDetails);

    MailMessage mailMessage = createMailMessage(
      senderEmail,
      cosAdminEmailId,
      htmlBody,
      "Credit Request"
    );

    return emailService.sendEmail(mailMessage).onComplete(res -> {
      if (res.succeeded()) {
        LOGGER.info("Credit request email sent to {}", emailId);
      } else {
        LOGGER.error("Failed to send credit request email: {}", res.cause().getMessage());
      }
    }).recover(failure -> {
      LOGGER.error("Failed to handle email for credit request: {}", failure.getMessage());
      return Future.failedFuture(failure);
    });
  }

  public Future<Void> sendUserEmailForComputeRoleApproval(UUID reqId,Status status)
  {

    return creditService.getComputeRequestById(reqId).compose(ar-> {

      UUID userId = ar.userId();

      if (userId == null) {
        return Future.failedFuture("User ID is null for compute role request with ID: " + reqId);
      }

      return userService.getUserInfoByID(userId).compose(userInfo -> {
        String emailId = userInfo.email();
        String userName = userInfo.name();
        String subject = "Compute Role Request Status Update";
        String senderEmail = config.getString("emailSender");
        String adminPortalUrl = config.getString("TGDxUrl");
        String senderName = config.getString("senderName");
        String platformName = config.getString("platformName");



        String approvedMessage="";
        if(status.equals(Status.GRANTED)) {
          approvedMessage = String.format(
            "You can now access the system and use your compute privileges in the the %s platform.%n%n",
            platformName
          );
        }
        Map<String, String> emailDetails = Map.of(
          "USER_FIRST_NAME", userName,
          "ADMIN_PORTAL_URL", adminPortalUrl,
          "SENDER_NAME", senderName,
          "STATUS", status.getStatus(),
          "APPROVED_MESSAGE", approvedMessage,
          "SUBJECT", subject);

        String emailTemplate = loadTemplate("templates/approved-compute-role.html"); // Path to HTML template
        String htmlBody = getHtmlBody(emailTemplate, emailDetails);

        MailMessage mailMessage = createMailMessage(
          senderEmail,
          emailId,
          htmlBody,
          subject
        );

        return emailService.sendEmail(mailMessage).onComplete(res -> {
          if (res.succeeded()) {
            LOGGER.info("Approved email sent to {}", emailId);
          } else {
            LOGGER.error("Failed to send approved email: {}", res.cause().getMessage());
          }
        }).recover(failure -> {
          LOGGER.error("Failed to handle email for approval: {}", failure.getMessage());
          return Future.failedFuture(failure);
        });
      });

    });

  }

  public Future<Void> sendUserEmailForCreditApproval(UUID reqId, Status status)
  {

    return creditService.getCreditRequestById(reqId).compose(ar-> {

      UUID userId = ar.userId();

      if (userId == null) {
        return Future.failedFuture("User ID is null for credit request with ID: " + reqId);
      }

      return userService.getUserInfoByID(userId).compose(userInfo -> {
        String emailId = userInfo.email();
        String userName = userInfo.name();
        String subject = "Credit Request Status Update";
        String senderEmail = config.getString("emailSender");
        String adminPortalUrl = config.getString("TGDxUrl");
        String platformName = config.getString("platformName");
        String senderName = config.getString("senderName");



        String approvedMessage="";
        if(status.equals(Status.GRANTED)) {
          approvedMessage = String.format(
            "You can now access the the %s platform with the credits.%n%n",
            platformName
          );
        }

        Map<String, String> emailDetails = Map.of(
          "USER_FIRST_NAME", userName,
          "ADMIN_PORTAL_URL", adminPortalUrl,
          "SENDER_NAME", senderName,
          "STATUS", status.getStatus(),
          "APPROVED_MESSAGE", approvedMessage,
          "SUBJECT", subject);

        String emailTemplate = loadTemplate("templates/approved-credit-request.html"); // Path to HTML template
        String htmlBody = getHtmlBody(emailTemplate, emailDetails);

        MailMessage mailMessage = createMailMessage(
          senderEmail,
          emailId,
          htmlBody,
          subject
        );

        return emailService.sendEmail(mailMessage).onComplete(res -> {
          if (res.succeeded()) {
            LOGGER.info("Approved email sent to {}", emailId);
          } else {
            LOGGER.error("Failed to send approved email: {}", res.cause().getMessage());
          }
        }).recover(failure -> {
          LOGGER.error("Failed to handle email for approval: {}", failure.getMessage());
          return Future.failedFuture(failure);
        });
      });

    });

  }

  public Future<Void> sendUserEmailForProviderRoleApproval(UUID reqId, org.cdpg.dx.aaa.organization.models.Status status) {

    return organizationService.getProviderRequestById(reqId).compose(ar-> {

      UUID userId = ar.userId();
      UUID orgId = ar.orgId();

      return userService.getUserInfoByID(userId).compose(userInfo-> {
        String emailId = userInfo.email();
        String userName = userInfo.name();
        String subject = "Provider Role Request Status Update";
        String senderEmail = config.getString("emailSender");
        String adminPortalUrl = config.getString("TGDxUrl");
        String platformName = config.getString("platformName");
        String senderName = config.getString("senderName");

        String approvedMessage="";
        if(status.equals(org.cdpg.dx.aaa.organization.models.Status.GRANTED)) {
          approvedMessage = String.format(
            "You can now access the the %s platform  as a Provider.%n%n",
            platformName
          );
        }


        Map<String, String> emailDetails = Map.of(
          "USER_FIRST_NAME", userName,
          "ADMIN_PORTAL_URL", adminPortalUrl,
          "SENDER_NAME", senderName,
          "STATUS", status.getStatus(),
          "APPROVED_MESSAGE", approvedMessage,
          "SUBJECT", subject);


        String emailTemplate = loadTemplate("templates/approved-pending-role.html"); // Path to HTML template
        String htmlBody = getHtmlBody(emailTemplate, emailDetails);

        MailMessage mailMessage = createMailMessage(
          senderEmail,
          emailId,
          htmlBody,
          subject
        );

        return emailService.sendEmail(mailMessage).onComplete(res -> {
          if (res.succeeded()) {
            LOGGER.info("Approved email sent to {}", emailId);
          } else {
            LOGGER.error("Failed to send approved email: {}", res.cause().getMessage());
          }
        }).recover(failure -> {
          LOGGER.error("Failed to handle email for approval: {}", failure.getMessage());
          return Future.failedFuture(failure);
        });
      });
    });

  }

  public Future<Void> sendUserEmailForOrgCreateRequestApproval(UUID reqId, org.cdpg.dx.aaa.organization.models.Status status) {

    System.out.println("Inside sendUserEmailForOrgCreateRequestApproval method");

    return organizationService.getOrganizationCreateRequestById(reqId).compose(ar-> {

      UUID requestedBy = ar.requestedBy();
      String userName = ar.userName();
      String orgName = ar.name();

      return userService.getUserInfoByID(requestedBy).compose(userInfo-> {
        String emailId = userInfo.email();
        String subject = "Organization Creation Status Update";
        String senderEmail = config.getString("emailSender");
        String adminPortalUrl = config.getString("TGDxUrl"); // Admin portal URL
        String platformName = config.getString("platformName");
        String senderName = config.getString("senderName");

        String approvedMessage="";
        if(status.equals(org.cdpg.dx.aaa.organization.models.Status.GRANTED)) {
          approvedMessage = String.format(
            "You can now manage your organisation and users in the %s platform  as a Provider.%n%n",
            platformName
          );
        }

        Map<String, String> emailDetails = Map.of(
          "USER_FIRST_NAME", userName,
          "ORGANIZATION_NAME", orgName,
          "ADMIN_PORTAL_URL", adminPortalUrl,
          "SENDER_NAME", senderName,
          "STATUS", status.getStatus(),
          "APPROVED_MESSAGE", approvedMessage,
          "SUBJECT", subject);

        String emailTemplate = loadTemplate("templates/approved-create-organization.html"); // Path to HTML template
        String htmlBody = getHtmlBody(emailTemplate, emailDetails);

        MailMessage mailMessage = createMailMessage(
          senderEmail,
          emailId,
          htmlBody,
          subject
        );

        return emailService.sendEmail(mailMessage).onComplete(res -> {
          if (res.succeeded()) {
            LOGGER.info("Approved email sent to {}", emailId);
          } else {
            LOGGER.error("Failed to send approved email for org create request: {}", res.cause().getMessage());
          }
        }).recover(failure -> {
          LOGGER.error("Failed to handle email for approval of org create request: {}", failure.getMessage());
          return Future.failedFuture(failure);
        });
      });
    });

  }






  /**
   * Replaces placeholders in the HTML template with actual values.
   *
   * @param template     The HTML template containing placeholders.
   * @param replacements A map of placeholder names to their replacement values.
   * @return The HTML body with placeholders replaced by actual values.
   */
  public String getHtmlBody(String template, Map<String, String> replacements) {
    String result = template;
    for (Map.Entry<String, String> entry : replacements.entrySet()) {
      result = result.replace("${" + entry.getKey() + "}", entry.getValue());
    }
    return result;
  }

  public MailMessage createMailMessage(String senderEmail, String receiverEmail, String body,String subject) {
    MailMessage message = new MailMessage();
    message.setFrom(senderEmail);
    message.setTo(receiverEmail);
    message.setSubject(subject);
    message.setHtml(body);
    return message;
  }

  public Future<String> getOrgAdminEmail(UUID orgId) {
    if (orgId == null) {
      return Future.failedFuture("Organization ID cannot be null");
    }

    return organizationService.getOrganisationAdminId(orgId)
      .compose(res -> {
        if (res == null || res.size() == 0) {
          return Future.failedFuture("No admin found for organization: " + orgId);
        }
        OrganizationUser organizationUser = res.get(0);
        UUID userId = organizationUser.userId();

        if (userId == null) {
          return Future.failedFuture("Invalid user ID for organization admin");
        }

        return userService.getUserInfoByID(userId)
          .compose(user -> {
            if (user == null || user.email() == null || user.email().trim().isEmpty()) {
              return Future.failedFuture("No valid email found for admin user");
            }
            return Future.succeededFuture(user.email());
          });
      })
      .recover(throwable -> {
        LOGGER.error("Failed to get organization admin email for orgId: {}", orgId, throwable);
        return Future.failedFuture("Failed to retrieve organization admin email: " + throwable.getMessage());
      });
  }

  public Future<Void> sendEmailForUpdatingUserStatus(User user,String statusValue) {
    LOGGER.info("Inside email notification for user status update");
    String userName = user.principal().getString("name");

    String userEmailId = user.principal().getString("email");

    String senderEmail = config.getString("emailSender");
    String emailTemplate = loadTemplate("templates/approved-user-status.html");
    String adminPortalUrl = config.getString("TGDxUrl");
    String cosAdminEmailId = config.getString("cosAdminEmailId");
    String senderName = config.getString("senderName");


    if(statusValue.equalsIgnoreCase("activate"))
      statusValue="activated";
    else if(statusValue.equalsIgnoreCase("deactivate"))
      statusValue="deactivated";


    Map<String, String> emailDetails = Map.of(
      "USER_FIRST_NAME", userName,
      "STATUS", statusValue,
      "USER_EMAIL_ID", userEmailId,
      "ADMIN_PORTAL_URL", adminPortalUrl,
      "SENDER_NAME", senderName
    );

    String htmlBody = getHtmlBody(emailTemplate, emailDetails);

    MailMessage mailMessage = createMailMessage(
      senderEmail,
      userEmailId,
      htmlBody,
      "Your account has been " + statusValue
    );

    return emailService.sendEmail(mailMessage).onComplete(res -> {
      if (res.succeeded()) {
        LOGGER.info("Account Update Status email sent to {}", userEmailId);
      } else {
        LOGGER.error("Failed to send account status update email: {}", res.cause().getMessage());
      }
    }).recover(failure -> {
      LOGGER.error("Failed to handle email for account status update: {}", failure.getMessage());
      return Future.failedFuture(failure);
    });
  }


  public Future<Void> sendEmailForUpdatingUserStatusByAdmin(UUID userId, String statusValue) {


    return userService.getUserInfoByID(userId).compose(userInfo -> {
      String newstatusValue="";
      String userEmailId = userInfo.email();
      String userName = userInfo.name();

      String senderEmail = config.getString("emailSender"); // no-org-reply
      String emailTemplate_user = loadTemplate("templates/approved-user-status.html");
      String emailTemplate_admin = loadTemplate("templates/approved-user-status-by-admin.html");

      String adminPortalUrl = config.getString("TGDxUrl");
      String cosAdminEmailId = config.getString("cosAdminEmailId");
      String senderName = config.getString("senderName"); // no-org-reply


      if (statusValue.equalsIgnoreCase("activate"))
        newstatusValue = "activated";
      else if (statusValue.equalsIgnoreCase("deactivate")) newstatusValue = "deactivated";

      Map<String, String> emailDetails_user = Map.of(
        "USER_FIRST_NAME", userName,
        "STATUS", newstatusValue,
        "USER_EMAIL_ID", userEmailId,
        "ADMIN_PORTAL_URL", adminPortalUrl,
        "SENDER_NAME", senderName
      );

      String htmlBody_user = getHtmlBody(emailTemplate_user, emailDetails_user);



      MailMessage userMail = createMailMessage(
        senderEmail,
        userEmailId,
        htmlBody_user,
        "Your account has been " + newstatusValue
      );


      Map<String, String> emailDetails_admin = Map.of(
        "USER_FIRST_NAME", "admin",
        "STATUS", newstatusValue,
        "USER_EMAIL_ID", userEmailId,
        "ADMIN_PORTAL_URL", adminPortalUrl,
        "SENDER_NAME", senderName
      );

      String htmlBody_admin = getHtmlBody(emailTemplate_admin, emailDetails_admin);


      MailMessage adminMail = createMailMessage(
        senderEmail,
        cosAdminEmailId,
        htmlBody_admin,
        "User account " + userEmailId + " has been " + newstatusValue
      );

      return emailService.sendEmail(userMail).compose(v ->
          emailService.sendEmail(adminMail)
        ).onSuccess(v -> LOGGER.info("Account status update emails sent to user {} and admin {}", userEmailId, cosAdminEmailId))
        .onFailure(err -> LOGGER.error("Failed to send account status update emails: {}", err.getMessage()));
    });
  }




}
