package org.cdpg.dx.aaa.token.util;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.time.Instant;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.item.service.ItemServiceImpl;
import org.cdpg.dx.common.model.DxUser;

public class TokenClaimsBuilder {

  private static final Logger LOGGER = LogManager.getLogger(TokenClaimsBuilder.class);

  public static  JsonObject buildClaims(DxUser dxUser, String iss, String aud, long expiryMinutes) {
    JsonObject claims = new JsonObject();


    Instant now = Instant.now();
    long nowEpoch = now.getEpochSecond();
    long expEpoch = now.plusSeconds(expiryMinutes * 60).getEpochSecond();

    LOGGER.info("dxroles: {}",dxUser.roles());

    //**************************************************************************************

    JsonObject delegationAccess = new JsonObject();
    JsonArray scopesObj = dxUser.scopes();

    LOGGER.info("scopes array :{}",scopesObj.encode());
    delegationAccess.put("roles", scopesObj);
    claims.put("delegation_access",delegationAccess);
    LOGGER.info(" delegation_access :{}",delegationAccess);

    //***************************************************************************************

    JsonObject realmAccess = new JsonObject();
    JsonArray rolesArray = new JsonArray();

    if (dxUser.roles() != null && !dxUser.roles().isEmpty()) {


      for (String role : dxUser.roles()) {
          rolesArray.add(role);
        }
      }

      LOGGER.info("roles array :{}",rolesArray);
      realmAccess.put("roles", rolesArray);
      claims.put("realm_access",realmAccess);
      LOGGER.info(" realm_access :{}",realmAccess);


    //**********************************************************************************************


    // Standard OIDC / JWT claims
    claims.put("sub", dxUser.sub().toString());
    claims.put("iss", iss);
    claims.put("aud", aud);
    claims.put("exp", expEpoch);
    claims.put("iat", nowEpoch);

    // Keycloak-like structure
//    if (dxUser.roles() != null && !dxUser.roles().isEmpty()) {
//      JsonObject realmAccess = new JsonObject().put("roles", dxUser.roles());
//      claims.put("realm_access", realmAccess);
//    }

    // Resource access (Keycloak-like)
//    if (dxUser.roles() != null && !dxUser.roles().isEmpty()) {
//      JsonObject resourceAccess =
//          new JsonObject().put("account", new JsonObject().put("roles", dxUser.roles()));
//      claims.put("resource_access", resourceAccess);
//    }


    // Keycloak-like user fields (only if present)
    putIfNotBlank(claims, "preferred_username", dxUser.preferredUsername());
    putIfNotBlank(claims, "name", dxUser.name());
    putIfNotBlank(claims, "given_name", dxUser.givenName());
    putIfNotBlank(claims, "family_name", dxUser.familyName());
    putIfNotBlank(claims, "email", dxUser.email());

    claims.put("email_verified", dxUser.emailVerified());
    claims.put(
        "account_enabled", dxUser.account_enabled() != null ? dxUser.account_enabled() : true);

    // Organisation info (only if present)
    putIfNotBlank(claims, "organisation_id", dxUser.organisationId());
    putIfNotBlank(claims, "organisation_name", dxUser.organisationName());
    if (dxUser.organisation() != null && !dxUser.organisation().isEmpty()) {
      claims.put("organisation", dxUser.organisation());
    }

    // Social accounts (only if present)
    putIfNotBlank(claims, "twitter_account", dxUser.twitter_account());
    putIfNotBlank(claims, "linkedin_account", dxUser.linkedin_account());
    putIfNotBlank(claims, "github_account", dxUser.github_account());

    // Pending roles (if any)
    if (dxUser.pendingRoles() != null && !dxUser.pendingRoles().isEmpty()) {
      claims.put("pending_roles", dxUser.pendingRoles());
    }
    // KYC info
    claims.put("kycVerified", dxUser.kycVerified());
    if (dxUser.kycData() != null && !dxUser.kycData().isEmpty()) {
      claims.put("kycInformation", dxUser.kycData());
    }

    return claims;
  }

  private static void putIfNotBlank(JsonObject json, String key, String value) {
    if (value != null && !value.isBlank()) {
      json.put(key, value);
    }
  }
}
