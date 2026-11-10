package org.cdpg.dx.aaa.clientSecret.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cdpg.dx.testutil.VertxFutureAssert.assertFutureFailure;
import static org.cdpg.dx.testutil.VertxFutureAssert.assertFutureSuccess;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.List;
import java.util.UUID;
import org.cdpg.dx.aaa.clientSecret.dao.ClientcredetialDao;
import org.cdpg.dx.aaa.clientSecret.model.ClientCredentials;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
@DisplayName("ClientcredetialService Tests")
class ClientCredentialServiceTest {

  @Mock private ClientcredetialDao clientcredetialDao;

  private ClientcredetialServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new ClientcredetialServiceImpl(clientcredetialDao);
  }

  @Nested
  @DisplayName("createClientIdAndClientSecret")
  class CreateClientIdAndClientSecret {

    @Test
    @DisplayName("should create client credentials and return plaintext clientId and secret")
    void createClientIdAndClientSecret_success(VertxTestContext ctx) {
      UUID userId = UUID.randomUUID();
      String now = "2026-03-07T10:00:00";

      // The DAO returns the saved record (with hashed values and timestamps)
      ClientCredentials savedCredentials =
          new ClientCredentials(userId, "hashed-client-id", "hashed-client-secret", now, now);

      when(clientcredetialDao.upsertClientCredentials(
              any(ClientCredentials.class), anyList(), anyList()))
          .thenReturn(Future.succeededFuture(savedCredentials));

      Future<ClientCredentials> future = service.createClientIdAndClientSecret(userId);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            // The returned userId should match the input
            assertThat(result.userId()).isEqualTo(userId);

            // The returned clientId should be a plaintext UUID (not the hashed value)
            assertThat(result.clientId()).isNotNull();
            assertThat(result.clientId()).isNotEqualTo("hashed-client-id");
            // Verify it is a valid UUID string
            UUID.fromString(result.clientId());

            // The returned clientSecret should be a plaintext hex string (not hashed)
            assertThat(result.clientSecret()).isNotNull();
            assertThat(result.clientSecret()).isNotEqualTo("hashed-client-secret");
            // 20 bytes encoded as hex = 40 characters
            assertThat(result.clientSecret()).hasSize(40);

            // Timestamps should be forwarded from the saved record
            assertThat(result.createdAt()).isEqualTo(now);
            assertThat(result.updatedAt()).isEqualTo(now);
          });
    }

    @Test
    @DisplayName("should pass hashed clientId and hashed clientSecret to DAO")
    void createClientIdAndClientSecret_passesHashedValuesToDao(VertxTestContext ctx) {
      UUID userId = UUID.randomUUID();

      ClientCredentials savedCredentials =
          new ClientCredentials(userId, "hashed-client-id", "hashed-client-secret", null, null);

      ArgumentCaptor<ClientCredentials> credentialsCaptor =
          ArgumentCaptor.forClass(ClientCredentials.class);
      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<String>> conflictCaptor = ArgumentCaptor.forClass(List.class);
      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<String>> updateCaptor = ArgumentCaptor.forClass(List.class);

      when(clientcredetialDao.upsertClientCredentials(
              credentialsCaptor.capture(), conflictCaptor.capture(), updateCaptor.capture()))
          .thenReturn(Future.succeededFuture(savedCredentials));

      Future<ClientCredentials> future = service.createClientIdAndClientSecret(userId);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            // Verify the DAO was called
            verify(clientcredetialDao)
                .upsertClientCredentials(any(ClientCredentials.class), anyList(), anyList());

            ClientCredentials captured = credentialsCaptor.getValue();

            // The DAO should receive hashed values, not plaintext
            assertThat(captured.userId()).isEqualTo(userId);
            assertThat(captured.clientId()).isNotNull();
            // SHA-512 hex digest is 128 characters long
            assertThat(captured.clientId()).hasSize(128);
            assertThat(captured.clientSecret()).isNotNull();
            assertThat(captured.clientSecret()).hasSize(128);

            // Verify conflict and update columns
            assertThat(conflictCaptor.getValue()).containsExactly("user_id");
            assertThat(updateCaptor.getValue()).containsExactly("client_id", "client_secret");
          });
    }

    @Test
    @DisplayName("should propagate failure when DAO upsert fails")
    void createClientIdAndClientSecret_daoFailure(VertxTestContext ctx) {
      UUID userId = UUID.randomUUID();
      RuntimeException daoException = new RuntimeException("Database connection failed");

      when(clientcredetialDao.upsertClientCredentials(
              any(ClientCredentials.class), anyList(), anyList()))
          .thenReturn(Future.failedFuture(daoException));

      Future<ClientCredentials> future = service.createClientIdAndClientSecret(userId);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(RuntimeException.class);
            assertThat(err.getMessage()).isEqualTo("Database connection failed");
          });
    }
  }

  @Nested
  @DisplayName("getUserIdByClientIdAndSecret")
  class GetUserIdByClientIdAndSecret {

    @Test
    @DisplayName("should return userId when DAO finds matching credentials")
    void getUserIdByClientIdAndSecret_success(VertxTestContext ctx) {
      UUID expectedUserId = UUID.randomUUID();
      String clientId = "some-client-id";
      String clientSecret = "some-client-secret";

      when(clientcredetialDao.getClientCredentialsByClientId(clientId, clientSecret))
          .thenReturn(Future.succeededFuture(expectedUserId.toString()));

      Future<UUID> future = service.getUserIdByClientIdAndSecret(clientId, clientSecret);

      assertFutureSuccess(
          future,
          ctx,
          result -> {
            assertThat(result).isEqualTo(expectedUserId);
            verify(clientcredetialDao).getClientCredentialsByClientId(clientId, clientSecret);
          });
    }

    @Test
    @DisplayName("should propagate failure when DAO returns failed future (not found)")
    void getUserIdByClientIdAndSecret_notFound(VertxTestContext ctx) {
      String clientId = "non-existent-client-id";
      String clientSecret = "non-existent-client-secret";

      when(clientcredetialDao.getClientCredentialsByClientId(clientId, clientSecret))
          .thenReturn(Future.failedFuture(new RuntimeException("Credentials not found")));

      Future<UUID> future = service.getUserIdByClientIdAndSecret(clientId, clientSecret);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(RuntimeException.class);
            assertThat(err.getMessage()).isEqualTo("Credentials not found");
          });
    }

    @Test
    @DisplayName("should fail when DAO returns invalid UUID string")
    void getUserIdByClientIdAndSecret_invalidUuid(VertxTestContext ctx) {
      String clientId = "some-client-id";
      String clientSecret = "some-client-secret";

      when(clientcredetialDao.getClientCredentialsByClientId(clientId, clientSecret))
          .thenReturn(Future.succeededFuture("not-a-valid-uuid"));

      Future<UUID> future = service.getUserIdByClientIdAndSecret(clientId, clientSecret);

      assertFutureFailure(
          future,
          ctx,
          err -> {
            assertThat(err).isInstanceOf(IllegalArgumentException.class);
          });
    }
  }
}
