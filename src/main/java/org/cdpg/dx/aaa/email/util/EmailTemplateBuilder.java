package org.cdpg.dx.aaa.email.util;

import io.vertx.core.Future;
import io.vertx.ext.mail.MailMessage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.email.service.EmailService;

/**
 * Fluent builder for constructing and sending templated HTML emails.
 *
 * <p>Absorbs the repetitive load-template → substitute-variables → create-mail → send pattern
 * that appears in every {@link EmailComposer} method.
 *
 * <h3>Usage:</h3>
 * <pre>{@code
 * new EmailTemplateBuilder(emailService, config)
 *     .template("templates/request-credit.html")
 *     .to(recipientEmail)
 *     .subject("Credit Request")
 *     .variable("USER_FIRST_NAME", name)
 *     .variable("ADMIN_PORTAL_URL", url)
 *     .send();  // → Future<Void>
 * }</pre>
 */
public final class EmailTemplateBuilder {

  private static final Logger LOGGER = LogManager.getLogger(EmailTemplateBuilder.class);

  private final EmailService emailService;
  private final String senderEmail;

  private String templatePath;
  private String recipient;
  private String subject;
  private final Map<String, String> variables = new LinkedHashMap<>();

  public EmailTemplateBuilder(EmailService emailService, String senderEmail) {
    this.emailService = emailService;
    this.senderEmail = senderEmail;
  }

  /** Set the classpath resource path for the HTML template. */
  public EmailTemplateBuilder template(String resourcePath) {
    this.templatePath = resourcePath;
    return this;
  }

  /** Set the recipient email address. */
  public EmailTemplateBuilder to(String recipientEmail) {
    this.recipient = recipientEmail;
    return this;
  }

  /** Set the email subject line. */
  public EmailTemplateBuilder subject(String subjectLine) {
    this.subject = subjectLine;
    return this;
  }

  /** Add a template variable replacement: {@code ${key}} → value. */
  public EmailTemplateBuilder variable(String key, String value) {
    this.variables.put(key, value);
    return this;
  }

  /** Add all template variable replacements from a map. */
  public EmailTemplateBuilder variables(Map<String, String> vars) {
    this.variables.putAll(vars);
    return this;
  }

  /**
   * Build and send the email. Loads the template, substitutes variables, and sends via
   * {@link EmailService}.
   *
   * @return a succeeded {@code Future<Void>} on success, or a failed future on error
   */
  public Future<Void> send() {
    String htmlTemplate = loadTemplate(templatePath);
    String htmlBody = substituteVariables(htmlTemplate, variables);

    MailMessage message = new MailMessage();
    message.setFrom(senderEmail);
    message.setTo(recipient);
    message.setSubject(subject);
    message.setHtml(htmlBody);

    return emailService
        .sendEmail(message)
        .onSuccess(v -> LOGGER.info("Email '{}' sent to {}", subject, recipient))
        .onFailure(err -> LOGGER.error("Failed to send email '{}' to {}: {}",
            subject, recipient, err.getMessage()));
  }

  // ── internal helpers ──

  static String loadTemplate(String resourcePath) {
    try (InputStream inputStream =
            EmailTemplateBuilder.class.getClassLoader().getResourceAsStream(resourcePath);
        Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8)) {
      return scanner.useDelimiter("\\A").next();
    } catch (Exception e) {
      throw new RuntimeException("Failed to load template: " + resourcePath, e);
    }
  }

  static String substituteVariables(String template, Map<String, String> replacements) {
    String result = template;
    for (Map.Entry<String, String> entry : replacements.entrySet()) {
      result = result.replace("${" + entry.getKey() + "}", entry.getValue());
    }
    return result;
  }
}
