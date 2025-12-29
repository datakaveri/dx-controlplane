package org.cdpg.dx.common;

import static org.cdpg.dx.aaa.apiserver.config.ApiConstants.*;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.validation.BodyProcessorException;
import io.vertx.ext.web.validation.ParameterProcessorException;
import io.vertx.ext.web.validation.RequestPredicateException;
import io.vertx.json.schema.ValidationException;
import org.apache.http.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.common.response.DxErrorResponse;
import org.cdpg.dx.common.response.DxErrorResponseNGSILD;
import org.cdpg.dx.common.util.ExceptionHttpStatusMapper;
import org.cdpg.dx.common.util.ThrowableUtils;

public class FailureHandler implements Handler<RoutingContext> {

  private static final Logger LOGGER = LogManager.getLogger(FailureHandler.class);
  private final URNGenerator urnGenerator;

  public FailureHandler(URNGenerator urnGenerator) {
    this.urnGenerator = urnGenerator;
  }

  public void handle(RoutingContext context) {
    String path = context.request().path();
    LOGGER.error("path : {} ", path);

    if (path.contains("/ngsi-ld/v1")) {
      ngsildErrorResponse(context);
    } else {
      nonNgsildErrorResponse(context);
    }
  }

  private void nonNgsildErrorResponse(RoutingContext context) {
    Throwable failure = context.failure();

    if (failure == null) {
      LOGGER.warn(
          "FailureHandler triggered without an actual Throwable. Possibly context.fail(statusCode) was used.");
      failure = new RuntimeException("Unknown server error");
    }
    LOGGER.info("FailureHandler: {}", failure.getClass());
    /* exceptions from OpenAPI specification*/
    if (failure instanceof ValidationException
        || failure instanceof BodyProcessorException
        || failure instanceof RequestPredicateException
        || failure instanceof ParameterProcessorException) {
      LOGGER.error("Validation error: {}", failure.getMessage(), failure);
      context
          .response()
          .putHeader(CONTENT_TYPE, APPLICATION_JSON)
          .putHeader(HEADER_ALLOW_ORIGIN, "*")
          .putHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
          .putHeader("Access-Control-Allow-Headers", "Authorization, Content-Type")
          .setStatusCode(HttpStatus.SC_BAD_REQUEST)
          .end(
              ResponseUtil.generateResponse(
                      HttpStatusCode.BAD_REQUEST,
                      urnGenerator.generateUrn(HttpStatusCode.BAD_REQUEST.getPath()),
                      "Missing or malformed request")
                  .toString());
      return;
    }

    HttpStatusCode statusCode = ExceptionHttpStatusMapper.map(failure);
    LOGGER.debug("FailureHandler() statusCode: {}", statusCode.getValue());

    // Log complete error with stack trace for diagnostics
    LOGGER.error("Unhandled error: {}", failure.getMessage(), failure);

    // Avoid leaking internal exception messages
    String safeDetail =
        ThrowableUtils.isSafeToExpose(failure)
            ? failure.getMessage()
            : "An unexpected error occurred";

    String urn = urnGenerator.generateUrn(statusCode.getPath());

    DxErrorResponse errorResponse =
        new DxErrorResponse(urn, statusCode.getDescription(), safeDetail);

    if (!context.response().ended()) {
      int status = statusCode.getValue();
      if (status < 400 || status > 599) {
        status = 500;
      }

      context
          .response()
          .putHeader("Content-Type", "application/json")
          .putHeader(HEADER_ALLOW_ORIGIN, "*")
          .putHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
          .putHeader("Access-Control-Allow-Headers", "Authorization, Content-Type")
          .setStatusCode(status)
          .end(errorResponse.toJson().encode());
    }
  }

  private void ngsildErrorResponse(RoutingContext context) {
    Throwable failure = context.failure();
    String instance = context.request().getHeader(HEADER_HOST);
    if (failure == null) {
      LOGGER.warn(
          "FailureHandlerNGSILD triggered without an actual Throwable. Possibly context.fail(statusCode) was used.");
      failure = new RuntimeException("Unknown server error");
    }
    LOGGER.info("FailureHandlerNGSILD: {}", failure.getClass());
    /* exceptions from OpenAPI specification*/
    if (failure instanceof ValidationException
        || failure instanceof BodyProcessorException
        || failure instanceof RequestPredicateException
        || failure instanceof ParameterProcessorException) {
      context
          .response()
          .putHeader(CONTENT_TYPE, APPLICATION_JSON)
          .putHeader(HEADER_ALLOW_ORIGIN, "*")
          .putHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
          .putHeader("Access-Control-Allow-Headers", "Authorization, Content-Type")
          .setStatusCode(HttpStatus.SC_BAD_REQUEST)
          .end(
              ResponseUtilNGSILD.generateResponse(
                      HttpStatusCode.BAD_REQUEST,
                      urnGenerator.generateUrn(HttpStatusCode.BAD_REQUEST.getPath()),
                      "Missing or malformed request",
                      instance)
                  .toString());
      return;
    }

    HttpStatusCode statusCode = ExceptionHttpStatusMapper.map(failure);
    LOGGER.debug("FailureHandlerNGSILD() statusCode: {}", statusCode.getValue());

    // Log complete error with stack trace for diagnostics
    LOGGER.error("error: {}", failure.getMessage(), failure);

    // Avoid leaking internal exception messages
    String safeDetail =
        ThrowableUtils.isSafeToExpose(failure)
            ? failure.getMessage()
            : "An unexpected error occurred";

    String urn = urnGenerator.generateUrn(statusCode.getPath());

    DxErrorResponseNGSILD errorResponse =
        new DxErrorResponseNGSILD(urn, statusCode.getDescription(), safeDetail, instance);

    if (!context.response().ended()) {
      int status = statusCode.getValue();
      if (status < 400 || status > 599) {
        status = 500;
      }

      context
          .response()
          .putHeader("Content-Type", "application/json")
          .putHeader(HEADER_ALLOW_ORIGIN, "*")
          .putHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
          .putHeader("Access-Control-Allow-Headers", "Authorization, Content-Type")
          .setStatusCode(status)
          .end(errorResponse.toJson().encode());
    }
  }
}
