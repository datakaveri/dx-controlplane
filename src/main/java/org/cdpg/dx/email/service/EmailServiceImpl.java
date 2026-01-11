package org.cdpg.dx.email.service;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mail.MailClient;
import io.vertx.ext.mail.MailMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
    if (emailRequest.isCreated()) {
      emailComposer
          .sendEmailForCreateAccessRequest(emailRequest)
          .compose(v -> sendEmail(v))
          .onSuccess(
              success -> {
                LOGGER.debug("Email sent successfully for access request creation.");
                promise.complete();
              })
          .onFailure(promise::fail);
    } else {
      emailComposer
          .sendEmailForUpdateAccessRequest(emailRequest)
          .compose(this::sendEmail)
          .onSuccess(
              success -> {
                LOGGER.debug("Email sent successfully for access request update.");
                promise.complete();
              })
          .onFailure(promise::fail);
    }
    return promise.future();
  }
}
