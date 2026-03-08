package org.cdpg.dx.aaa.credit.util;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.credit.models.CreditRequest;
import org.cdpg.dx.aaa.credit.service.CreditService;

/**
 * Enriches credit request records with balance and expiration date information.
 *
 * <p>This utility consolidates the duplicated enrichment logic that was previously present in
 * {@code CreditHandler.getCreditRequests} and {@code CreditHandler.getUserCreditRequests}.
 */
public final class CreditRequestEnricher {

  private static final Logger LOGGER = LogManager.getLogger(CreditRequestEnricher.class);

  private CreditRequestEnricher() {}

  /**
   * Enriches a list of credit requests with the user's current balance and credit expiration date.
   *
   * <p>For each credit request, two parallel lookups are made:
   * <ol>
   *   <li>The user's credit balance</li>
   *   <li>The user's credit expiration date</li>
   * </ol>
   * Results are merged into the credit request JSON. If either lookup fails, safe defaults are used
   * (balance=0.0, expirationDate=null).
   *
   * @param creditRequests the list of credit requests to enrich
   * @param creditService  the credit service for balance/expiry lookups
   * @return a future containing the enriched list of JSON objects
   */
  public static Future<List<JsonObject>> enrichWithBalanceAndExpiry(
      List<CreditRequest> creditRequests, CreditService creditService) {

    List<Future<JsonObject>> futures =
        creditRequests.stream()
            .map(cr -> enrichSingle(cr, creditService))
            .toList();

    return Future.all(futures).map(cf -> cf.<JsonObject>list());
  }

  private static Future<JsonObject> enrichSingle(
      CreditRequest cr, CreditService creditService) {

    UUID userId = cr.userId();
    Promise<JsonObject> promise = Promise.promise();

    Future<JsonObject> balanceFuture =
        creditService
            .getBalance(userId)
            .map(res -> new JsonObject().put("balance", res.getString("balance")))
            .otherwise(new JsonObject().put("balance", 0.0));

    Future<JsonObject> expiryFuture =
        creditService
            .getExpirationDateByUserId(userId)
            .map(res -> new JsonObject().put("expirationDate", res.expirationDate().toString()))
            .otherwise(new JsonObject().put("expirationDate", (String) null));

    Future.all(balanceFuture, expiryFuture)
        .onSuccess(
            cf -> {
              JsonObject creditRequestJson =
                  JsonObject.mapFrom(cr).mergeIn(cf.resultAt(0)).mergeIn(cf.resultAt(1));
              promise.complete(creditRequestJson);
            })
        .onFailure(
            err -> {
              LOGGER.warn(
                  "Failed to enrich credit request for user {}: {}", userId, err.getMessage());
              JsonObject creditRequestJson =
                  JsonObject.mapFrom(cr)
                      .put("balance", 0.0)
                      .put("expirationDate", (String) null);
              promise.complete(creditRequestJson);
            });

    return promise.future();
  }
}
