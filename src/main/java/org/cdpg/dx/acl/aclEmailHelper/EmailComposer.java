package org.cdpg.dx.acl.aclEmailHelper;

import static org.cdpg.dx.acl.accessRequest.dao.model.AssetType.AI_MODEL;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mail.MailMessage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.acl.accessRequest.dao.model.AccessRequestDto;
import org.cdpg.dx.acl.accessRequest.dao.model.AssetType;
import org.cdpg.dx.acl.accessRequest.dao.model.Status;
import org.cdpg.dx.email.service.EmailService;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
@Deprecated
public class EmailComposer {
  private static final Logger LOGGER = LogManager.getLogger(EmailComposer.class);
  private final EmailService emailService;
  private final KeycloakUserService keycloakUserService;
  private final JsonObject config;

  public EmailComposer(
    EmailService emailService, KeycloakUserService keycloakUserService, JsonObject config) {
    this.emailService = emailService;
    this.keycloakUserService = keycloakUserService;
    this.config = config;
    if (config.getString("publisherPanelUrl") == null
      || config.getString("publisherPanelUrl").endsWith("/")) {
      throw new IllegalArgumentException(
        "Publisher panel URL is not configured or ends with a slash");
    }
  }

  // TODO: call keycloak to get provider email id, first name by using provider ID
  // TODO 2 : create email body by extracting html template from resources folder

  // TODO 3: create email message object with subject, body, from, to, cc, bcc etc.
  // TODO 4: call email service to send email

