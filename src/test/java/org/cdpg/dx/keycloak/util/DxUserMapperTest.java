package org.cdpg.dx.keycloak.util;

import static org.assertj.core.api.Assertions.*;

import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.util.*;
import org.cdpg.dx.common.model.DxUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

class DxUserMapperTest {

  private UserRepresentation basicUser() {
    UserRepresentation u = new UserRepresentation();
    u.setId(UUID.randomUUID().toString());
    u.setUsername("jdoe");
    u.setEmail("jdoe@example.com");
    u.setEmailVerified(true);
    u.setEnabled(true);
    u.setFirstName("John");
    u.setLastName("Doe");
    u.setCreatedTimestamp(Instant.parse("2024-06-15T10:30:00Z").toEpochMilli());
    return u;
  }

  private RoleRepresentation role(String name) {
    RoleRepresentation r = new RoleRepresentation();
    r.setName(name);
    return r;
  }

  @Nested
  @DisplayName("fromUserRepresentation basic mapping")
  class BasicMapping {

    @Test
    void mapsBasicFieldsCorrectly() {
      UserRepresentation u = basicUser();
      List<RoleRepresentation> roles = List.of(role("consumer"), role("provider"));

      DxUser result = DxUserMapper.fromUserRepresentation(u, roles);

      assertThat(result.sub()).isEqualTo(UUID.fromString(u.getId()));
      assertThat(result.preferredUsername()).isEqualTo("jdoe");
      assertThat(result.email()).isEqualTo("jdoe@example.com");
      assertThat(result.emailVerified()).isTrue();
      assertThat(result.givenName()).isEqualTo("John");
      assertThat(result.familyName()).isEqualTo("Doe");
      assertThat(result.name()).isEqualTo("John Doe");
      assertThat(result.account_enabled()).isTrue();
    }

    @Test
    void mapsRoles() {
      UserRepresentation u = basicUser();
      List<RoleRepresentation> roles = List.of(role("consumer"), role("provider"), role("cos_admin"));

      DxUser result = DxUserMapper.fromUserRepresentation(u, roles);

      assertThat(result.roles()).containsExactly("consumer", "provider", "cos_admin");
    }

    @Test
    void mapsEmptyRoles() {
      UserRepresentation u = basicUser();

      DxUser result = DxUserMapper.fromUserRepresentation(u, List.of());

      assertThat(result.roles()).isEmpty();
    }

    @Test
    void mapsCreatedTimestamp() {
      UserRepresentation u = basicUser();

      DxUser result = DxUserMapper.fromUserRepresentation(u, List.of());

      assertThat(result.createdAt()).isNotNull();
      assertThat(result.createdAt().getYear()).isEqualTo(2024);
      assertThat(result.createdAt().getMonthValue()).isEqualTo(6);
    }

    @Test
    void handlesNullCreatedTimestamp() {
      UserRepresentation u = basicUser();
      u.setCreatedTimestamp(null);

      DxUser result = DxUserMapper.fromUserRepresentation(u, List.of());

      assertThat(result.createdAt()).isNull();
    }
  }

  @Nested
  @DisplayName("attribute mapping")
  class AttributeMapping {

    @Test
    void mapsOrganisationAttributes() {
      UserRepresentation u = basicUser();
      UUID orgId = UUID.randomUUID();
      u.setAttributes(Map.of(
          "organisation_id", List.of(orgId.toString()),
          "organisation_name", List.of("Test Org")
      ));

      DxUser result = DxUserMapper.fromUserRepresentation(u, List.of());

      assertThat(result.organisationId()).isEqualTo(orgId.toString());
      assertThat(result.organisationName()).isEqualTo("Test Org");
    }

    @Test
    void mapsKycAttributes() {
      UserRepresentation u = basicUser();
      u.setAttributes(Map.of(
          "kyc_verified", List.of("true"),
          "aadhaar_kyc_data", List.of("{\"kycStatus\":\"Active\"}")
      ));

      DxUser result = DxUserMapper.fromUserRepresentation(u, List.of());

      assertThat(result.kycVerified()).isTrue();
      assertThat(result.kycData().getString("kycStatus")).isEqualTo("Active");
    }

