package org.cdpg.dx.testutil;

import static org.cdpg.dx.common.config.ServiceProxyAddressConstants.POSTGRES_SERVICE_ADDRESS;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.concurrent.TimeUnit;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.cdpg.dx.database.postgres.verticle.PostgresVerticle;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for DAO integration tests.
 *
 * <p>Starts a Testcontainers PostgreSQL instance, applies Flyway migrations, deploys the
 * PostgresVerticle, and truncates all tables before each test for isolation.
 */
@Testcontainers
@ExtendWith(VertxExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class PostgresTestBase {

  @Container
  protected static final PostgreSQLContainer<?> PG =
      new PostgreSQLContainer<>("postgres:15-alpine")
          .withDatabaseName("testdb")
          .withUsername("testuser")
          .withPassword("testpass");

  protected PostgresService postgresService;
  private String deploymentId;

  @BeforeAll
  void setUp(Vertx vertx, VertxTestContext ctx) throws Exception {
    // 1. Run Flyway migrations against the container
    Flyway flyway =
        Flyway.configure()
            .dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())
            .locations("classpath:db/migration")
            .placeholders(java.util.Map.of("authUser", "testuser"))
            .schemas("public")
            .load();
    flyway.migrate();

    // 2. Deploy PostgresVerticle so the EventBus proxy is available
    JsonObject pgConfig =
        new JsonObject()
            .put("databaseIP", PG.getHost())
            .put("databasePort", PG.getMappedPort(5432))
            .put("databaseName", PG.getDatabaseName())
            .put("databaseUserName", PG.getUsername())
            .put("databasePassword", PG.getPassword())
            .put("databaseSchema", "public")
            .put("poolSize", 5);

    DeploymentOptions opts = new DeploymentOptions().setConfig(pgConfig);

    vertx
        .deployVerticle(new PostgresVerticle(), opts)
        .onComplete(
            ctx.succeeding(
                id -> {
                  deploymentId = id;
                  postgresService = PostgresService.createProxy(vertx, POSTGRES_SERVICE_ADDRESS);
                  ctx.completeNow();
                }));

    // Block until the context completes (max 30 s)
    ctx.awaitCompletion(30, TimeUnit.SECONDS);
  }

  /**
   * Truncate every application table with CASCADE before each test so tests are fully isolated.
   * Order matters because of foreign-key constraints -- children are listed before parents.
   */
  @BeforeEach
  void truncateTables(VertxTestContext ctx) {
    String truncateSql =
        "TRUNCATE TABLE "
            + "provider_feedback, "
            + "user_interactions, "
            + "item_votes, "
            + "bookmarks, "
            + "app_constraints, "
            + "app_credentials, "
            + "subscription, "
            + "credit_transactions, "
            + "credit_requests, "
            + "user_credits, "
            + "compute_role, "
            + "provider_requests, "
            + "organization_users, "
            + "organization_join_requests, "
            + "organization_create_requests, "
            + "organizations, "
            + "client_credentials, "
            + "resource_servers "
            + "CASCADE";

    postgresService
        .executeQuery(truncateSql, new JsonArray())
        .onComplete(
            ctx.succeeding(
                result ->
                    ctx.verify(
                        () -> {
                          ctx.completeNow();
                        })));
  }

  @AfterAll
  void tearDown(Vertx vertx, VertxTestContext ctx) {
    if (deploymentId != null) {
      vertx.undeploy(deploymentId).onComplete(ctx.succeeding(v -> ctx.completeNow()));
    } else {
      ctx.completeNow();
    }
  }
}
