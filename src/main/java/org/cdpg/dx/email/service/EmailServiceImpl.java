package org.cdpg.dx.email.service;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.ext.mail.MailClient;
import io.vertx.ext.mail.MailMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class EmailServiceImpl implements EmailService {
  private final static Logger LOGGER = LogManager.getLogger(EmailServiceImpl.class);
  private final MailClient mailClient;
  private final boolean notifyByEmail;

  public EmailServiceImpl(MailClient mailClient, boolean notifyByEmail) {
    this.mailClient = mailClient;
    this.notifyByEmail = notifyByEmail;
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
    mailClient.sendMail(message, res -> {
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

}