    @Test
    void handlesFalseKycVerified() {
      UserRepresentation u = basicUser();
      u.setAttributes(Map.of("kyc_verified", List.of("false")));

      DxUser result = DxUserMapper.fromUserRepresentation(u, List.of());

      assertThat(result.kycVerified()).isFalse();
    }

    @Test
    void mapsSocialAccountAttributes() {
      UserRepresentation u = basicUser();
      u.setAttributes(Map.of(
          "twitter_account", List.of("@johndoe"),
          "linkedin_account", List.of("linkedin.com/in/johndoe"),
          "github_account", List.of("github.com/johndoe")
      ));

      DxUser result = DxUserMapper.fromUserRepresentation(u, List.of());

      assertThat(result.twitter_account()).isEqualTo("@johndoe");
      assertThat(result.linkedin_account()).isEqualTo("linkedin.com/in/johndoe");
      assertThat(result.github_account()).isEqualTo("github.com/johndoe");
    }

    @Test
    void mapsDidAndAud() {
      UserRepresentation u = basicUser();
      UUID delegatorId = UUID.randomUUID();
      u.setAttributes(Map.of(
          "did", List.of(delegatorId.toString()),
          "aud", List.of("rs.example.com")
      ));

      DxUser result = DxUserMapper.fromUserRepresentation(u, List.of());

      assertThat(result.did()).isEqualTo(delegatorId.toString());
      assertThat(result.aud()).isEqualTo("rs.example.com");
    }

    @Test
    void handlesNullAttributes() {
      UserRepresentation u = basicUser();
      u.setAttributes(null);

      DxUser result = DxUserMapper.fromUserRepresentation(u, List.of());

      assertThat(result.organisationId()).isEmpty();
      assertThat(result.kycVerified()).isFalse();
      assertThat(result.scopes()).isEmpty();
    }

    @Test
    void handlesEmptyAttributeValues() {
      UserRepresentation u = basicUser();
      u.setAttributes(Map.of(
          "organisation_id", List.of(""),
          "aadhaar_kyc_data", List.of("")
      ));

      DxUser result = DxUserMapper.fromUserRepresentation(u, List.of());

      assertThat(result.organisationId()).isEmpty();
      assertThat(result.kycData()).isEqualTo(new io.vertx.core.json.JsonObject());
    }
  }

  @Nested
  @DisplayName("scopes parsing")
  class ScopesParsing {

    @Test
    void parsesJsonArrayScopes() {
      UserRepresentation u = basicUser();
      u.setAttributes(Map.of(
          "delegation_scope", List.of("[\"read\",\"write\",\"subscribe\"]")
      ));

      DxUser result = DxUserMapper.fromUserRepresentation(u, List.of());

      assertThat(result.scopes()).hasSize(3);
      assertThat(result.scopes().getList()).containsExactly("read", "write", "subscribe");
    }

    @Test
    void handlesEmptyScopes() {
      UserRepresentation u = basicUser();
      u.setAttributes(Map.of(
          "delegation_scope", List.of("[]")
      ));

      DxUser result = DxUserMapper.fromUserRepresentation(u, List.of());

      assertThat(result.scopes()).isEmpty();
    }

    @Test
    void handlesMissingScopes() {
      UserRepresentation u = basicUser();
      u.setAttributes(Map.of());

      DxUser result = DxUserMapper.fromUserRepresentation(u, List.of());

      assertThat(result.scopes()).isEmpty();
    }

    @Test
    void handlesNonJsonScopeString() {
      UserRepresentation u = basicUser();
      u.setAttributes(Map.of(
          "delegation_scope", List.of("single-scope-value")
      ));

      DxUser result = DxUserMapper.fromUserRepresentation(u, List.of());

      assertThat(result.scopes()).hasSize(1);
      assertThat(result.scopes().getString(0)).isEqualTo("single-scope-value");
    }

    @Test
    void handlesBlankScopeString() {
      UserRepresentation u = basicUser();
      u.setAttributes(Map.of(
          "delegation_scope", List.of("   ")
      ));

      DxUser result = DxUserMapper.fromUserRepresentation(u, List.of());

      assertThat(result.scopes()).isEmpty();
    }
  }
}
