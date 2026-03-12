package org.cdpg.dx.testutil;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQOptions;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import org.cdpg.dx.databroker.RabbitClient;
import org.cdpg.dx.databroker.RabbitWebClient;
import org.cdpg.dx.databroker.service.DataBrokerService;
import org.cdpg.dx.databroker.service.DataBrokerServiceImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for RabbitMQ integration tests.
 *
 * <p>Starts a RabbitMQ container with management plugin, creates vhosts, and wires up
 * RabbitClient + DataBrokerServiceImpl for testing.
 */
@Testcontainers
@ExtendWith(VertxExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class RabbitMQTestBase {

  private static final String RMQ_USER = "guest";
  private static final String RMQ_PASS = "guest";

  protected static final String PROD_VHOST = "iudx-prod";
  protected static final String INTERNAL_VHOST = "iudx-internal";
  protected static final String EXTERNAL_VHOST = "iudx-external";

  @Container
  protected static final RabbitMQContainer RABBITMQ =
      new RabbitMQContainer("rabbitmq:3.13-management");

  protected DataBrokerService dataBrokerService;
  protected RabbitClient rabbitClient;
  protected RabbitMQClient prodRmqClient;
  protected RabbitMQClient internalRmqClient;

  @BeforeAll
  void setUp(Vertx vertx, VertxTestContext ctx) throws Exception {
    // Reset static WebClient from any previous test run
    resetRabbitWebClient();

    String host = RABBITMQ.getHost();
    int amqpPort = RABBITMQ.getAmqpPort();
    int mgmtPort = RABBITMQ.getMappedPort(15672);

    // Create vhosts via Management API
    createVhost(host, mgmtPort, PROD_VHOST);
    createVhost(host, mgmtPort, INTERNAL_VHOST);
    createVhost(host, mgmtPort, EXTERNAL_VHOST);

    // Set guest permissions on all vhosts
    setPermissions(host, mgmtPort, RMQ_USER, PROD_VHOST);
    setPermissions(host, mgmtPort, RMQ_USER, INTERNAL_VHOST);
    setPermissions(host, mgmtPort, RMQ_USER, EXTERNAL_VHOST);

    // Create RabbitMQ AMQP clients
    RabbitMQOptions baseOptions = new RabbitMQOptions();
    baseOptions.setHost(host);
    baseOptions.setPort(amqpPort);
    baseOptions.setUser(RMQ_USER);
    baseOptions.setPassword(RMQ_PASS);
    baseOptions.setConnectionTimeout(10000);
    baseOptions.setRequestedHeartbeat(30);
    baseOptions.setHandshakeTimeout(10000);
    baseOptions.setRequestedChannelMax(5);
    baseOptions.setNetworkRecoveryInterval(5000);
    baseOptions.setAutomaticRecoveryEnabled(true);

    RabbitMQOptions prodConfig = new RabbitMQOptions(baseOptions);
    prodConfig.setVirtualHost(PROD_VHOST);

    RabbitMQOptions internalConfig = new RabbitMQOptions(baseOptions);
    internalConfig.setVirtualHost(INTERNAL_VHOST);

    prodRmqClient = RabbitMQClient.create(vertx, prodConfig);
    internalRmqClient = RabbitMQClient.create(vertx, internalConfig);

    // Create RabbitWebClient for management API
    WebClientOptions webConfig = new WebClientOptions();
    webConfig.setKeepAlive(true);
    webConfig.setConnectTimeout(10000);
    webConfig.setDefaultHost(host);
    webConfig.setDefaultPort(mgmtPort);

    JsonObject propObj = new JsonObject();
    propObj.put("username", RMQ_USER);
    propObj.put("password", RMQ_PASS);

    RabbitWebClient rabbitWebClient = new RabbitWebClient(vertx, webConfig, propObj);
    rabbitClient = new RabbitClient(vertx, rabbitWebClient, internalRmqClient, prodRmqClient);

    dataBrokerService =
        new DataBrokerServiceImpl(
            rabbitClient, host, amqpPort, INTERNAL_VHOST, PROD_VHOST, EXTERNAL_VHOST);

    // Wait for AMQP clients to connect
    Thread.sleep(2000);

    ctx.completeNow();
    ctx.awaitCompletion(30, TimeUnit.SECONDS);
  }

  @AfterAll
  void tearDown(Vertx vertx, VertxTestContext ctx) {
    if (prodRmqClient != null) {
      prodRmqClient.stop();
    }
    if (internalRmqClient != null) {
      internalRmqClient.stop();
    }
    resetRabbitWebClient();
    ctx.completeNow();
  }

  /** Create a vhost via RabbitMQ Management HTTP API. */
  private void createVhost(String host, int port, String vhost) throws IOException {
    String encodedVhost = java.net.URLEncoder.encode(vhost, StandardCharsets.UTF_8);
    URL url = URI.create("http://" + host + ":" + port + "/api/vhosts/" + encodedVhost).toURL();
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestMethod("PUT");
    conn.setRequestProperty("Content-Type", "application/json");
    conn.setRequestProperty(
        "Authorization",
        "Basic "
            + Base64.getEncoder()
                .encodeToString((RMQ_USER + ":" + RMQ_PASS).getBytes(StandardCharsets.UTF_8)));
    conn.setDoOutput(true);
    conn.getOutputStream().write("{}".getBytes(StandardCharsets.UTF_8));
    conn.getResponseCode(); // execute request
    conn.disconnect();
  }

  /** Set full permissions for a user on a vhost via Management HTTP API. */
  private void setPermissions(String host, int port, String user, String vhost) throws IOException {
    String encodedVhost = java.net.URLEncoder.encode(vhost, StandardCharsets.UTF_8);
    URL url =
        URI.create(
                "http://" + host + ":" + port + "/api/permissions/" + encodedVhost + "/" + user)
            .toURL();
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestMethod("PUT");
    conn.setRequestProperty("Content-Type", "application/json");
    conn.setRequestProperty(
        "Authorization",
        "Basic "
            + Base64.getEncoder()
                .encodeToString((RMQ_USER + ":" + RMQ_PASS).getBytes(StandardCharsets.UTF_8)));
    conn.setDoOutput(true);
    String body = "{\"configure\":\".*\",\"write\":\".*\",\"read\":\".*\"}";
    conn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
    conn.getResponseCode();
    conn.disconnect();
  }

  /** Reset RabbitWebClient's static WebClient via reflection. */
  private void resetRabbitWebClient() {
    try {
      Class<?> clazz = Class.forName("org.cdpg.dx.databroker.RabbitWebClient");
      Method resetMethod = clazz.getDeclaredMethod("resetWebClient");
      resetMethod.setAccessible(true);
      resetMethod.invoke(null);
    } catch (Exception e) {
      // Ignore
    }
  }
}
