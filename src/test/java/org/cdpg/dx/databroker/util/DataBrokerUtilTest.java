package org.cdpg.dx.databroker.util;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DataBrokerUtilTest {

  @Nested
  @DisplayName("Util.randomPassword")
  class RandomPasswordTests {

    @Test
    void generatesNonNullPassword() {
      String password = Util.randomPassword.get();
      assertThat(password).isNotNull();
    }

    @Test
    void generatesPasswordOfLength22() {
      String password = Util.randomPassword.get();
      assertThat(password).hasSize(22);
    }

    @Test
    void generatesUrlSafePassword() {
      String password = Util.randomPassword.get();
      // URL-safe Base64 characters only: A-Z, a-z, 0-9, -, _
      assertThat(password).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void generatesDifferentPasswords() {
      String p1 = Util.randomPassword.get();
      String p2 = Util.randomPassword.get();
      assertThat(p1).isNotEqualTo(p2);
    }
  }

  @Nested
  @DisplayName("Util.encodeValue")
  class EncodeValueTests {

    @Test
    void encodesSpecialCharacters() {
      assertThat(Util.encodeValue("hello world")).isEqualTo("hello+world");
    }

    @Test
    void encodesSlash() {
      assertThat(Util.encodeValue("a/b")).isEqualTo("a%2Fb");
    }

    @Test
    void encodesAmpersand() {
      assertThat(Util.encodeValue("a&b")).isEqualTo("a%26b");
    }

    @Test
    void returnsEmptyForNull() {
      assertThat(Util.encodeValue(null)).isEmpty();
    }

    @Test
    void leavesSimpleStringUnchanged() {
      assertThat(Util.encodeValue("hello")).isEqualTo("hello");
    }

    @Test
    void encodesVhostPath() {
      assertThat(Util.encodeValue("iudx-prod")).isEqualTo("iudx-prod");
    }
  }

  @Nested
  @DisplayName("Vhosts enum")
  class VhostsTests {

    @Test
    void hasProdVhost() {
      assertThat(Vhosts.IUDX_PROD.value).isEqualTo("prodVhost");
    }

    @Test
    void hasInternalVhost() {
      assertThat(Vhosts.IUDX_INTERNAL.value).isEqualTo("internalVhost");
    }

    @Test
    void hasExternalVhost() {
      assertThat(Vhosts.IUDX_EXTERNAL.value).isEqualTo("externalVhost");
    }

    @Test
    void hasThreeValues() {
      assertThat(Vhosts.values()).hasSize(3);
    }
  }

  @Nested
  @DisplayName("PermissionOpType enum")
  class PermissionOpTypeTests {

    @Test
    void addReadHasReadPermission() {
      assertThat(PermissionOpType.ADD_READ.permission).isEqualTo("read");
    }

    @Test
    void addWriteHasWritePermission() {
      assertThat(PermissionOpType.ADD_WRITE.permission).isEqualTo("write");
    }

    @Test
    void deleteReadHasReadPermission() {
      assertThat(PermissionOpType.DELETE_READ.permission).isEqualTo("read");
    }

    @Test
    void deleteWriteHasWritePermission() {
      assertThat(PermissionOpType.DELETE_WRITE.permission).isEqualTo("write");
    }

    @Test
    void hasFourValues() {
      assertThat(PermissionOpType.values()).hasSize(4);
    }
  }
}
