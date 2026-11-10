package org.cdpg.dx.aaa.organization.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cdpg.dx.testutil.VertxFutureAssert.assertFutureFailure;
import static org.cdpg.dx.testutil.VertxFutureAssert.assertFutureSuccess;

import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;
import java.util.Map;
import java.util.UUID;
import org.cdpg.dx.aaa.organization.dao.impl.OrganizationDAOImpl;
import org.cdpg.dx.aaa.organization.models.Organization;
import org.cdpg.dx.testutil.PostgresTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrganizationDAOIT extends PostgresTestBase {

  private OrganizationDAO dao;

  @BeforeEach
  void initDao(VertxTestContext ctx) {
    dao = new OrganizationDAOImpl(postgresService);
    ctx.completeNow();
  }

  // ------------------------------------------------------------------
  // helpers
  // ------------------------------------------------------------------

  /**
   * Build an Organization record for testing. The id is left null so the database generates it.
   * A unique orgName is generated per call to avoid UNIQUE constraint violations.
   */
  private static Organization anOrganization() {
    return anOrganization("Test Org " + UUID.randomUUID().toString().substring(0, 8));
  }

  private static Organization anOrganization(String orgName) {
    return new Organization(
        null, // id -- let DB generate
        orgName,
        "/logo.png",
        "Private",
        "Tech",
        "https://example.com",
        "123 Main St",
        "/cert.pdf",
        "/pan.pdf",
        "/doc.pdf",
        "org documents",
        null, // createdAt -- let DB set
        null  // updatedAt -- let DB set
    );
  }

  // ------------------------------------------------------------------
  // create
  // ------------------------------------------------------------------

  @Test
  @DisplayName("create - should persist an organization and return it with a generated id")
  void testCreate(VertxTestContext ctx) {
    Organization org = anOrganization();

    assertFutureSuccess(
        dao.create(org),
        ctx,
        created -> {
          assertThat(created).isNotNull();
          assertThat(created.id()).isNotNull();
          assertThat(created.orgName()).isEqualTo(org.orgName());
          assertThat(created.entityType()).isEqualTo("Private");
          assertThat(created.orgSector()).isEqualTo("Tech");
          assertThat(created.websiteLink()).isEqualTo("https://example.com");
        });
  }

  // ------------------------------------------------------------------
  // get by id
  // ------------------------------------------------------------------

  @Test
  @DisplayName("get - should retrieve an organization by its id")
  void testGetById(VertxTestContext ctx) {
    Organization org = anOrganization();

    dao.create(org)
        .compose(created -> dao.get(created.id()))
        .onComplete(
            ctx.succeeding(
                fetched ->
                    ctx.verify(
                        () -> {
                          assertThat(fetched).isNotNull();
                          assertThat(fetched.orgName()).isEqualTo(org.orgName());
                          assertThat(fetched.address()).isEqualTo("123 Main St");
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
  @DisplayName("getAll - should return all organizations")
  void testGetAll(VertxTestContext ctx) {
    dao.create(anOrganization())
        .compose(v -> dao.create(anOrganization()))
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
  // update
  // ------------------------------------------------------------------

  @Test
  @DisplayName("update - should update the organization name")
  void testUpdate(VertxTestContext ctx) {
    Organization org = anOrganization();

    dao.create(org)
        .compose(
            created ->
                dao.update(
                    Map.of("id", created.id().toString()),
                    Map.of("name", "Updated Org Name")))
        .onComplete(
            ctx.succeeding(
                updated ->
                    ctx.verify(
                        () -> {
                          assertThat(updated).isNotNull();
                          assertThat(updated.orgName()).isEqualTo("Updated Org Name");
                          ctx.completeNow();
                        })));
  }

  // ------------------------------------------------------------------
  // delete
  // ------------------------------------------------------------------

  @Test
  @DisplayName("delete - should remove an organization")
  void testDelete(VertxTestContext ctx) {
    Organization org = anOrganization();

    dao.create(org)
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
