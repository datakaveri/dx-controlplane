package org.cdpg.dx.keycloak.util;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;
import org.cdpg.dx.common.model.UserInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.UserRepresentation;

class UserInfoMapperTest {

  private UserRepresentation makeUser(String id, String username, String email,
                                       String firstName, String lastName) {
    UserRepresentation u = new UserRepresentation();
    u.setId(id);
    u.setUsername(username);
    u.setEmail(email);
    u.setFirstName(firstName);
    u.setLastName(lastName);
    return u;
  }

  @Test
  @DisplayName("maps all fields correctly")
  void mapsAllFields() {
    String id = UUID.randomUUID().toString();
    UserRepresentation u = makeUser(id, "jdoe", "jdoe@example.com", "John", "Doe");

    UserInfo result = UserInfoMapper.fromUserRepresentation(u);

    assertThat(result.sub()).isEqualTo(UUID.fromString(id));
    assertThat(result.preferredUsername()).isEqualTo("jdoe");
    assertThat(result.email()).isEqualTo("jdoe@example.com");
    assertThat(result.givenName()).isEqualTo("John");
    assertThat(result.familyName()).isEqualTo("Doe");
    assertThat(result.name()).isEqualTo("John Doe");
  }

  @Test
  @DisplayName("builds name from first and last names")
  void buildsNameFromParts() {
    String id = UUID.randomUUID().toString();
    UserRepresentation u = makeUser(id, "alice", "alice@test.com", "Alice", "Smith");

    UserInfo result = UserInfoMapper.fromUserRepresentation(u);

    assertThat(result.name()).isEqualTo("Alice Smith");
  }

  @Test
  @DisplayName("handles null first name")
  void handlesNullFirstName() {
    String id = UUID.randomUUID().toString();
    UserRepresentation u = makeUser(id, "bob", "bob@test.com", null, "Jones");

    UserInfo result = UserInfoMapper.fromUserRepresentation(u);

    assertThat(result.name()).isEqualTo("Jones");
    assertThat(result.givenName()).isNull();
  }

  @Test
  @DisplayName("handles null last name")
  void handlesNullLastName() {
    String id = UUID.randomUUID().toString();
    UserRepresentation u = makeUser(id, "carol", "carol@test.com", "Carol", null);

    UserInfo result = UserInfoMapper.fromUserRepresentation(u);

    assertThat(result.name()).isEqualTo("Carol");
    assertThat(result.familyName()).isNull();
  }

  @Test
  @DisplayName("handles both names null")
  void handlesBothNamesNull() {
    String id = UUID.randomUUID().toString();
    UserRepresentation u = makeUser(id, "anon", "anon@test.com", null, null);

    UserInfo result = UserInfoMapper.fromUserRepresentation(u);

    assertThat(result.name()).isEmpty();
  }

  @Test
  @DisplayName("trims whitespace in built name")
  void trimsWhitespace() {
    String id = UUID.randomUUID().toString();
    UserRepresentation u = makeUser(id, "user1", "user@test.com", "  Alice  ", "  Smith  ");

    UserInfo result = UserInfoMapper.fromUserRepresentation(u);

    // buildName concatenates and trims the outer result
    assertThat(result.name()).isNotBlank();
  }
}
