package org.cdpg.dx.auth.authorization.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DxRoleTest {

  @Nested
  @DisplayName("fromString")
  class FromStringTests {

    @ParameterizedTest(name = "fromString(\"{0}\") should return {1}")
    @CsvSource({
      "delegate,    DELEGATE",
      "consumer,    CONSUMER",
      "provider,    PROVIDER",
      "cos_admin,   COS_ADMIN",
      "org_admin,   ORG_ADMIN",
      "consumerDelegate, CONSUMER_DELEGATE",
      "providerDelegate, PROVIDER_DELEGATE",
      "compute,     COMPUTE"
    })
    @DisplayName("should return correct DxRole for valid role strings")
    void validRoleName_returnsCorrectEnum(String input, String expectedEnumName) {
      Optional<DxRole> result = DxRole.fromString(input);

      assertThat(result).isPresent();
      assertThat(result.get()).isEqualTo(DxRole.valueOf(expectedEnumName));
    }

    @Test
    @DisplayName("should return Optional.empty() for an invalid role name")
    void invalidRoleName_returnsEmpty() {
      Optional<DxRole> result = DxRole.fromString("nonexistent_role");

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should return Optional.empty() for null input")
    void nullInput_returnsEmpty() {
      Optional<DxRole> result = DxRole.fromString(null);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should return Optional.empty() for empty string input")
    void emptyStringInput_returnsEmpty() {
      Optional<DxRole> result = DxRole.fromString("");

      assertThat(result).isEmpty();
    }

    @ParameterizedTest(name = "fromString(\"{0}\") should resolve case-insensitively to {1}")
    @CsvSource({
      "CONSUMER,            CONSUMER",
      "Consumer,            CONSUMER",
      "PROVIDER,            PROVIDER",
      "Provider,            PROVIDER",
      "COS_ADMIN,           COS_ADMIN",
      "Cos_Admin,           COS_ADMIN",
      "DELEGATE,            DELEGATE",
      "Delegate,            DELEGATE",
      "ConsumerDelegate,    CONSUMER_DELEGATE",
      "CONSUMERDELEGATE,    CONSUMER_DELEGATE",
      "PROVIDERDELEGATE,    PROVIDER_DELEGATE",
      "COMPUTE,             COMPUTE",
    })
    @DisplayName("should be case insensitive")
    void caseInsensitive_returnsCorrectEnum(String input, String expectedEnumName) {
      Optional<DxRole> result = DxRole.fromString(input);

      assertThat(result).isPresent();
      assertThat(result.get()).isEqualTo(DxRole.valueOf(expectedEnumName));
    }

    @Test
    @DisplayName("should handle uppercase of actual role string values")
    void uppercaseRoleStringValues_resolved() {
      // "consumer" -> CONSUMER; "CONSUMER" should also resolve
      assertThat(DxRole.fromString("CONSUMER")).isPresent().contains(DxRole.CONSUMER);
      assertThat(DxRole.fromString("PROVIDER")).isPresent().contains(DxRole.PROVIDER);
      assertThat(DxRole.fromString("COS_ADMIN")).isPresent().contains(DxRole.COS_ADMIN);
      assertThat(DxRole.fromString("ORG_ADMIN")).isPresent().contains(DxRole.ORG_ADMIN);
      assertThat(DxRole.fromString("DELEGATE")).isPresent().contains(DxRole.DELEGATE);
      assertThat(DxRole.fromString("COMPUTE")).isPresent().contains(DxRole.COMPUTE);
    }

    @Test
    @DisplayName("should handle mixed case of actual role string values")
    void mixedCaseRoleStringValues_resolved() {
      assertThat(DxRole.fromString("Consumer")).isPresent().contains(DxRole.CONSUMER);
      assertThat(DxRole.fromString("Provider")).isPresent().contains(DxRole.PROVIDER);
      assertThat(DxRole.fromString("Delegate")).isPresent().contains(DxRole.DELEGATE);
      assertThat(DxRole.fromString("ConsumerDelegate"))
          .isPresent()
          .contains(DxRole.CONSUMER_DELEGATE);
      assertThat(DxRole.fromString("ProviderDelegate"))
          .isPresent()
          .contains(DxRole.PROVIDER_DELEGATE);
    }
  }

  @Nested
  @DisplayName("getRole")
  class GetRoleTests {

    @Test
    @DisplayName("should return the correct string value for each role")
    void returnsCorrectStringValue() {
      assertThat(DxRole.DELEGATE.getRole()).isEqualTo("delegate");
      assertThat(DxRole.CONSUMER.getRole()).isEqualTo("consumer");
      assertThat(DxRole.PROVIDER.getRole()).isEqualTo("provider");
      assertThat(DxRole.COS_ADMIN.getRole()).isEqualTo("cos_admin");
      assertThat(DxRole.ORG_ADMIN.getRole()).isEqualTo("org_admin");
      assertThat(DxRole.CONSUMER_DELEGATE.getRole()).isEqualTo("consumerDelegate");
      assertThat(DxRole.PROVIDER_DELEGATE.getRole()).isEqualTo("providerDelegate");
      assertThat(DxRole.COMPUTE.getRole()).isEqualTo("compute");
    }
  }

  @Nested
  @DisplayName("toString")
  class ToStringTests {

    @Test
    @DisplayName("should return the same value as getRole()")
    void toStringMatchesGetRole() {
      for (DxRole role : DxRole.values()) {
        assertThat(role.toString()).isEqualTo(role.getRole());
      }
    }
  }

  @Nested
  @DisplayName("values enumeration")
  class ValuesTests {

    @Test
    @DisplayName("should contain exactly 8 role constants")
    void correctNumberOfRoles() {
      assertThat(DxRole.values()).hasSize(8);
    }

    @Test
    @DisplayName("should contain all expected role constants")
    void containsAllExpectedRoles() {
      assertThat(DxRole.values())
          .containsExactlyInAnyOrder(
              DxRole.DELEGATE,
              DxRole.CONSUMER,
              DxRole.PROVIDER,
              DxRole.COS_ADMIN,
              DxRole.ORG_ADMIN,
              DxRole.CONSUMER_DELEGATE,
              DxRole.PROVIDER_DELEGATE,
              DxRole.COMPUTE);
    }
  }
}
