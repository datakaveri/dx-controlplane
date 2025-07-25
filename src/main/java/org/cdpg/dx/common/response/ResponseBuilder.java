package org.cdpg.dx.common.response;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.cdpg.dx.common.HttpStatusCode;
import org.cdpg.dx.common.util.PaginationInfo;

import static org.cdpg.dx.common.config.CorsUtil.allowedOrigins;

public class ResponseBuilder {

  public static <T> DxResponse<T> success(String detail, T result, PaginationInfo pageInfo) {
    HttpStatusCode code = HttpStatusCode.SUCCESS;
    return new DxResponse<>(code.getUrn(), code.getDescription(), detail, result, pageInfo);
  }

  public static <T> DxResponse<T> success(String detail, T result) {
    HttpStatusCode code = HttpStatusCode.SUCCESS;
    return new DxResponse<>(code.getUrn(), code.getDescription(), detail, result, null);
  }

  public static DxResponse<Void> success(String detail) {
    return success(detail, null);
  }

  public static <T> void send(
          RoutingContext ctx, HttpStatusCode status, String detail, T result, PaginationInfo pageInfo) {
    if (status == HttpStatusCode.NO_CONTENT) {
      ctx.response().setStatusCode(status.getValue()).end();
      return;
    }
    DxResponse<T> response =
            new DxResponse<>(status.getUrn(), status.getDescription(), detail, result, pageInfo);
    String requestOrigin = ctx.request().getHeader("Origin");
    System.out.println("Request Origin: " + requestOrigin);
    System.out.println("Allowed Origins: " + allowedOrigins);
    System.out.println(allowedOrigins.contains(requestOrigin));
    if (allowedOrigins != null && requestOrigin != null && (allowedOrigins.contains(requestOrigin) || allowedOrigins.contains("*"))) {

      ctx.response()
              .putHeader("Content-Type", "application/json")
              .putHeader("Access-Control-Allow-Origin", requestOrigin)
              .putHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
              .putHeader("Access-Control-Allow-Headers", "Authorization, Content-Type")
              .setStatusCode(status.getValue())
              .end(JsonObject.mapFrom(response).encode());
    } else {
      ctx.response()
              .putHeader("Content-Type", "application/json")
              .putHeader("Access-Control-Allow-Origin", "*")
              .putHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
              .putHeader("Access-Control-Allow-Headers", "Authorization, Content-Type")
              .setStatusCode(status.getValue())
              .end(JsonObject.mapFrom(response).encode());
    }
  }

  public static void sendSuccess(RoutingContext ctx, String detail) {
    send(ctx, HttpStatusCode.SUCCESS, detail, null, null);
  }

  public static <R> void sendSuccess(RoutingContext ctx, R result) {
    send(ctx, HttpStatusCode.SUCCESS, null, result, null);
  }

  public static <T> void sendSuccess(RoutingContext ctx, T result, PaginationInfo pageInfo) {
    send(ctx, HttpStatusCode.SUCCESS, null, result, pageInfo);
  }

  public static <T> void sendCreated(RoutingContext ctx, String detail, T result) {
    send(ctx, HttpStatusCode.CREATED, detail, result, null);
  }

  public static void sendCreated(RoutingContext ctx, String detail) {
    send(ctx, HttpStatusCode.CREATED, detail, null, null);
  }

  public static void sendNoContent(RoutingContext ctx) {
    send(ctx, HttpStatusCode.NO_CONTENT, null, null, null);
  }

  public static void sendProcessing(RoutingContext ctx, String detail) {
    send(ctx, HttpStatusCode.PROCESSING, detail, null, null);
  }
}
