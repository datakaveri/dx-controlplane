package org.cdpg.dx.common;

import static org.cdpg.dx.aaa.accessRequest.config.Constants.*;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.validation.BodyProcessorException;
import io.vertx.ext.web.validation.ParameterProcessorException;
import io.vertx.ext.web.validation.RequestPredicateException;
import io.vertx.json.schema.ValidationException;
import io.vertx.serviceproxy.HelperUtils;
import org.apache.http.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.common.config.CorsUtil;
import org.cdpg.dx.common.response.DxErrorResponse;
import org.cdpg.dx.common.util.ExceptionHttpStatusMapper;
import org.cdpg.dx.common.util.ThrowableUtils;

import java.time.LocalDateTime;

public class FailureHandler implements Handler<RoutingContext> {

  private static final Logger LOGGER = LogManager.getLogger(FailureHandler.class);

  public void handle(RoutingContext context) {
    Throwable failure = context.failure();
//    failure.printStackTrace();
    LOGGER.info("FailureHandler: {}", failure.getClass());
    LOGGER.error("error: {}", HelperUtils.convertStackTrace(failure));
    if (failure == null) {
      LOGGER.warn("FailureHandler triggered without an actual Throwable. Possibly context.fail(statusCode) was used.");
      failure = new RuntimeException("Unknown server error");
    }
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
              ResponseUtil.generateResponse(
                      HttpStatusCode.BAD_REQUEST,
                      ResponseUrn.BAD_REQUEST_URN,
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

    DxErrorResponse errorResponse =
        new DxErrorResponse(statusCode.getUrn(), statusCode.getDescription(), safeDetail);

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

