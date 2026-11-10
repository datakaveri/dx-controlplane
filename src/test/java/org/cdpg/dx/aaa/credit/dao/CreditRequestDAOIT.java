package org.cdpg.dx.aaa.credit.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cdpg.dx.testutil.VertxFutureAssert.assertFutureFailure;
import static org.cdpg.dx.testutil.VertxFutureAssert.assertFutureSuccess;

import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;
import java.util.Map;
import java.util.UUID;
import org.cdpg.dx.aaa.credit.dao.impl.CreditRequestDAOImpl;
import org.cdpg.dx.aaa.credit.models.CreditRequest;
import org.cdpg.dx.testutil.PostgresTestBase;
import org.cdpg.dx.testutil.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CreditRequestDAOIT extends PostgresTestBase {

  private CreditRequestDAO dao;

  @BeforeEach
  void initDao(VertxTestContext ctx) {
    dao = new CreditRequestDAOImpl(postgresService);
    ctx.completeNow();
  }

  // ------------------------------------------------------------------
  // create
  // ------------------------------------------------------------------

  @Test
  @DisplayName("create - should persist a credit request and return it with a generated id")
  void testCreate(VertxTestContext ctx) {
    UUID userId = UUID.randomUUID();
    CreditRequest request = TestDataFactory.aCreditRequest(userId);

    assertFutureSuccess(
        dao.create(request),
        ctx,
        created -> {
          assertThat(created).isNotNull();
          assertThat(created.id()).isNotNull();
          assertThat(created.userId()).isEqualTo(userId);
          assertThat(created.userName()).isEqualTo("Test User");
          assertThat(created.status()).isEqualTo("pending");
        });
  }

  // ------------------------------------------------------------------
  // get by id
  // ------------------------------------------------------------------

  @Test
  @DisplayName("get - should retrieve a credit request by its id")
  void testGetById(VertxTestContext ctx) {
    UUID userId = UUID.randomUUID();
    CreditRequest request = TestDataFactory.aCreditRequest(userId);

    dao.create(request)
        .compose(created -> dao.get(created.id()))
        .onComplete(
            ctx.succeeding(
                fetched ->
                    ctx.verify(
                        () -> {
                          assertThat(fetched).isNotNull();
                          assertThat(fetched.userId()).isEqualTo(userId);
                          assertThat(fetched.userName()).isEqualTo("Test User");
                          ctx.completeNow();
                        })));
  }

  @Test
  @DisplayName("get - should fail when id does not exist")
  void testGetByIdNotFound(VertxTestContext ctx) {
    UUID nonExistent = UUID.randomUUID();

    assertFutureFailure(
        dao.get(nonExistent),
        ctx,
        err -> assertThat(err).isNotNull());
  }

  // ------------------------------------------------------------------
  // getAll
  // ------------------------------------------------------------------

  @Test
  @DisplayName("getAll - should return all credit requests")
  void testGetAll(VertxTestContext ctx) {
    UUID userId1 = UUID.randomUUID();
    UUID userId2 = UUID.randomUUID();

    dao.create(TestDataFactory.aCreditRequest(userId1))
        .compose(v -> dao.create(TestDataFactory.aCreditRequest(userId2)))
        .compose(v -> dao.getAll())
        .onComplete(
            ctx.succeeding(
                list ->
                    ctx.verify(
                        () -> {
                          assertThat(list).hasSize(2);
                          ctx.completeNow();
                        })));
  }

  // ------------------------------------------------------------------
  // getAllWithFilters
  // ------------------------------------------------------------------

  @Test
  @DisplayName("getAllWithFilters - should filter by status")
  void testGetAllWithFilters(VertxTestContext ctx) {
    UUID userId1 = UUID.randomUUID();
    UUID userId2 = UUID.randomUUID();

    dao.create(TestDataFactory.aCreditRequest(userId1, "pending"))
        .compose(v -> dao.create(TestDataFactory.aCreditRequest(userId2, "granted")))
        .compose(v -> dao.getAllWithFilters(Map.of("status", "pending")))
        .onComplete(
            ctx.succeeding(
                list ->
                    ctx.verify(
                        () -> {
                          assertThat(list).hasSize(1);
                          assertThat(list.get(0).status()).isEqualTo("pending");
                          ctx.completeNow();
                        })));
  }

  // ------------------------------------------------------------------
  // update status
  // ------------------------------------------------------------------

  @Test
  @DisplayName("update - should update the status of a credit request")
  void testUpdateStatus(VertxTestContext ctx) {
    UUID userId = UUID.randomUUID();
    CreditRequest request = TestDataFactory.aCreditRequest(userId, "pending");

    dao.create(request)
        .compose(
            created ->
                dao.update(
                    Map.of("id", created.id().toString()),
                    Map.of("status", "granted")))
        .onComplete(
            ctx.succeeding(
                updated ->
                    ctx.verify(
                        () -> {
                          assertThat(updated).isNotNull();
                          assertThat(updated.status()).isEqualTo("granted");
                          ctx.completeNow();
                        })));
  }

  // ------------------------------------------------------------------
  // delete
  // ------------------------------------------------------------------

  @Test
  @DisplayName("delete - should remove a credit request")
  void testDelete(VertxTestContext ctx) {
    UUID userId = UUID.randomUUID();
    CreditRequest request = TestDataFactory.aCreditRequest(userId);

    dao.create(request)
        .compose(created -> dao.delete(created.id()))
        .onComplete(
            ctx.succeeding(
                deleted ->
                    ctx.verify(
                        () -> {
                          assertThat(deleted).isTrue();
                          ctx.completeNow();
                        })));
  }

  @Test
  @DisplayName("delete - should fail for non-existent id")
  void testDeleteNonExistent(VertxTestContext ctx) {
    UUID nonExistent = UUID.randomUUID();

    assertFutureFailure(
        dao.delete(nonExistent),
        ctx,
        err -> assertThat(err).isNotNull());
  }
}
