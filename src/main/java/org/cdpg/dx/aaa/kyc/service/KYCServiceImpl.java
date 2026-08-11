package org.cdpg.dx.aaa.kyc.service;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import org.cdpg.dx.aaa.kyc.dao.KYCTransactionDAO;
import org.cdpg.dx.aaa.kyc.model.KYCTransaction;
import org.cdpg.dx.aaa.kyc.util.Constants;
import org.cdpg.dx.common.exception.DxValidationException;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
import org.json.JSONObject;
import org.json.XML;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public class KYCServiceImpl implements KYCService {
  private static final Logger LOGGER = LoggerFactory.getLogger(KYCServiceImpl.class);

  private static final String GRANT_TYPE = "authorization_code";

  private static final String CONFIG_TOKEN_URL = "digilockerTokenUrl";
  private static final String CONFIG_AADHAAR_URL = "digilockerAadhaarUrl";
  private static final String CONFIG_CLIENT_ID = "clientId";
  private static final String CONFIG_CLIENT_SECRET = "clientSecret";
  private static final String CONFIG_REDIRECT_URI = "redirectUri";

  private final WebClient webClient;
  private final KYCTransactionDAO kycTransactionDAO;
  private final KeycloakUserService keycloakUserService;
  private final JsonObject config;

  public KYCServiceImpl(WebClient webClient,
                        KYCTransactionDAO kycTransactionDAO,
                        KeycloakUserService keycloakUserService,
                        JsonObject config) {
    this.webClient = webClient;
    this.kycTransactionDAO = kycTransactionDAO;
    this.keycloakUserService = keycloakUserService;
    this.config = config;
  }

  @Override
  public Future<JsonObject> getKYCData(UUID userId, String authCode, String codeVerifier, String userName) {
    MultiMap tokenRequestForm = MultiMap.caseInsensitiveMultiMap()
      .add("grant_type", GRANT_TYPE)
      .add("client_id", config.getString(CONFIG_CLIENT_ID))
      .add("client_secret", config.getString(CONFIG_CLIENT_SECRET))
      .add("code", authCode)
      .add("code_verifier", codeVerifier)
      .add("redirect_uri", config.getString(CONFIG_REDIRECT_URI));

    return webClient.postAbs(config.getString(CONFIG_TOKEN_URL))
      .sendForm(tokenRequestForm)
      .compose(tokenResponse -> handleTokenResponse(tokenResponse.bodyAsJsonObject(), userId, userName));
  }

  // Marks the user verified/unverified based solely on the `eaadhaar` flag from the
  // DigiLocker token response — no Aadhaar XML fetch, no duplicate-Aadhaar check.
  private Future<JsonObject> handleTokenResponse(JsonObject tokenResponse, UUID userId, String userName) {
    String accessToken = tokenResponse.getString("access_token");
    String aadhaarAvailable = tokenResponse.getString("eaadhaar");

    if (accessToken == null || accessToken.isBlank()) {
      LOGGER.error("Access token missing in response: {}", tokenResponse.encodePrettily());
      return Future.failedFuture(new DxValidationException("Missing access token from DigiLocker"));
    }

    boolean verified = "Y".equalsIgnoreCase(aadhaarAvailable);

    Future<Boolean> keycloakUpdate = verified
      ? keycloakUserService.setKycVerifiedTrueWithData(userId, userName, null)
      : keycloakUserService.setKycVerifiedFalse(userId);

    return keycloakUpdate.compose(success -> {
      if (!success) {
        String msg = "Failed to update KYC status in Keycloak for userId: " + userId;
        LOGGER.error(msg);
        return Future.failedFuture(new DxValidationException(msg));
      }
      LOGGER.info("KYC status set to {} for userId: {}", verified, userId);
      return Future.succeededFuture(new JsonObject().put("kycVerified", verified));
    });
  }

  private Future<JsonObject> fetchAadhaarDetails(String accessToken, String userId, String codeVerifier) {
    return webClient.getAbs(config.getString(CONFIG_AADHAAR_URL))
      .bearerTokenAuthentication(accessToken)
      .send()
      .compose(response -> {
        if (response.statusCode() != 200) {
          LOGGER.error("Failed to fetch Aadhaar details: statusCode={}, body={}",
            response.statusCode(), response.bodyAsString());
          return Future.failedFuture(new DxValidationException("Failed to fetch Aadhaar details"));
        }

        JsonObject aadhaarJson = parseKYCxml(response.bodyAsString());
        String txnId = aadhaarJson.getString("txn");
        aadhaarJson.remove("Pht");

        if (txnId == null || txnId.isBlank()) {
          LOGGER.error("Transaction ID missing in Aadhaar response: {}", aadhaarJson.encodePrettily());
          return Future.failedFuture(new DxValidationException("Missing transaction ID from Aadhaar response"));
        }

        UUID uuid = UUID.fromString(userId);

        return isValidTransaction(txnId, uuid).compose(isValid -> {
          if (!isValid) {
            LOGGER.error("Invalid transaction: {}", txnId);
            return Future.failedFuture(new DxValidationException("KYC not allowed: Aadhaar already linked to another user."));
          }

          KYCTransaction newTxn = new KYCTransaction(uuid, txnId, codeVerifier, false, null);
          return kycTransactionDAO.create(newTxn).map(created -> {
            LOGGER.info("KYC transaction created for userId: {}", userId);
            return aadhaarJson;
          });
        });
      });
  }

  private Future<Boolean> isValidTransaction(String txnId, UUID userId) {
    if (txnId == null || txnId.isBlank()) {
      LOGGER.error("Invalid transaction ID: {}", txnId);
      return Future.failedFuture("Invalid transaction ID");
    }

    Map<String, Object> txnMap = Map.of(Constants.TRANSACTION_ID, txnId);

    return kycTransactionDAO.getAllWithFilters(txnMap).compose(existingByTxn -> {
      if (existingByTxn != null && !existingByTxn.isEmpty()) {
        KYCTransaction txn = existingByTxn.get(0);
        if (!txn.userId().equals(userId) && txn.isConfirmed().equals(Boolean.TRUE)) {
          LOGGER.error("KYC Aadhaar already used by userId: {}", txn.userId());
          return Future.failedFuture(new DxValidationException("KYC failed: This Aadhaar is already linked to another user."));
        }
      }
      return Future.succeededFuture(true);
    });
  }



  @Override
  public Future<JsonObject> confirmKYCData(UUID userId, String codeVerifier, String userName) {
    Promise<JsonObject> promise = Promise.promise();

    return getAllKYCTransactions(userId, codeVerifier)
      .compose(kycTransaction -> {
        keycloakUserService.setKycVerifiedTrueWithData(userId, userName, kycTransaction.transactionId())
          .onComplete(kycResult -> {
            if (kycResult.succeeded() && kycResult.result()) {
              LOGGER.info("KYC verification successful for userId: {}", userId);
              promise.complete(kycTransaction.toJson());
            } else {
              String msg = "Failed to update KYC status in Keycloak for userId: " + userId;
              LOGGER.error(msg);
              promise.fail(new DxValidationException(msg));
            }
          });
        Map<String, Object> conditionMap = Map.of(Constants.USER_ID, userId.toString(),
          Constants.TRANSACTION_ID, kycTransaction.transactionId());
        Map<String, Object> updateFields = Map.of(Constants.CONFIRMED_FLAG, true,
          Constants.UPDATED_AT, LocalDateTime.now().toString());
        kycTransactionDAO.update(conditionMap, updateFields).onComplete(var -> {
          if (var.succeeded()) {
            LOGGER.info("KYC transaction updated for userId: {}", userId);
            promise.complete(kycTransaction.toJson());
          } else {
            LOGGER.error("Failed to update KYC transaction for userId: {}. Error: {}", userId, var.cause().getMessage());
            promise.complete(new JsonObject("Failed to update KYC transaction"));
          }
        });
        return promise.future();
      });
  }


  private Future<KYCTransaction> getAllKYCTransactions(UUID userId, String codeVerifier) {
    Promise<KYCTransaction> promise = Promise.promise();
    Map<String, Object> conditionMap = Map.of(Constants.USER_ID, userId.toString(),
      Constants.CODE_VERIFIER, codeVerifier);

    kycTransactionDAO.getAllWithFilters(conditionMap)
      .onComplete(ar -> {
        if (ar.failed()) {
          String msg = "KYC retrieval failed for " + userId + ": " + ar.cause().getMessage();
          LOGGER.error(msg);
          promise.fail(new DxValidationException(msg));
          return;
        }

        if (ar.result() == null || ar.result().isEmpty()) {
          String msg = "No KYC data found for userId: " + userId;
          LOGGER.warn(msg);
          promise.fail(new DxValidationException(msg));
          return;
        }

        boolean foundUnconfirmed = false;
        for (KYCTransaction txn : ar.result()) {
          if (Boolean.FALSE.equals(txn.isConfirmed())) {
            promise.complete(txn);
            foundUnconfirmed = true;
            break;
          }
        }
        if (!foundUnconfirmed) {
          promise.fail(new DxValidationException("KYC already confirmed for userId: " + userId));
        }

      });

    return promise.future();
  }

  private JsonObject parseKYCxml(String xmlData) {
    try {
      JSONObject jsonObject = XML.toJSONObject(xmlData);
      //System.out.println("jsonObject: " + jsonObject); // For debugging purposes
      // For debugging purposes
      Map<String, Object> map = jsonObject.toMap();

      JsonObject root = new JsonObject(map);
      JsonObject certificate = root.getJsonObject("Certificate");
      if (certificate == null) {
        throw new DxValidationException("Missing 'Certificate' field in Aadhaar XML.");
      }

      JsonObject certificateData = certificate.getJsonObject("CertificateData");
      if (certificateData == null) {
        throw new DxValidationException("Missing 'CertificateData' field in Aadhaar XML.");
      }

      JsonObject kycRes = certificateData.getJsonObject("KycRes");
      if (kycRes == null) {
        throw new DxValidationException("Missing 'KycRes' field in Aadhaar XML.");
      }

      JsonObject uidData = kycRes.getJsonObject("UidData");
      if (uidData == null) {
        throw new DxValidationException("Missing 'UidData' field in Aadhaar XML.");
      }

      // Extract only the required fields
      JsonObject result = new JsonObject();
      if (uidData.containsKey("uid")) {
        result.put("uid", uidData.getString("uid"));
      } else {
        throw new DxValidationException("Missing 'uid' field in Aadhaar XML.");
      }

      if (uidData.containsKey("Poa")) {
        result.put("Poa", uidData.getJsonObject("Poa"));
      }

      if (uidData.containsKey("LData")) {
        result.put("LData", uidData.getJsonObject("LData"));
      }

      if (uidData.containsKey("Poi")) {
        result.put("Poi", uidData.getJsonObject("Poi"));
      }

      if (uidData.containsKey("Pht")) {
        result.put("Pht", uidData.getString("Pht"));
      }

      if(kycRes.containsKey("txn")) {
        result.put("txn", kycRes.getString("txn"));
      }

      result.put("requestedAt", Instant.now().toString());

      return result;

    } catch (DxValidationException ve) {
      throw ve; // rethrow explicitly known validation exceptions
    } catch (Exception e) {
      LOGGER.error("Error while parsing Aadhaar XML data", e);
      throw new DxValidationException("Invalid Aadhaar XML format");
    }
  }
}
