package org.cdpg.dx.email.verticle;

import static org.cdpg.dx.common.config.ServiceProxyAddressConstants.DATA_BROKER_SERVICE_ADDRESS;
import static org.cdpg.dx.common.config.ServiceProxyAddressConstants.EMAIL_SERVICE_ADDRESS;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mail.LoginOption;
import io.vertx.ext.mail.MailClient;
import io.vertx.ext.mail.MailConfig;
import io.vertx.ext.mail.StartTLSOptions;
import io.vertx.serviceproxy.ServiceBinder;
import io.vertx.sqlclient.Pool;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.common.ItemType;
import org.cdpg.dx.databroker.service.DataBrokerService;
import org.cdpg.dx.email.service.EmailService;
import org.cdpg.dx.email.service.EmailServiceImpl;
import org.cdpg.dx.email.util.EmailComposer;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
import org.cdpg.dx.keycloak.service.KeycloakUserServiceImpl;

public class EmailVerticle extends AbstractVerticle {
  private static final Logger LOGGER = LogManager.getLogger(EmailVerticle.class);
  private static final String DEFAULT_AUTH_METHODS = "LOGIN PLAIN";
  private MessageConsumer<JsonObject> consumer;
  private ServiceBinder binder;
  private DataBrokerService dataBrokerService;
  private KeycloakUserService keycloakUserService;
  private Pool pool;

  @Override
  public void start(Promise<Void> startPromise) {
    ItemType.configurePrefix(config());

    boolean notifyByEmail = config().getBoolean("notifyByEmail", true);
    dataBrokerService = DataBrokerService.createProxy(vertx, DATA_BROKER_SERVICE_ADDRESS);
    keycloakUserService = new KeycloakUserServiceImpl(config());

    MailClient mailClient = MailClient.create(vertx, buildMailConfig(config()));
    EmailComposer emailComposer = new EmailComposer(config(), keycloakUserService);
    EmailService service =
        new EmailServiceImpl(
            mailClient, notifyByEmail, dataBrokerService, keycloakUserService, emailComposer);
    binder = new ServiceBinder(vertx);
    consumer = binder.setAddress(EMAIL_SERVICE_ADDRESS).register(EmailService.class, service);
    startPromise.complete();
  }

  static MailConfig buildMailConfig(JsonObject verticleConfig) {
    MailConfig config = new MailConfig();
    config.setHostname(verticleConfig.getString("emailHostName"));
    config.setLogin(LoginOption.REQUIRED);
    config.setKeepAliveTimeout(5);
    config.setAllowRcptErrors(true);
    config.setPort(verticleConfig.getInteger("emailPort"));
    config.setStarttls(StartTLSOptions.REQUIRED);
    config.setUsername(verticleConfig.getString("emailUserName"));
    config.setPassword(verticleConfig.getString("emailPassword"));
    // Left unrestricted, the client tries XOAUTH2 first on servers that offer it, sending the
    // password as a token. Office365 then rejects the LOGIN fallback in the same session (535).
    config.setAuthMethods(verticleConfig.getString("emailAuthMethods", DEFAULT_AUTH_METHODS));
    return config;
  }

  @Override
  public void stop() {
    if (binder != null && consumer != null) {
      binder.unregister(consumer);
    }
  }
}
