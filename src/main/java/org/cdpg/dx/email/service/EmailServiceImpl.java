package org.cdpg.dx.email.service;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mail.MailClient;
import io.vertx.ext.mail.MailMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.acl.accessRequest.util.EmailType;
import org.cdpg.dx.databroker.service.DataBrokerService;
import org.cdpg.dx.email.model.EmailRequest;
import org.cdpg.dx.email.util.EmailComposer;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

public class EmailServiceImpl implements EmailService {
  private static final Logger LOGGER = LogManager.getLogger(EmailServiceImpl.class);
  private final MailClient mailClient;
  private final boolean notifyByEmail;
  private final DataBrokerService dataBrokerService;
  private final KeycloakUserService keycloakUserService;
  private final EmailComposer emailComposer;

  public EmailServiceImpl(
      MailClient mailClient,
      boolean notifyByEmail,
      DataBrokerService dataBrokerService,
      KeycloakUserService keycloakUserService,
      EmailComposer emailComposer) {
    this.mailClient = mailClient;
    this.notifyByEmail = notifyByEmail;
    this.dataBrokerService = dataBrokerService;
    this.keycloakUserService = keycloakUserService;
    this.emailComposer = emailComposer;
  }

  /**
   * Sends an email using the provided MailMessage.
   *
   * @param message The MailMessage to be sent.
   * @return A Future that completes when the email is sent or fails if there is an error.
   */
  @Override
  public Future<Void> sendEmail(MailMessage message) {
    Promise<Void> promise = Promise.promise();
    if (!notifyByEmail) {
      LOGGER.info("Email notifications are disabled. Not sending email.");
      promise.complete();
      return promise.future();
    }
    mailClient.sendMail(
        message,
        res -> {
          if (res.succeeded()) {
            LOGGER.info("Email sent: {}", res.result());
            promise.complete();
          } else {
            LOGGER.error("Failed to send email", res.cause());
            promise.fail(res.cause());
          }
        });

    return promise.future();
  }

  @Override
  public Future<Void> sendEmailService(JsonObject jsonObject) {
    EmailRequest emailRequest = EmailRequest.fromJson(jsonObject);
    Promise<Void> promise = Promise.promise();

    EmailType emailType = EmailType.valueOf(jsonObject.getString("emailType"));

    Future<MailMessage> mailFuture;

    switch (emailType) {

      case PROVIDER_CREATE:
        mailFuture = emailComposer.sendEmailForCreateAccessRequest(emailRequest);
        break;

      case CONSUMER_ACK:
        mailFuture = emailComposer.sendEmailForCreateAccessRequestConsumerAck(emailRequest);
        break;

      case CONSUMER_UPDATE:
        mailFuture = emailComposer.sendEmailForUpdateAccessRequest(emailRequest);
        break;

      default:
        return Future.failedFuture(
            new IllegalArgumentException("Unsupported email type: " + emailType));
    }

    mailFuture
        .compose(this::sendEmail)
        .onSuccess(v -> {
          LOGGER.debug("Email sent successfully for type: {}", emailType);
          promise.complete();
        })
        .onFailure(err -> {
          LOGGER.error("Failed to send email for type {}: {}", emailType, err.getMessage());
          promise.fail(err);
        });

    return promise.future();
  }
}
