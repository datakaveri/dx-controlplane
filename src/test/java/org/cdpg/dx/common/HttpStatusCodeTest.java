package org.cdpg.dx.common;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HttpStatusCodeTest {

  @Test
  @DisplayName("getByValue returns correct enum")
  void getByValue_returnsCorrect() {
    assertThat(HttpStatusCode.getByValue(200)).isEqualTo(HttpStatusCode.SUCCESS);
    assertThat(HttpStatusCode.getByValue(400)).isEqualTo(HttpStatusCode.BAD_REQUEST);
    assertThat(HttpStatusCode.getByValue(404)).isEqualTo(HttpStatusCode.NOT_FOUND);
    assertThat(HttpStatusCode.getByValue(500)).isEqualTo(HttpStatusCode.INTERNAL_SERVER_ERROR);
  }

  @Test
  @DisplayName("getByValue throws for unknown code")
  void getByValue_throwsForUnknown() {
    assertThatThrownBy(() -> HttpStatusCode.getByValue(999))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("999");
  }

  @Test
  @DisplayName("getValue returns numeric code")
  void getValue() {
    assertThat(HttpStatusCode.SUCCESS.getValue()).isEqualTo(200);
    assertThat(HttpStatusCode.CREATED.getValue()).isEqualTo(201);
    assertThat(HttpStatusCode.NO_CONTENT.getValue()).isEqualTo(204);
    assertThat(HttpStatusCode.UNAUTHORIZED.getValue()).isEqualTo(401);
    assertThat(HttpStatusCode.FORBIDDEN.getValue()).isEqualTo(403);
    assertThat(HttpStatusCode.CONFLICT.getValue()).isEqualTo(409);
  }

  @Test
  @DisplayName("getDescription returns human-readable text")
  void getDescription() {
    assertThat(HttpStatusCode.SUCCESS.getDescription()).isEqualTo("Success");
    assertThat(HttpStatusCode.BAD_REQUEST.getDescription()).isEqualTo("Bad Request");
    assertThat(HttpStatusCode.NOT_FOUND.getDescription()).isEqualTo("Not Found");
    assertThat(HttpStatusCode.INTERNAL_SERVER_ERROR.getDescription()).isEqualTo("Internal Server Error");
  }

  @Test
  @DisplayName("getPath returns path string")
  void getPath() {
    assertThat(HttpStatusCode.SUCCESS.getPath()).isEqualTo("success");
    assertThat(HttpStatusCode.BAD_REQUEST.getPath()).isEqualTo("badRequest");
    assertThat(HttpStatusCode.NOT_FOUND.getPath()).isEqualTo("notFound");
    assertThat(HttpStatusCode.FORBIDDEN.getPath()).isEqualTo("forbidden");
  }

  @Test
  @DisplayName("toString formats as code + description")
  void toStringFormat() {
    assertThat(HttpStatusCode.SUCCESS.toString()).isEqualTo("200 Success");
    assertThat(HttpStatusCode.NOT_FOUND.toString()).isEqualTo("404 Not Found");
  }

  @Test
  @DisplayName("FORBIDDEN variants have different paths")
  void forbiddenVariants() {
    assertThat(HttpStatusCode.FORBIDDEN.getPath()).isEqualTo("forbidden");
    assertThat(HttpStatusCode.FORBIDDEN_NO_ACCESS.getPath()).isEqualTo("no-access");
    assertThat(HttpStatusCode.FORBIDDEN_ACCESS_PENDING.getPath()).isEqualTo("access-pending");
    assertThat(HttpStatusCode.FORBIDDEN_ACCESS_REJECTED.getPath()).isEqualTo("access-rejected");
    // All share 403 status
    assertThat(HttpStatusCode.FORBIDDEN.getValue()).isEqualTo(403);
    assertThat(HttpStatusCode.FORBIDDEN_NO_ACCESS.getValue()).isEqualTo(403);
  }
}
