package org.cdpg.dx.email.verticle;

import static org.cdpg.dx.email.util.Constants.EMAIL_PERMANENT_FAILURE_CODE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetSocket;
import io.vertx.core.parsetools.RecordParser;
import io.vertx.ext.mail.MailClient;
import io.vertx.ext.mail.MailConfig;
import io.vertx.ext.mail.MailMessage;
import io.vertx.ext.mail.StartTLSOptions;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.serviceproxy.ServiceBinder;
import io.vertx.serviceproxy.ServiceException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.cdpg.dx.acl.accessRequest.util.EmailType;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.exception.KeycloakServiceException;
import org.cdpg.dx.email.service.EmailService;
import org.cdpg.dx.email.service.EmailServiceImpl;
import org.cdpg.dx.email.util.EmailComposer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Runs the real mail client against a fake SMTP server that behaves like Office365: it advertises
 * XOAUTH2 and LOGIN, rejects the password sent as an XOAUTH2 token, and then rejects any further
 * AUTH in the same session.
 */
@ExtendWith(VertxExtension.class)
class EmailSmtpAuthTest {
  private final List<String> authCommands = new CopyOnWriteArrayList<>();
  private NetServer server;
  private MessageConsumer<JsonObject> proxyConsumer;

  /** Reply sent to RCPT TO; lets a test simulate a temporary server-side failure. */
  private String rcptReply = "250 ok";

  private boolean rejectLogin;
  private boolean xoauthRejected;
  private boolean inData;
  private int loginStep;

  @BeforeEach
  void startServer(Vertx vertx, VertxTestContext ctx) {
    server =
        vertx
            .createNetServer()
            .connectHandler(
                socket -> {
                  socket.write("220 fake ready\r\n");
                  socket.handler(
                      RecordParser.newDelimited("\r\n", line -> reply(socket, line.toString())));
                });
    server.listen(0, "127.0.0.1").onComplete(ctx.succeedingThenComplete());
  }

  private void reply(NetSocket socket, String line) {
    if (inData) {
      if (line.equals(".")) {
        inData = false;
        socket.write("250 queued\r\n");
      }
      return;
    }
    if (loginStep > 0) {
      if (loginStep == 1) {
        loginStep = 2;
        socket.write("334 UGFzc3dvcmQ6\r\n");
      } else {
        loginStep = 0;
        socket.write(
            rejectLogin
                ? "535 5.7.3 Authentication unsuccessful\r\n"
                : "235 2.7.0 Authentication successful\r\n");
      }
      return;
    }
    String upper = line.toUpperCase();
    if (upper.startsWith("EHLO")) {
      socket.write("250-fake\r\n250 AUTH LOGIN XOAUTH2\r\n");
    } else if (upper.startsWith("AUTH XOAUTH2")) {
      authCommands.add("XOAUTH2");
      xoauthRejected = true;
      socket.write("535 5.7.3 Authentication unsuccessful\r\n");
    } else if (upper.startsWith("AUTH LOGIN")) {
      authCommands.add("LOGIN");
      if (xoauthRejected) {
        socket.write("535 5.7.3 Authentication unsuccessful\r\n");
      } else {
        loginStep = 1;
        socket.write("334 VXNlcm5hbWU6\r\n");
      }
    } else if (upper.startsWith("RCPT")) {
      socket.write(rcptReply + "\r\n");
    } else if (upper.equals("DATA")) {
      inData = true;
      socket.write("354 go ahead\r\n");
    } else if (upper.startsWith("QUIT")) {
      socket.write("221 bye\r\n").onComplete(v -> socket.close());
    } else {
      socket.write("250 ok\r\n");
    }
  }

  @AfterEach
  void stopServer(VertxTestContext ctx) {
    if (proxyConsumer != null) {
      proxyConsumer.unregister();
    }
    server.close().onComplete(ctx.succeedingThenComplete());
  }

  private static JsonObject verticleConfig(int port) {
    return new JsonObject()
        .put("emailHostName", "127.0.0.1")
        .put("emailPort", port)
        .put("emailUserName", "user@example.org")
        .put("emailPassword", "secret");
  }

  private MailConfig mailConfig(JsonObject verticleConfig) {
    // The fake server has no TLS; everything else is what production builds.
    return EmailVerticle.buildMailConfig(verticleConfig)
        .setStarttls(StartTLSOptions.DISABLED)
        .setKeepAlive(false);
  }

  private static MailMessage mail() {
    return new MailMessage()
        .setFrom("from@example.org")
        .setTo("to@example.org")
        .setSubject("subject")
        .setText("body");
  }

  /** Goes through the real proxy, as the RabbitMQ consumer does. */
  private EmailService proxiedService(Vertx vertx, EmailComposer composer, MailClient client) {
    EmailService impl = new EmailServiceImpl(client, true, null, null, composer);
    String address = "email-test-" + UUID.randomUUID();
    proxyConsumer =
        new ServiceBinder(vertx).setAddress(address).register(EmailService.class, impl);
    return EmailService.createProxy(vertx, address);
  }

