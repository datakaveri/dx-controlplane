package org.cdpg.dx.aaa.grpc.apis;

import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.Status;
import io.vertx.core.json.JsonObject;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.junit.jupiter.api.Test;

class GrpcErrorsTest {

  private static Status.Code code(Throwable err) {
    return GrpcErrors.toStatus(err).getStatus().getCode();
  }

  private static RuntimeException problem(int httpStatus) {
    return new RuntimeException(
        new JsonObject().put("type", httpStatus).put("title", "t").put("detail", "d").encode());
  }

  @Test
  void mapsPolicyProblemStatuses() {
    assertThat(code(problem(400))).isEqualTo(Status.Code.INVALID_ARGUMENT);
    assertThat(code(problem(403))).isEqualTo(Status.Code.PERMISSION_DENIED);
    assertThat(code(problem(404))).isEqualTo(Status.Code.NOT_FOUND);
    assertThat(code(problem(409))).isEqualTo(Status.Code.ALREADY_EXISTS);
    assertThat(code(problem(500))).isEqualTo(Status.Code.INTERNAL);
  }

  @Test
  void keepsProblemDetailButHidesInternalDetail() {
    assertThat(GrpcErrors.toStatus(problem(409)).getStatus().getDescription()).isEqualTo("d");
    assertThat(GrpcErrors.toStatus(problem(500)).getStatus().getDescription())
        .isEqualTo("internal error");
  }

  @Test
  void mapsKeycloakNotFoundAnywhereInTheCauseChain() {
    Throwable wrapped = new RuntimeException("x", new jakarta.ws.rs.NotFoundException());
    assertThat(code(wrapped)).isEqualTo(Status.Code.NOT_FOUND);
  }

  @Test
  void mapsDxExceptionsAndArguments() {
    assertThat(code(new DxForbiddenException("no"))).isEqualTo(Status.Code.PERMISSION_DENIED);
    assertThat(code(new IllegalArgumentException("bad"))).isEqualTo(Status.Code.INVALID_ARGUMENT);
    assertThat(code(new IllegalStateException("boom"))).isEqualTo(Status.Code.INTERNAL);
  }
}
