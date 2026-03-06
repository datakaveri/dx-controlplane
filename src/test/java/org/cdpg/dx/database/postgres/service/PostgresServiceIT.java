package org.cdpg.dx.database.postgres.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cdpg.dx.testutil.VertxFutureAssert.assertFutureSuccess;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;
import java.util.List;
import java.util.UUID;
import org.cdpg.dx.database.postgres.models.Condition;
import org.cdpg.dx.database.postgres.models.DeleteQuery;
import org.cdpg.dx.database.postgres.models.InsertQuery;
import org.cdpg.dx.database.postgres.models.QueryResult;
import org.cdpg.dx.database.postgres.models.SelectQuery;
import org.cdpg.dx.database.postgres.models.UpdateQuery;
import org.cdpg.dx.testutil.PostgresTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Integration tests that exercise {@link PostgresService} directly (not through a DAO)
 * against a real PostgreSQL container. The credit_requests table is used as the test target.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresServiceIT extends PostgresTestBase {

  private static final String TABLE = "credit_requests";

  // ------------------------------------------------------------------
  // insert
  // ------------------------------------------------------------------

  @Test
  @DisplayName("insert - should insert a row and return it with a generated id")
  void testInsert(VertxTestContext ctx) {
    UUID userId = UUID.randomUUID();

    InsertQuery query =
        new InsertQuery(
            TABLE,
            List.of("user_id", "user_name", "status"),
            List.of(userId.toString(), "Test User", "pending"));

    assertFutureSuccess(
        postgresService.insert(query),
        ctx,
        result -> {
          assertThat(result).isNotNull();
          assertThat(result.isRowsAffected()).isTrue();
          assertThat(result.getRows()).isNotNull();
          assertThat(result.getRows().size()).isEqualTo(1);

          JsonObject row = result.getRows().getJsonObject(0);
          assertThat(row.getString("id")).isNotNull();
          assertThat(row.getString("user_id")).isEqualTo(userId.toString());
          assertThat(row.getString("user_name")).isEqualTo("Test User");
          assertThat(row.getString("status")).isEqualTo("pending");
        });
  }

  // ------------------------------------------------------------------
  // select
  // ------------------------------------------------------------------

  @Test
  @DisplayName("select - should find previously inserted rows")
  void testSelect(VertxTestContext ctx) {
    UUID userId = UUID.randomUUID();

    InsertQuery insertQuery =
        new InsertQuery(
            TABLE,
            List.of("user_id", "user_name", "status"),
            List.of(userId.toString(), "Select User", "pending"));

    Condition condition =
        new Condition("user_id", Condition.Operator.EQUALS, List.of(userId.toString()));
    SelectQuery selectQuery =
        new SelectQuery(TABLE, List.of("*"), condition, null, null, null, null);

    postgresService
        .insert(insertQuery)
        .compose(v -> postgresService.select(selectQuery, false))
        .onComplete(
            ctx.succeeding(
                result ->
                    ctx.verify(
                        () -> {
                          assertThat(result.getRows()).hasSize(1);

                          JsonObject row = result.getRows().getJsonObject(0);
                          assertThat(row.getString("user_name")).isEqualTo("Select User");
                          assertThat(row.getString("status")).isEqualTo("pending");
                          ctx.completeNow();
                        })));
  }

  // ------------------------------------------------------------------
  // update
  // ------------------------------------------------------------------

  @Test
  @DisplayName("update - should update fields and return the modified row")
  void testUpdate(VertxTestContext ctx) {
    UUID userId = UUID.randomUUID();

    InsertQuery insertQuery =
        new InsertQuery(
            TABLE,
            List.of("user_id", "user_name", "status"),
            List.of(userId.toString(), "Update User", "pending"));

    postgresService
        .insert(insertQuery)
        .compose(
            insertResult -> {
              String id = insertResult.getRows().getJsonObject(0).getString("id");
              Condition condition =
                  new Condition("id", Condition.Operator.EQUALS, List.of(id));
              UpdateQuery updateQuery =
                  new UpdateQuery(TABLE, List.of("status"), List.of("granted"), condition, null, null);
              return postgresService.update(updateQuery);
            })
        .onComplete(
            ctx.succeeding(
                result ->
                    ctx.verify(
                        () -> {
                          assertThat(result.isRowsAffected()).isTrue();
                          assertThat(result.getRows()).hasSize(1);

                          JsonObject row = result.getRows().getJsonObject(0);
                          assertThat(row.getString("status")).isEqualTo("granted");
                          assertThat(row.getString("user_name")).isEqualTo("Update User");
                          ctx.completeNow();
                        })));
  }

  // ------------------------------------------------------------------
  // delete
  // ------------------------------------------------------------------

  @Test
  @DisplayName("delete - should remove a row and report rowsAffected")
  void testDelete(VertxTestContext ctx) {
    UUID userId = UUID.randomUUID();

    InsertQuery insertQuery =
        new InsertQuery(
            TABLE,
            List.of("user_id", "user_name", "status"),
            List.of(userId.toString(), "Delete User", "pending"));

    postgresService
        .insert(insertQuery)
        .compose(
            insertResult -> {
              String id = insertResult.getRows().getJsonObject(0).getString("id");
              Condition condition =
                  new Condition("id", Condition.Operator.EQUALS, List.of(id));
              DeleteQuery deleteQuery = new DeleteQuery(TABLE, condition, null, null);
              return postgresService.delete(deleteQuery);
            })
        .onComplete(
            ctx.succeeding(
                result ->
                    ctx.verify(
                        () -> {
                          assertThat(result.isRowsAffected()).isTrue();
                          ctx.completeNow();
                        })));
  }

  // ------------------------------------------------------------------
  // executeQuery (raw SQL)
  // ------------------------------------------------------------------

  @Test
  @DisplayName("executeQuery - should execute raw SQL and return results")
  void testExecuteQuery(VertxTestContext ctx) {
    UUID userId = UUID.randomUUID();

    // Insert via raw SQL
    String insertSql =
        "INSERT INTO "
            + TABLE
            + " (user_id, user_name, status) VALUES ($1, $2, $3) RETURNING *";
    JsonArray insertParams =
        new JsonArray().add(userId.toString()).add("Raw SQL User").add("pending");

    postgresService
        .executeQuery(insertSql, insertParams)
        .compose(
            insertResult -> {
              String id = insertResult.getRows().getJsonObject(0).getString("id");
              // Select via raw SQL
              String selectSql = "SELECT * FROM " + TABLE + " WHERE id = $1";
              JsonArray selectParams = new JsonArray().add(UUID.fromString(id));
              return postgresService.executeQuery(selectSql, selectParams);
            })
        .onComplete(
            ctx.succeeding(
                result ->
                    ctx.verify(
                        () -> {
                          assertThat(result.getRows()).hasSize(1);

                          JsonObject row = result.getRows().getJsonObject(0);
                          assertThat(row.getString("user_name")).isEqualTo("Raw SQL User");
                          ctx.completeNow();
                        })));
  }

  // ------------------------------------------------------------------
  // QueryResult structure verification
  // ------------------------------------------------------------------

  @Test
  @DisplayName("QueryResult - should have correct structure after insert")
  void testQueryResultStructure(VertxTestContext ctx) {
    UUID userId = UUID.randomUUID();

    InsertQuery query =
        new InsertQuery(
            TABLE,
            List.of("user_id", "user_name", "status"),
            List.of(userId.toString(), "Structure User", "pending"));

    assertFutureSuccess(
        postgresService.insert(query),
        ctx,
        result -> {
          // Verify QueryResult fields are populated
          assertThat(result).isInstanceOf(QueryResult.class);
          assertThat(result.getRows()).isNotNull().isInstanceOf(JsonArray.class);
          assertThat(result.isRowsAffected()).isTrue();
          assertThat(result.getTotalCount()).isGreaterThan(0);

          // Verify the returned row contains all expected columns
          JsonObject row = result.getRows().getJsonObject(0);
          assertThat(row.containsKey("id")).isTrue();
          assertThat(row.containsKey("user_id")).isTrue();
          assertThat(row.containsKey("user_name")).isTrue();
          assertThat(row.containsKey("status")).isTrue();
          assertThat(row.containsKey("requested_at")).isTrue();
        });
  }

  // ------------------------------------------------------------------
  // ping
  // ------------------------------------------------------------------

  @Test
  @DisplayName("ping - should return true when database is reachable")
  void testPing(VertxTestContext ctx) {
    assertFutureSuccess(
        postgresService.ping(),
        ctx,
        result -> assertThat(result).isTrue());
  }
}