  private static JsonObject validRequest() {
    return new JsonObject()
        .put("consumerUserId", UUID.randomUUID().toString())
        .put("templateType", "INLINE")
        .put("templateStructure", "x")
        .put("isCreated", true)
        .put("emailType", EmailType.CONSUMER_ACK.name());
  }

  @Test
  void defaultConfigUsesLoginAndNeverOffersXoauth2(Vertx vertx, VertxTestContext ctx) {
    MailClient client = MailClient.create(vertx, mailConfig(verticleConfig(server.actualPort())));

    client
        .sendMail(mail())
        .onComplete(
            ctx.succeeding(
                r ->
                    ctx.verify(
                        () -> {
                          assertEquals(List.of("LOGIN"), authCommands);
                          ctx.completeNow();
                        })));
  }

  /** Pins the root cause: if this starts passing unrestricted, the library behaviour changed. */
  @Test
  void unrestrictedClientReproducesOffice365Failure(Vertx vertx, VertxTestContext ctx) {
    MailConfig unrestricted = mailConfig(verticleConfig(server.actualPort())).setAuthMethods(null);

    MailClient.create(vertx, unrestricted)
        .sendMail(mail())
        .onComplete(
            ctx.failing(
                err ->
                    ctx.verify(
                        () -> {
                          assertEquals(List.of("XOAUTH2", "LOGIN"), authCommands);
                          assertTrue(err.getMessage().contains("535"), err.getMessage());
                          ctx.completeNow();
                        })));
  }

  @Test
  void authMethodsAreConfigurable(Vertx vertx, VertxTestContext ctx) {
    JsonObject config = verticleConfig(server.actualPort()).put("emailAuthMethods", "XOAUTH2");

    MailClient.create(vertx, mailConfig(config))
        .sendMail(mail())
        .onComplete(
            ctx.failing(
                err ->
                    ctx.verify(
                        () -> {
                          assertEquals(List.of("XOAUTH2"), authCommands);
                          ctx.completeNow();
                        })));
  }

  @Test
  void rejectedLoginIsPermanentAcrossTheProxy(Vertx vertx, VertxTestContext ctx) {
    rejectLogin = true;
    EmailComposer composer = mock(EmailComposer.class);
    when(composer.sendEmailForCreateAccessRequestConsumerAck(any()))
        .thenReturn(Future.succeededFuture(mail()));
    MailClient client = MailClient.create(vertx, mailConfig(verticleConfig(server.actualPort())));

    proxiedService(vertx, composer, client)
        .sendEmailService(validRequest())
        .onComplete(ctx.failing(err -> ctx.verify(() -> assertCode(err, ctx, true))));
  }

  @Test
  void temporarySmtpFailureIsNotPermanent(Vertx vertx, VertxTestContext ctx) {
    rcptReply = "451 4.3.0 try again later";
    EmailComposer composer = mock(EmailComposer.class);
    when(composer.sendEmailForCreateAccessRequestConsumerAck(any()))
        .thenReturn(Future.succeededFuture(mail()));
    MailClient client =
        MailClient.create(
            vertx, mailConfig(verticleConfig(server.actualPort())).setAllowRcptErrors(false));

    proxiedService(vertx, composer, client)
        .sendEmailService(validRequest())
        .onComplete(ctx.failing(err -> ctx.verify(() -> assertCode(err, ctx, false))));
  }

  @Test
  void missingKeycloakUserIsPermanent(Vertx vertx, VertxTestContext ctx) {
    EmailComposer composer = mock(EmailComposer.class);
    when(composer.sendEmailForCreateAccessRequestConsumerAck(any()))
        .thenReturn(
            Future.failedFuture(new DxNotFoundException("User not found")));

    proxiedService(vertx, composer, mock(MailClient.class))
        .sendEmailService(validRequest())
        .onComplete(ctx.failing(err -> ctx.verify(() -> assertCode(err, ctx, true))));
  }

  @Test
  void keycloakOutageIsNotPermanent(Vertx vertx, VertxTestContext ctx) {
    EmailComposer composer = mock(EmailComposer.class);
    when(composer.sendEmailForCreateAccessRequestConsumerAck(any()))
        .thenReturn(
            Future.failedFuture(
                new KeycloakServiceException(
                    "Failed to retrieve user", new RuntimeException("connection refused"))));

    proxiedService(vertx, composer, mock(MailClient.class))
        .sendEmailService(validRequest())
        .onComplete(ctx.failing(err -> ctx.verify(() -> assertCode(err, ctx, false))));
  }

  @Test
  void malformedPayloadIsPermanent(Vertx vertx, VertxTestContext ctx) {
    proxiedService(vertx, mock(EmailComposer.class), mock(MailClient.class))
        .sendEmailService(new JsonObject().put("emailType", "NOT_A_TYPE"))
        .onComplete(ctx.failing(err -> ctx.verify(() -> assertCode(err, ctx, true))));
  }

  private static void assertCode(Throwable err, VertxTestContext ctx, boolean permanent) {
    ServiceException serviceException = assertInstanceOf(ServiceException.class, err);
    assertEquals(
        permanent, serviceException.failureCode() == EMAIL_PERMANENT_FAILURE_CODE, err.toString());
    ctx.completeNow();
  }
}
