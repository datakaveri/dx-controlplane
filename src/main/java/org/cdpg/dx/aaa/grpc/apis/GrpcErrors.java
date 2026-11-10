package org.cdpg.dx.aaa.grpc.apis;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.vertx.core.json.JsonObject;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.exception.DxUnauthorizedException;
import org.cdpg.dx.common.exception.DxValidationException;

/**
 * Maps failures from the controlplane's services to the gRPC status codes the shared {@code
 * dx.apis.*} contracts define. Both servers of a contract (Java and Go) must return the same code
 * for the same failure, so this is the single place the Java side decides it.
 */
final class GrpcErrors {

  private GrpcErrors() {}

  static StatusRuntimeException invalid(String message) {
    return Status.INVALID_ARGUMENT.withDescription(message).asRuntimeException();
  }

  static StatusRuntimeException notFound(String message) {
    return Status.NOT_FOUND.withDescription(message).asRuntimeException();
  }

  /**
   * Converts any failure to a status. Policy and item code fail with a JSON message whose {@code
   * type} is an HTTP status ({@code generateErrorResponse}); that status decides the code, and its
   * {@code detail} becomes the description.
   */
  static StatusRuntimeException toStatus(Throwable err) {
    if (err instanceof StatusRuntimeException sre) {
      return sre;
    }
    if (isKeycloakNotFound(err)) {
      return notFound("not found");
    }
    if (err instanceof DxNotFoundException) {
      return notFound(err.getMessage());
    }
    if (err instanceof DxForbiddenException || err instanceof DxUnauthorizedException) {
      return Status.PERMISSION_DENIED.withDescription(err.getMessage()).asRuntimeException();
    }
    JsonObject problem = parseProblem(err.getMessage());
    if (problem != null) {
      return fromHttp(problem.getInteger("type"), problem.getString("detail", ""));
    }
    if (err instanceof DxValidationException || err instanceof IllegalArgumentException) {
      return invalid(err.getMessage());
    }
    return Status.INTERNAL.withDescription("internal error").asRuntimeException();
  }

  private static StatusRuntimeException fromHttp(int httpStatus, String detail) {
    Status status = forHttp(httpStatus);
    return status.withDescription(status == Status.INTERNAL ? "internal error" : detail)
        .asRuntimeException();
  }

  private static Status forHttp(int httpStatus) {
    switch (httpStatus) {
      case 400:
        return Status.INVALID_ARGUMENT;
      case 401:
      case 403:
        return Status.PERMISSION_DENIED;
      case 404:
        return Status.NOT_FOUND;
      case 409:
        return Status.ALREADY_EXISTS;
      default:
        return Status.INTERNAL;
    }
  }

  private static JsonObject parseProblem(String message) {
    if (message == null || !message.startsWith("{")) {
      return null;
    }
    try {
      JsonObject json = new JsonObject(message);
      return json.getValue("type") instanceof Number ? json : null;
    } catch (Exception e) {
      return null;
    }
  }

  /** True when the Keycloak admin client reported 404 anywhere in the cause chain. */
  static boolean isKeycloakNotFound(Throwable err) {
    for (Throwable t = err; t != null; t = t.getCause()) {
      if (t instanceof jakarta.ws.rs.NotFoundException) {
        return true;
      }
      if (t.getCause() == t) {
        break;
      }
    }
    return false;
  }
}
