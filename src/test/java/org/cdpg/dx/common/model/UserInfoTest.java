package org.cdpg.dx.common.model;

import static org.assertj.core.api.Assertions.*;

import io.vertx.core.json.JsonObject;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserInfoTest {

  @Test
  @DisplayName("toJson serializes all fields")
  void toJsonSerializesAll() {
    UUID id = UUID.randomUUID();
    UserInfo info = new UserInfo(id, "John Doe", "jdoe", "John", "Doe", "john@example.com");

    JsonObject json = info.toJson();

    assertThat(json.getString("sub")).isEqualTo(id.toString());
    assertThat(json.getString("name")).isEqualTo("John Doe");
    assertThat(json.getString("preferredUsername")).isEqualTo("jdoe");
    assertThat(json.getString("givenName")).isEqualTo("John");
    assertThat(json.getString("familyName")).isEqualTo("Doe");
    assertThat(json.getString("email")).isEqualTo("john@example.com");
  }

  @Test
  @DisplayName("toJson handles null sub")
  void toJsonNullSub() {
    UserInfo info = new UserInfo(null, "Name", "user", "First", "Last", "user@test.com");

    JsonObject json = info.toJson();

    assertThat(json.getString("sub")).isNull();
  }

  @Test
  @DisplayName("toJson handles null fields")
  void toJsonNullFields() {
    UUID id = UUID.randomUUID();
    UserInfo info = new UserInfo(id, null, null, null, null, null);

    JsonObject json = info.toJson();

    assertThat(json.getString("sub")).isEqualTo(id.toString());
    assertThat(json.getString("name")).isNull();
    assertThat(json.getString("email")).isNull();
  }

  @Test
  @DisplayName("record equality works")
  void recordEquality() {
    UUID id = UUID.randomUUID();
    UserInfo a = new UserInfo(id, "Name", "user", "First", "Last", "e@t.com");
    UserInfo b = new UserInfo(id, "Name", "user", "First", "Last", "e@t.com");

    assertThat(a).isEqualTo(b);
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
  }
}
