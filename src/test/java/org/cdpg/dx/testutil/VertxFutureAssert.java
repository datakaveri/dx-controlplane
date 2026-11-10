package org.cdpg.dx.testutil;

import io.vertx.core.Future;
import io.vertx.junit5.VertxTestContext;
import java.util.function.Consumer;

/**
 * Utility to reduce boilerplate when asserting on Vert.x Futures in tests.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * assertFutureSuccess(future, testContext, result -> {
 *     assertThat(result.name()).isEqualTo("expected");
 * });
 * }</pre>
 */
public final class VertxFutureAssert {

  private VertxFutureAssert() {}

  public static <T> void assertFutureSuccess(
      Future<T> future, VertxTestContext ctx, Consumer<T> assertions) {
    future.onComplete(
        ctx.succeeding(
            result ->
                ctx.verify(
                    () -> {
                      assertions.accept(result);
                      ctx.completeNow();
                    })));
  }

  public static <T> void assertFutureFailure(
      Future<T> future, VertxTestContext ctx, Consumer<Throwable> assertions) {
    future.onComplete(
        ctx.failing(
            err ->
                ctx.verify(
                    () -> {
                      assertions.accept(err);
                      ctx.completeNow();
                    })));
  }

  public static <T> void assertFutureFailureType(
      Future<T> future, VertxTestContext ctx, Class<? extends Throwable> expectedType) {
    assertFutureFailure(
        future,
        ctx,
        err -> {
          if (!expectedType.isInstance(err)) {
            throw new AssertionError(
                "Expected " + expectedType.getSimpleName() + " but got " + err.getClass().getSimpleName(),
                err);
          }
        });
  }
}
