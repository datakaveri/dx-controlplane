package org.cdpg.dx.email.util;

import static org.cdpg.dx.acl.accessRequest.dao.model.AssetType.AI_MODEL;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mail.MailMessage;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.acl.accessRequest.dao.model.AssetType;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.email.model.EmailRequest;
import org.cdpg.dx.email.model.UserDetails;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

public class EmailComposer {
  private static final Logger LOGGER = LogManager.getLogger(EmailComposer.class);
  private final JsonObject config;
  private final KeycloakUserService keycloakUserService;

  public EmailComposer(JsonObject config, KeycloakUserService keycloakUserService) {
    this.config = config;
    this.keycloakUserService = keycloakUserService;
    if (config.getString("publisherPanelUrl") == null
        || config.getString("publisherPanelUrl").endsWith("/")) {
      throw new IllegalArgumentException(
          "Publisher panel URL is not configured or ends with a slash");
    }
  }

  /**
   * Sends an email to the provider with the access request details.
   *
   * @param emailRequest contains the all email related information.
   * @return A Future that completes when the email is sent or fails if there is an error.
   */
  public Future<MailMessage> sendEmailForUpdateAccessRequest(EmailRequest emailRequest) {
    Promise<MailMessage> promise = Promise.promise();
    String senderEmail = config.getString("emailSender");
    String emailTemplate = emailRequest.templateResolver().resolve();
    String senderName = config.getString("senderName");
    String platformName = config.getString("platformName");
    String iudxPanelUrl = getDashboardUrl(emailRequest.assetType(), emailRequest.itemId());

    List<String> supportEmailIds = config.getJsonArray("emailSupport").getList();
    String statusMessage;
    String actionMessage;

    if (emailRequest.status() != null && emailRequest.status().equalsIgnoreCase("REJECTED")) {
      statusMessage = "Unfortunately, your request has been rejected by the provider";
      actionMessage =
          String.format(
              "You can reach out to the provider for more details or you can create a new request with same or different asset on %s platform: "
                  + iudxPanelUrl
                  + "%n%n",
              platformName);
    } else {
      statusMessage = "You now have access to the asset as per the granted permissions";
      actionMessage =
          String.format(
              "You can now access the asset through the %s platform: " + iudxPanelUrl + "%n%n",
              platformName);
    }
    if (emailRequest.consumerUserId() != null) {
      getUserDetails(emailRequest.consumerUserId())
          .onSuccess(
              userDetails -> {
                Map<String, String> emailDetails =
                    Map.of(
                        "REQUEST_STATUS",
                        emailRequest.status().toLowerCase(),
                        "CONSUMER_FIRST_NAME",
                        userDetails.firstName(),
                        "CONSUMER_LAST_NAME",
                        userDetails.lastName(),
                        "STATUS_MESSAGE",
                        statusMessage,
                        "ASSET_NAME",
                        emailRequest.assetName(),
                        "ASSET_DESCRIPTION",
                        emailRequest.shortDescription(),
                        "ACTION_MESSAGE",
                        actionMessage,
                        "SENDER_NAME",
                        senderName);

                String htmlBody = TemplateCreator.render(emailTemplate, emailDetails);
                MailMessage mailMessage =
                    createMailMessage(
                        senderEmail, userDetails.userEmail(), supportEmailIds, htmlBody);
                promise.complete(mailMessage);
              })
          .onFailure(
              fail -> {
                LOGGER.error("Failed to fetch user details: {}", fail.getMessage());
                promise.fail(fail);
              });
    } else {
      LOGGER.error("Consumer user ID is null or not found Cannot fetch user details.");
      promise.fail(new DxBadRequestException("Consumer user ID is null."));
    }
    return promise.future();
  }

  private String getDashboardUrl(String assetType, String itemId) {
    String baseUrl = config.getString("publisherPanelUrl");

    AssetType type = AssetType.fromString(assetType);

    if (type.equals(AI_MODEL)) {
      return baseUrl + "/model/" + itemId;
    }

    // Default → dataset
    return baseUrl + "/dataset/" + itemId + "?tab=dataset";
  }

  public Future<UserDetails> getUserDetails(String userId) {
    Promise<UserDetails> promise = Promise.promise();
    keycloakUserService
        .getUserById(UUID.fromString(userId))
        .onSuccess(
            res -> {
              LOGGER.info("Fetched user details for userId: {}", userId);
              promise.complete(new UserDetails(res.email(), res.givenName(), res.familyName()));
            })
        .onFailure(
            err -> {
              LOGGER.error(
                  "Failed to fetch user details for userId: {}. Error: {}",
                  userId,
                  err.getMessage());
              promise.fail(err);
            });
    return promise.future();
  }

