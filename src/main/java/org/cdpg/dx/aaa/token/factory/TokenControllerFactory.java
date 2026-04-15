package org.cdpg.dx.aaa.token.factory;

import static org.cdpg.dx.aaa.common.Constants.DOC_INDEX;
import static org.cdpg.dx.database.elastic.util.Constants.APD_URL;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyUse;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.web.client.WebClient;
import java.security.KeyStore;
import org.cdpg.dx.aaa.clientSecret.dao.ClientcredetialDao;
import org.cdpg.dx.aaa.clientSecret.dao.impl.ClientcredetialDaoImpl;
import org.cdpg.dx.aaa.clientSecret.service.ClientcredetialService;
import org.cdpg.dx.aaa.clientSecret.service.ClientcredetialServiceImpl;
import org.cdpg.dx.aaa.delegation.DelegationAccessEvaluator;
import org.cdpg.dx.aaa.delegation.service.DelegationService;
import org.cdpg.dx.aaa.item.service.ItemService;
import org.cdpg.dx.aaa.item.service.ItemServiceImpl;
import org.cdpg.dx.aaa.token.controller.TokenController;
import org.cdpg.dx.aaa.token.service.TokenService;
import org.cdpg.dx.aaa.token.service.impl.TokenServiceImpl;
import org.cdpg.dx.acl.policy.dao.PolicyDao;
import org.cdpg.dx.common.URNGenerator;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
import org.cdpg.dx.keycloak.service.KeycloakUserServiceImpl;

public class TokenControllerFactory {

  public static TokenController create(
      PostgresService pgService,
      ElasticsearchService elasticsearchService,
      JsonObject config,
      Vertx vertx,
      WebClient webClient,
      PolicyDao policyDao,
      DelegationService delegationService,
      URNGenerator urnGenerator) {

    KeycloakUserService keycloakUserService = new KeycloakUserServiceImpl(config);
    ClientcredetialDao clientcredetialDao = new ClientcredetialDaoImpl(pgService);
    ClientcredetialService clientcredetialService =
        new ClientcredetialServiceImpl(clientcredetialDao);

    String keystorePath = config.getString("keystorePath");
    String keystorePassword = config.getString("keystorePassword");
    int tokenExpirationMinutes = config.getInteger("tokenExpirationMinutes", 60);
    String isssuer = config.getString("cosDomain", "");

    JWTAuth provider = jwtInitConfig(keystorePath, keystorePassword, vertx);

    ItemService itemService =
        new ItemServiceImpl(
            elasticsearchService,
            keycloakUserService,
            pgService,
            policyDao,
            webClient,
            config.getString(DOC_INDEX),
            config.getString(APD_URL));

    DelegationAccessEvaluator delegationAccessEvaluator =
        new DelegationAccessEvaluator(delegationService, itemService, keycloakUserService);

    TokenService tokenService =
        new TokenServiceImpl(
            provider,
            keycloakUserService,
            clientcredetialService,
            itemService,
            isssuer,
            tokenExpirationMinutes,
            delegationService,
            delegationAccessEvaluator,
            vertx);

    return new TokenController(tokenService, urnGenerator);
  }

  /**
   * Creates a JWTAuth provider using the EC private key from the keystore as a JWK.
   * The JWK includes the kid (SHA-256 thumbprint), so Vert.x will automatically
   * embed kid in the generated token headers.
   */
  public static JWTAuth jwtInitConfig(String keystorePath, String keystorePassword, Vertx vertx) {
    try {
      JksOptions jksOpts = new JksOptions().setPath(keystorePath).setPassword(keystorePassword);
      KeyStore ks = jksOpts.loadKeyStore(vertx);
      ECKey ecKey = ECKey.load(ks, "jwt-key-1", keystorePassword.toCharArray());
      String kid = ecKey.computeThumbprint().toString();

      ECKey signingJwk =
          new ECKey.Builder(Curve.P_256, ecKey.toECPublicKey())
              .privateKey(ecKey.toECPrivateKey())
              .keyUse(KeyUse.SIGNATURE)
              .algorithm(JWSAlgorithm.ES256)
              .keyID(kid)
              .build();

      JsonObject jwk = new JsonObject(signingJwk.toJSONObject());
      return JWTAuth.create(vertx, new JWTAuthOptions().addJwk(jwk));
    } catch (Exception e) {
      throw new RuntimeException("Failed to initialize JWT auth from keystore", e);
    }
  }
}
