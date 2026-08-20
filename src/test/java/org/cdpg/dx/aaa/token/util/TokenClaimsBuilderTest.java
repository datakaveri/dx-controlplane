package org.cdpg.dx.aaa.token.util;

import io.vertx.core.json.JsonObject;
import org.cdpg.dx.common.model.DxUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the `picture` attribute flows into JWT claims the same way the
 * existing social-account fields (twitter/linkedin/github) already do.
 */
class TokenClaimsBuilderTest {

  private DxUser baseUser(String picture) {
    return new DxUser(
      List.of("consumer"),
      null,
      null,
      UUID.randomUUID(),
      true,
      false,
      "Test User",
      "testuser",
      "Test",
      "User",
      "test@example.com",
      List.of(),
      new JsonObject(),
      null,
      new JsonObject(),
      "",
      "",
      "",
      picture,
      true,
      null,
      null,
      new JsonObject()
    );
  }

  @Test
  @DisplayName("picture claim is present in the token when set")
  void picturePresentWhenSet() {
    DxUser user = baseUser("https://staging.file-s3.adarv.iudx.io/assets/userid/photo.png");

    JsonObject claims = TokenClaimsBuilder.buildClaims(user, "issuer", "audience", 60);

    assertTrue(claims.containsKey("picture"));
    assertEquals("https://staging.file-s3.adarv.iudx.io/assets/userid/photo.png", claims.getString("picture"));
  }

  @Test
  @DisplayName("picture claim is omitted when blank, matching twitter/linkedin/github behavior")
  void pictureOmittedWhenBlank() {
    DxUser user = baseUser("");

    JsonObject claims = TokenClaimsBuilder.buildClaims(user, "issuer", "audience", 60);

    assertFalse(claims.containsKey("picture"));
  }

  @Test
  @DisplayName("picture claim is omitted when null")
  void pictureOmittedWhenNull() {
    DxUser user = baseUser(null);

    JsonObject claims = TokenClaimsBuilder.buildClaims(user, "issuer", "audience", 60);

    assertFalse(claims.containsKey("picture"));
  }
}
