package org.cdpg.dx.aaa.credit.util;

import static org.assertj.core.api.Assertions.*;
import static org.cdpg.dx.testutil.VertxFutureAssert.assertFutureSuccess;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.jackson.DatabindCodec;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.cdpg.dx.aaa.credit.models.CreditRequest;
import org.cdpg.dx.aaa.credit.models.UserCredit;
import org.cdpg.dx.aaa.credit.service.CreditService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
@DisplayName("CreditRequestEnricher")
class CreditRequestEnricherTest {

  @Mock private CreditService creditService;

  @BeforeAll
  static void configureJackson() {
    ObjectMapper mapper = DatabindCodec.mapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  private static CreditRequest createCreditRequest(UUID userId) {
    return new CreditRequest(
        UUID.randomUUID(),
        userId,
        "Test User",
        new JsonObject().put("amount", 100.0),
        "PENDING",
        LocalDateTime.of(2025, 1, 15, 10, 0),
        null);
  }

  @Test
  @DisplayName("enriches single credit request with balance and expiry")
  void enrichesSingleCreditRequest(VertxTestContext ctx) {
    UUID userId = UUID.randomUUID();
    CreditRequest cr = createCreditRequest(userId);
    LocalDateTime expiry = LocalDateTime.of(2025, 12, 31, 23, 59);

    when(creditService.getBalance(userId))
        .thenReturn(Future.succeededFuture(new JsonObject().put("balance", "500.0")));
    when(creditService.getExpirationDateByUserId(userId))
        .thenReturn(
            Future.succeededFuture(new UserCredit(UUID.randomUUID(), userId, 500.0, expiry, null)));

    Future<List<JsonObject>> future =
        CreditRequestEnricher.enrichWithBalanceAndExpiry(List.of(cr), creditService);

    assertFutureSuccess(
        future,
        ctx,
        result -> {
          assertThat(result).hasSize(1);
          JsonObject enriched = result.get(0);
          assertThat(enriched.getString("balance")).isEqualTo("500.0");
          assertThat(enriched.getString("expirationDate")).isEqualTo(expiry.toString());
          assertThat(enriched.getString("userId")).isEqualTo(userId.toString());
          assertThat(enriched.getString("status")).isEqualTo("PENDING");
        });
  }

  @Test
  @DisplayName("enriches multiple credit requests with respective balances and expiry dates")
  void enrichesMultipleCreditRequests(VertxTestContext ctx) {
    UUID userId1 = UUID.randomUUID();
    UUID userId2 = UUID.randomUUID();
    CreditRequest cr1 = createCreditRequest(userId1);
    CreditRequest cr2 = createCreditRequest(userId2);
    LocalDateTime expiry1 = LocalDateTime.of(2025, 6, 30, 12, 0);
    LocalDateTime expiry2 = LocalDateTime.of(2025, 9, 15, 18, 0);

    when(creditService.getBalance(userId1))
        .thenReturn(Future.succeededFuture(new JsonObject().put("balance", "100.0")));
    when(creditService.getExpirationDateByUserId(userId1))
        .thenReturn(
            Future.succeededFuture(
                new UserCredit(UUID.randomUUID(), userId1, 100.0, expiry1, null)));

    when(creditService.getBalance(userId2))
        .thenReturn(Future.succeededFuture(new JsonObject().put("balance", "250.0")));
    when(creditService.getExpirationDateByUserId(userId2))
        .thenReturn(
            Future.succeededFuture(
                new UserCredit(UUID.randomUUID(), userId2, 250.0, expiry2, null)));

    Future<List<JsonObject>> future =
        CreditRequestEnricher.enrichWithBalanceAndExpiry(List.of(cr1, cr2), creditService);

    assertFutureSuccess(
        future,
        ctx,
        result -> {
          assertThat(result).hasSize(2);

          JsonObject enriched1 = result.get(0);
          assertThat(enriched1.getString("balance")).isEqualTo("100.0");
          assertThat(enriched1.getString("expirationDate")).isEqualTo(expiry1.toString());

          JsonObject enriched2 = result.get(1);
          assertThat(enriched2.getString("balance")).isEqualTo("250.0");
          assertThat(enriched2.getString("expirationDate")).isEqualTo(expiry2.toString());
        });
  }

  @Test
  @DisplayName("handles empty list gracefully")
  void handlesEmptyList(VertxTestContext ctx) {
    Future<List<JsonObject>> future =
        CreditRequestEnricher.enrichWithBalanceAndExpiry(Collections.emptyList(), creditService);

    assertFutureSuccess(
        future,
        ctx,
        result -> {
          assertThat(result).isEmpty();
        });
  }

  @Test
  @DisplayName("uses defaults (balance=0.0, expirationDate=null) when service lookup fails")
  void usesDefaultsWhenServiceFails(VertxTestContext ctx) {
    UUID userId = UUID.randomUUID();
    CreditRequest cr = createCreditRequest(userId);

    when(creditService.getBalance(userId))
        .thenReturn(Future.failedFuture(new RuntimeException("balance service down")));
    when(creditService.getExpirationDateByUserId(userId))
        .thenReturn(Future.failedFuture(new RuntimeException("expiry service down")));

    Future<List<JsonObject>> future =
        CreditRequestEnricher.enrichWithBalanceAndExpiry(List.of(cr), creditService);

    assertFutureSuccess(
        future,
        ctx,
        result -> {
          assertThat(result).hasSize(1);
          JsonObject enriched = result.get(0);
          assertThat(enriched.getDouble("balance")).isEqualTo(0.0);
          assertThat(enriched.getString("expirationDate")).isNull();
          assertThat(enriched.getString("userId")).isEqualTo(userId.toString());
        });
  }
}