  /**
   * Sends an email to the provider with the access request details.
   *
   * @param emailRequest The access request details containing provider and consumer information.
   * @return A Future that completes when the email is sent or fails if there is an error.
   */
  public Future<MailMessage> sendEmailForCreateAccessRequest(EmailRequest emailRequest) {
    Promise<MailMessage> promise = Promise.promise();
    String senderEmail = config.getString("emailSender");
    String emailTemplate = emailRequest.templateResolver().resolve();
    String publisherPanelUrl = config.getString("publisherPanelUrl");
    String senderName = config.getString("senderName");
    List<String> supportEmailIds = config.getJsonArray("emailSupport").getList();
    if (emailRequest.consumerUserId() != null && emailRequest.providerUserId() != null) {
      Future<UserDetails> consumerDetails = getUserDetails(emailRequest.consumerUserId());
      Future<UserDetails> providerDetails = getUserDetails(emailRequest.providerUserId());

      Future.all(consumerDetails, providerDetails)
          .onSuccess(
              result -> {
                UserDetails consumer = result.resultAt(0);
                UserDetails provider = result.resultAt(1);
                Map<String, String> emailDetails =
                    Map.of(
                        "PROVIDER_FIRST_NAME", provider.firstName(),
                        "PROVIDER_LAST_NAME", provider.lastName(),
                        "CONSUMER_FIRST_NAME", consumer.firstName(),
                        "CONSUMER_LAST_NAME", consumer.lastName(),
                        "CONSUMER_EMAIL_ID", consumer.userEmail(),
                        "ASSET_NAME", emailRequest.assetName(),
                        "ASSET_DESCRIPTION", emailRequest.shortDescription(),
                        "PUBLISHER_PANEL_URL", publisherPanelUrl,
                        "SENDER_NAME", senderName);
                String htmlBody = TemplateCreator.render(emailTemplate, emailDetails);
                MailMessage mailMessage =
                    createMailMessage(senderEmail, provider.userEmail(), supportEmailIds, htmlBody);
                promise.complete(mailMessage);
              })
          .onFailure(
              fail -> {
                LOGGER.error("Failed to fetch users details: {}", fail.getMessage());
                promise.fail(fail);
              });
    } else {
      LOGGER.error("Consumer or Provider user ID is null or not found Cannot fetch user details.");
      promise.fail(new DxBadRequestException("Consumer or Provider user ID is null."));
    }
    return promise.future();
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

  public Future<MailMessage> sendEmailForCreateAccessRequestConsumerAck(EmailRequest emailRequest) {

    Promise<MailMessage> promise = Promise.promise();

    String senderEmail = config.getString("emailSender");
    String emailTemplate = emailRequest.templateResolver().resolve();
    String senderName = config.getString("senderName");
    String platformName = config.getString("platformName");
    String platformShortName = config.getString("platformShortName");
    String dashboardUrl = getDashboardUrl(emailRequest.assetType(), emailRequest.itemId());

    List<String> supportEmailIds = config.getJsonArray("emailSupport").getList();

    if (emailRequest.consumerUserId() != null) {

      getUserDetails(emailRequest.consumerUserId())
          .onSuccess(
              userDetails -> {
                Map<String, String> emailDetails =
                    Map.of(
                        "CONTACT_US_URL",
                        config.getString("publisherPanelUrl") + "/contact-us",
                        "CONSUMER_FIRST_NAME",
                        userDetails.firstName(),
                        "CONSUMER_LAST_NAME",
                        userDetails.lastName(),
                        "ASSET_NAME",
                        emailRequest.assetName(),
                        "ASSET_DESCRIPTION",
                        emailRequest.shortDescription(),
                        "PLATFORM_NAME",
                        platformName,
                        "PLATFORM_SHORT_NAME",
                        platformShortName,
                        "DASHBOARD_URL",
                        dashboardUrl,
                        "SENDER_NAME",
                        senderName,
                        "ACK_MESSAGE",
                        "Your access request has been successfully submitted and is currently under review.");

                String htmlBody = TemplateCreator.render(emailTemplate, emailDetails);

                MailMessage mailMessage =
                    createConsumerAckMailMessage(
                        senderEmail,
                        userDetails.userEmail(),
                        supportEmailIds,
                        htmlBody,
                        platformShortName);

                promise.complete(mailMessage);
              })
          .onFailure(
              err -> {
                LOGGER.error("Failed to fetch consumer details: {}", err.getMessage());
                promise.fail(err);
              });

    } else {
      LOGGER.error("Consumer user ID is null.");
      promise.fail(new DxBadRequestException("Consumer user ID is null."));
    }

    return promise.future();
  }

  public MailMessage createConsumerAckMailMessage(
      String senderEmail,
      String consumerEmail,
      List<String> supportEmailIds,
      String body,
      String platformShortName) {

    MailMessage message = new MailMessage();
    message.setFrom(senderEmail);
    message.setTo(consumerEmail);
    message.setCc(supportEmailIds);

    message.setSubject(platformShortName + " Platform – Dataset Access Request Received");

    message.setHtml(body);
    return message;
  }
}