  /**
   * Loads an HTML email template from the resources folder.
   *
   * @param resourcePath The path to the HTML template file in the resources folder.
   * @return The content of the HTML template as a String.
   */
  public static String loadTemplate(String resourcePath) {
    try (InputStream inputStream =
           EmailComposer.class.getClassLoader().getResourceAsStream(resourcePath);
         Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8)) {
      return scanner.useDelimiter("\\A").next();
    } catch (Exception e) {
      throw new RuntimeException("Failed to load template: " + resourcePath, e);
    }
  }

  /**
   * Sends an email to the provider with the access request details.
   *
   * @param accessRequestDto The access request details containing provider and consumer
   *     information.
   * @return A Future that completes when the email is sent or fails if there is an error.
   */
  public Future<Void> sendEmailForUpdateAccessRequest(
    AccessRequestDto accessRequestDto, Status requestStatus) {
    String senderEmail = config.getString("emailSender");
    String emailTemplate = loadTemplate("templates/AssetRequestApprovedEmailTemplate.html");
    String senderName = config.getString("senderName");
    String platformName = config.getString("platformName");
    String tgdexPanelUrl =
      getDashboardUrl(accessRequestDto.getAssetType(), accessRequestDto.getItemId());

    List<String> supportEmailIds = config.getJsonArray("emailSupport").getList();
    String statusMessage = "You now have access to the asset as per the granted permissions";

    String actionMessage = String.format(
      "You can now access the asset through the %s platform: " + tgdexPanelUrl
        +"%n%n",
      platformName
    );

    if (requestStatus.equals(Status.REJECTED)) {
      statusMessage = "Unfortunately, your request has been rejected by the provider";
      actionMessage = String.format(
        "You can reach out to the provider for more details or you can create a new request with same or different asset on %s platform: " + tgdexPanelUrl
          +"%n%n",
        platformName
      );
    }
    Map<String, String> emailDetails =
      Map.of(
        "REQUEST_STATUS", requestStatus.getStatus().toLowerCase(),
        "CONSUMER_FIRST_NAME", accessRequestDto.getConsumerFirstName(),
        "CONSUMER_LAST_NAME", accessRequestDto.getConsumerLastName(),
        "STATUS_MESSAGE", statusMessage,
        "ASSET_NAME", accessRequestDto.getAssetName(),
        "ASSET_DESCRIPTION", accessRequestDto.getShortDescription(),
        "ACTION_MESSAGE", actionMessage,
        "SENDER_NAME", senderName);
    String htmlBody = getHtmlBody(emailTemplate, emailDetails);
    MailMessage mailMessage =
      createMailMessage(
        senderEmail, accessRequestDto.getConsumerEmail(), supportEmailIds, htmlBody);
    return emailService
      .sendEmail(mailMessage)
      .onComplete(
        res -> {
          if (res.succeeded()) {
            LOGGER.info("Email sent successfully to {}", accessRequestDto.getConsumerEmail());
          } else {
            LOGGER.error("Failed to send email: {}", res.cause().getMessage());
          }
        });
  }

  private String getDashboardUrl(String assetType, String itemId) {
    String baseUrl = config.getString("publisherPanelUrl");
    String path = AssetType.fromString(assetType).equals(AI_MODEL) ? "ai-model" : "data-bank";
    return baseUrl + "/" + path + "/data-card?id=" + itemId;
  }

  /**
   * Sends an email to the provider with the access request details.
   *
   * @param accessRequestDto The access request details containing provider and consumer
   *     information.
   * @return A Future that completes when the email is sent or fails if there is an error.
   */
  public Future<Void> sendEmailForCreateAccessRequest(AccessRequestDto accessRequestDto) {
    UUID providerId = UUID.fromString(accessRequestDto.getProviderId());
    String senderEmail = config.getString("emailSender");
    String emailTemplate = loadTemplate("templates/AssetRequestEmailTemplate.html");
    String publisherPanelUrl = config.getString("publisherPanelUrl");
    String senderName = config.getString("senderName");
    List<String> supportEmailIds = config.getJsonArray("emailSupport").getList();

    return keycloakUserService
      .getUserById(providerId)
      .compose(
        providerUser -> {
          String providerFirstName = providerUser.givenName();
          String providerEmailId = providerUser.email();
          String providerLastName = providerUser.familyName();
          Map<String, String> emailDetails =
            Map.of(
              "PROVIDER_FIRST_NAME", providerFirstName,
              "PROVIDER_LAST_NAME", providerLastName,
              "CONSUMER_FIRST_NAME", accessRequestDto.getConsumerFirstName(),
              "CONSUMER_LAST_NAME", accessRequestDto.getConsumerLastName(),
              "CONSUMER_EMAIL_ID", accessRequestDto.getConsumerEmail(),
              "ASSET_NAME", accessRequestDto.getAssetName(),
              "ASSET_DESCRIPTION", accessRequestDto.getShortDescription(),
              "PUBLISHER_PANEL_URL", publisherPanelUrl,
              "SENDER_NAME", senderName);
          String htmlBody = getHtmlBody(emailTemplate, emailDetails);
          MailMessage mailMessage =
            createMailMessage(senderEmail, providerEmailId, supportEmailIds, htmlBody);
          return emailService
            .sendEmail(mailMessage)
            .onComplete(
              res -> {
                if (res.succeeded()) {
                  LOGGER.info("Email sent successfully to {}", providerEmailId);
                } else {
                  LOGGER.error("Failed to send email: {}", res.cause().getMessage());
                }
              });
        })
      .onFailure(
        failure -> {
          LOGGER.error(
            "Failed to retrieve provider user details for ID {}: {}",
            providerId,
            failure.getMessage());
        });
  }

  /**
   * Replaces placeholders in the HTML template with actual values.
   *
   * @param template The HTML template containing placeholders.
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

  /**
   * Creates a MailMessage with the specified sender, recipient, and body.
   *
   * @param senderEmail The email address of the sender.
   * @param providerEmailId The email address of the recipient (provider).
   * @param body The HTML body of the email.
   * @return A MailMessage object ready to be sent.
   */
  public MailMessage createMailMessage(
    String senderEmail, String providerEmailId, List<String> supportEmailIds, String body) {
    MailMessage message = new MailMessage();
    message.setFrom(senderEmail);
    message.setTo(providerEmailId);
    message.setCc(supportEmailIds);
    message.setSubject("Asset Access Request Notification");
    message.setHtml(body);
    return message;
  }
}
