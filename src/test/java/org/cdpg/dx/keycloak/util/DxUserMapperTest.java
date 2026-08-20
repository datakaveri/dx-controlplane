package org.cdpg.dx.keycloak.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.UserRepresentation;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the `picture` Keycloak attribute round-trips correctly into DxUser,
 * the same way the existing twitter_account/linkedin_account/github_account attributes do.
 */
class DxUserMapperTest {

  private UserRepresentation baseUserRep(Map<String, List<String>> attributes) {
    UserRepresentation rep = new UserRepresentation();
    rep.setId(UUID.randomUUID().toString());
    rep.setFirstName("Test");
    rep.setLastName("User");
    rep.setUsername("testuser");
    rep.setEmail("test@example.com");
    rep.setEmailVerified(true);
    rep.setEnabled(true);
    rep.setAttributes(attributes);
    return rep;
  }

  @Test
  @DisplayName("picture attribute is read into DxUser.picture() when present")
  void picturePresentInAttributes() {
    UserRepresentation rep = baseUserRep(
      Map.of("picture", List.of("https://staging.file-s3.adarv.iudx.io/assets/userid/photo.png"))
    );

    var dxUser = DxUserMapper.fromUserRepresentation(rep, List.of());

    assertEquals("https://staging.file-s3.adarv.iudx.io/assets/userid/photo.png", dxUser.picture());
  }

  @Test
  @DisplayName("picture defaults to empty string when attribute absent, matching twitter/linkedin/github behavior")
  void pictureDefaultsToEmptyWhenAbsent() {
    UserRepresentation rep = baseUserRep(Map.of());

    var dxUser = DxUserMapper.fromUserRepresentation(rep, List.of());

    assertEquals("", dxUser.picture());
  }
}
