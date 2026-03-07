package org.cdpg.dx.common.model;

import static org.assertj.core.api.Assertions.*;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DxUserTest {

  private DxUser sampleUser() {
    return new DxUser(
        List.of("consumer", "provider"),
        "org-123",
        "Test Organisation",
        UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890"),
        true,
        true,
        "John Doe",
        "jdoe",
        "John",
        "Doe",
        "john@example.com",
        List.of("cos_admin"),
        new JsonObject().put("name", "Org"),
        LocalDateTime.of(2024, 6, 15, 10, 30, 0),
        new JsonObject().put("kycStatus", "Active"),
        "@johndoe",
        "linkedin.com/johndoe",
        "github.com/johndoe",
        true,
        "delegator-id",
        "rs.example.com",
        new JsonArray().add("read").add("write"));
  }

  @Nested
  @DisplayName("toJson")
  class ToJson {

    @Test
    void serializesAllFields() {
      DxUser user = sampleUser();
      JsonObject json = user.toJson();

      assertThat(json.getJsonArray("roles")).containsExactly("consumer", "provider");
      assertThat(json.getString("organisationId")).isEqualTo("org-123");
      assertThat(json.getString("organisationName")).isEqualTo("Test Organisation");
      assertThat(json.getString("sub")).isEqualTo("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
      assertThat(json.getBoolean("emailVerified")).isTrue();
      assertThat(json.getBoolean("kycVerified")).isTrue();
      assertThat(json.getString("name")).isEqualTo("John Doe");
      assertThat(json.getString("preferredUsername")).isEqualTo("jdoe");
      assertThat(json.getString("givenName")).isEqualTo("John");
      assertThat(json.getString("familyName")).isEqualTo("Doe");
      assertThat(json.getString("email")).isEqualTo("john@example.com");
      assertThat(json.getJsonArray("pending_roles")).containsExactly("cos_admin");
      assertThat(json.getJsonObject("organisation").getString("name")).isEqualTo("Org");
      assertThat(json.getString("createdAt")).isNotNull();
      assertThat(json.getJsonObject("kycInformation").getString("kycStatus")).isEqualTo("Active");
      assertThat(json.getString("twitter_account")).isEqualTo("@johndoe");
      assertThat(json.getString("linkedin_account")).isEqualTo("linkedin.com/johndoe");
      assertThat(json.getString("github_account")).isEqualTo("github.com/johndoe");
      assertThat(json.getBoolean("account_enabled")).isTrue();
      assertThat(json.getString("did")).isEqualTo("delegator-id");
      assertThat(json.getString("aud")).isEqualTo("rs.example.com");
      assertThat(json.getJsonArray("delegation_scope")).containsExactly("read", "write");
    }

    @Test
    void handlesNullRoles() {
      DxUser user = new DxUser(
          null, null, null, null, false, false, null, null, null, null, null,
          null, null, null, null, null, null, null, null, null, null, null);
      JsonObject json = user.toJson();

      assertThat(json.getJsonArray("roles")).isEmpty();
      assertThat(json.getString("sub")).isNull();
      assertThat(json.getString("createdAt")).isNull();
    }

    @Test
    void handlesNullCreatedAt() {
      DxUser user = new DxUser(
          List.of(), "", "", UUID.randomUUID(), false, false, "", "", "", "", "",
          List.of(), new JsonObject(), null, new JsonObject(), "", "", "", true, "", "", new JsonArray());
      JsonObject json = user.toJson();

      assertThat(json.getString("createdAt")).isNull();
    }
  }

  @Nested
  @DisplayName("withPendingRoles")
  class WithPendingRoles {

    @Test
    void replacesPendingRolesAndOrganisation() {
      DxUser original = sampleUser();
      List<String> newPendingRoles = List.of("delegate", "compute");
      JsonObject newOrg = new JsonObject().put("name", "New Org");

      DxUser result = DxUser.withPendingRoles(original, newPendingRoles, newOrg);

      assertThat(result.pendingRoles()).containsExactly("delegate", "compute");
      assertThat(result.organisation().getString("name")).isEqualTo("New Org");
      // Other fields preserved
      assertThat(result.sub()).isEqualTo(original.sub());
      assertThat(result.roles()).isEqualTo(original.roles());
      assertThat(result.email()).isEqualTo(original.email());
      assertThat(result.kycVerified()).isEqualTo(original.kycVerified());
      assertThat(result.createdAt()).isEqualTo(original.createdAt());
      assertThat(result.scopes()).isEqualTo(original.scopes());
    }

    @Test
    void preservesAllOriginalFieldsExceptPendingRolesAndOrg() {
      DxUser original = sampleUser();
      DxUser result = DxUser.withPendingRoles(original, List.of(), new JsonObject());

      assertThat(result.organisationId()).isEqualTo(original.organisationId());
      assertThat(result.organisationName()).isEqualTo(original.organisationName());
      assertThat(result.emailVerified()).isEqualTo(original.emailVerified());
      assertThat(result.name()).isEqualTo(original.name());
      assertThat(result.preferredUsername()).isEqualTo(original.preferredUsername());
      assertThat(result.givenName()).isEqualTo(original.givenName());
      assertThat(result.familyName()).isEqualTo(original.familyName());
      assertThat(result.kycData()).isEqualTo(original.kycData());
      assertThat(result.twitter_account()).isEqualTo(original.twitter_account());
      assertThat(result.linkedin_account()).isEqualTo(original.linkedin_account());
      assertThat(result.github_account()).isEqualTo(original.github_account());
      assertThat(result.account_enabled()).isEqualTo(original.account_enabled());
      assertThat(result.did()).isEqualTo(original.did());
      assertThat(result.aud()).isEqualTo(original.aud());
    }
  }

  @Nested
  @DisplayName("record accessors")
  class RecordAccessors {

    @Test
    void accessorMethodsWork() {
      UUID id = UUID.randomUUID();
      JsonArray scopes = new JsonArray().add("scope1");
      DxUser user = new DxUser(
          List.of("consumer"), "org-1", "Org Name", id, true, false,
          "Full Name", "username", "First", "Last", "email@test.com",
          List.of(), new JsonObject(), null, new JsonObject(), "", "", "",
          true, "did-val", "aud-val", scopes);

      assertThat(user.sub()).isEqualTo(id);
      assertThat(user.email()).isEqualTo("email@test.com");
      assertThat(user.givenName()).isEqualTo("First");
      assertThat(user.familyName()).isEqualTo("Last");
      assertThat(user.did()).isEqualTo("did-val");
      assertThat(user.aud()).isEqualTo("aud-val");
      assertThat(user.scopes()).isEqualTo(scopes);
    }
  }
}
