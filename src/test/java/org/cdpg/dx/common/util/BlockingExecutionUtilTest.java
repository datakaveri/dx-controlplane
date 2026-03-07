package org.cdpg.dx.common.util;

import static org.assertj.core.api.Assertions.*;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BlockingExecutionUtilTest {

  @BeforeEach
  void reset() {
    try {
      var clazz = BlockingExecutionUtil.class;
      var resetMethod = clazz.getDeclaredMethod("reset");
      resetMethod.setAccessible(true);
      resetMethod.invoke(null);
    } catch (Exception ignored) {
    }
  }

  @Test
  @DisplayName("runBlocking executes supplier on worker thread")
  void runBlockingExecutes(Vertx vertx, VertxTestContext ctx) throws Exception {
    BlockingExecutionUtil.initialize(vertx);

    BlockingExecutionUtil.<String>runBlocking(() -> "hello")
        .onComplete(
            ctx.succeeding(
                result ->
                    ctx.verify(
                        () -> {
                          assertThat(result).isEqualTo("hello");
                          ctx.completeNow();
                        })));

    ctx.awaitCompletion(5, TimeUnit.SECONDS);
  }

  @Test
  @DisplayName("runBlocking propagates exception as failed future")
  void runBlockingPropagatesException(Vertx vertx, VertxTestContext ctx) throws Exception {
    BlockingExecutionUtil.initialize(vertx);

    BlockingExecutionUtil.<String>runBlocking(
            () -> {
              throw new RuntimeException("test error");
            })
        .onComplete(
            ctx.failing(
                err ->
                    ctx.verify(
                        () -> {
                          assertThat(err).isInstanceOf(RuntimeException.class);
                          assertThat(err.getMessage()).isEqualTo("test error");
                          ctx.completeNow();
                        })));

    ctx.awaitCompletion(5, TimeUnit.SECONDS);
  }

  @Test
  @DisplayName("runBlocking returns computed value from supplier")
  void runBlockingReturnsComputedValue(Vertx vertx, VertxTestContext ctx) throws Exception {
    BlockingExecutionUtil.initialize(vertx);

    BlockingExecutionUtil.<Integer>runBlocking(() -> 2 + 3)
        .onComplete(
            ctx.succeeding(
                result ->
                    ctx.verify(
                        () -> {
                          assertThat(result).isEqualTo(5);
                          ctx.completeNow();
                        })));

    ctx.awaitCompletion(5, TimeUnit.SECONDS);
  }

  @Test
  @DisplayName("initialize is idempotent")
  void initializeIdempotent(Vertx vertx, VertxTestContext ctx) throws Exception {
    BlockingExecutionUtil.initialize(vertx);
    BlockingExecutionUtil.initialize(vertx); // second call should be no-op

    BlockingExecutionUtil.<String>runBlocking(() -> "ok")
        .onComplete(
            ctx.succeeding(
                result ->
                    ctx.verify(
                        () -> {
                          assertThat(result).isEqualTo("ok");
                          ctx.completeNow();
                        })));

    ctx.awaitCompletion(5, TimeUnit.SECONDS);
  }
}
