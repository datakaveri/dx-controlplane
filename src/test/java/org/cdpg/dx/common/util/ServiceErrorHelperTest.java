package org.cdpg.dx.common.util;

import static org.assertj.core.api.Assertions.*;

import io.vertx.core.Future;
import java.util.function.Function;
import org.cdpg.dx.common.exception.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ServiceErrorHelperTest {

  @Nested
  @DisplayName("mapNotFound()")
  class MapNotFound {

    @Test
    @DisplayName("maps NoRowFoundException to DxNotFoundException with correct message")
    void mapsNoRowFoundToDxNotFound() {
      Function<Throwable, Future<String>> fn = ServiceErrorHelper.mapNotFound("Entity not found");
      NoRowFoundException original = new NoRowFoundException("no row");

      Future<String> result = fn.apply(original);

      assertThat(result.failed()).isTrue();
      assertThat(result.cause()).isInstanceOf(DxNotFoundException.class);
      assertThat(result.cause().getMessage()).isEqualTo("Entity not found");
    }

    @Test
    @DisplayName("passes through other BaseDxException unchanged")
    void passesThrough_baseDxException() {
      Function<Throwable, Future<String>> fn = ServiceErrorHelper.mapNotFound("Entity not found");
      DxConflictException original = new DxConflictException("conflict");

      Future<String> result = fn.apply(original);

      assertThat(result.failed()).isTrue();
      assertThat(result.cause()).isSameAs(original);
    }

    @Test
    @DisplayName("wraps generic RuntimeException via BaseDxException.from and passes through")
    void passesThrough_genericException() {
      Function<Throwable, Future<String>> fn = ServiceErrorHelper.mapNotFound("Entity not found");
      RuntimeException original = new RuntimeException("generic");

      Future<String> result = fn.apply(original);

      assertThat(result.failed()).isTrue();
      assertThat(result.cause()).isInstanceOf(BaseDxException.class);
      assertThat(result.cause()).isNotInstanceOf(DxNotFoundException.class);
      assertThat(result.cause().getMessage()).isEqualTo("generic");
    }
  }

  @Nested
  @DisplayName("mapConflict()")
  class MapConflict {

    @Test
    @DisplayName("maps UniqueConstraintViolationException to DxConflictException with correct message")
    void mapsUniqueConstraintToDxConflict() {
      Function<Throwable, Future<String>> fn = ServiceErrorHelper.mapConflict("Already exists");
      UniqueConstraintViolationException original =
          new UniqueConstraintViolationException("duplicate key");

      Future<String> result = fn.apply(original);

      assertThat(result.failed()).isTrue();
      assertThat(result.cause()).isInstanceOf(DxConflictException.class);
      assertThat(result.cause().getMessage()).isEqualTo("Already exists");
    }

    @Test
    @DisplayName("passes through other BaseDxException unchanged")
    void passesThrough_baseDxException() {
      Function<Throwable, Future<String>> fn = ServiceErrorHelper.mapConflict("Already exists");
      DxNotFoundException original = new DxNotFoundException("not found");

      Future<String> result = fn.apply(original);

      assertThat(result.failed()).isTrue();
      assertThat(result.cause()).isSameAs(original);
    }

    @Test
    @DisplayName("wraps generic RuntimeException via BaseDxException.from and passes through")
    void passesThrough_genericException() {
      Function<Throwable, Future<String>> fn = ServiceErrorHelper.mapConflict("Already exists");
      RuntimeException original = new RuntimeException("generic");

      Future<String> result = fn.apply(original);

      assertThat(result.failed()).isTrue();
      assertThat(result.cause()).isInstanceOf(BaseDxException.class);
      assertThat(result.cause()).isNotInstanceOf(DxConflictException.class);
      assertThat(result.cause().getMessage()).isEqualTo("generic");
    }
  }

  @Nested
  @DisplayName("mapNotFoundOrConflict()")
  class MapNotFoundOrConflict {

    @Test
    @DisplayName("maps NoRowFoundException to DxNotFoundException with correct message")
    void mapsNoRowFoundToDxNotFound() {
      Function<Throwable, Future<String>> fn =
          ServiceErrorHelper.mapNotFoundOrConflict("not found msg", "conflict msg");
      NoRowFoundException original = new NoRowFoundException("no row");

      Future<String> result = fn.apply(original);

      assertThat(result.failed()).isTrue();
      assertThat(result.cause()).isInstanceOf(DxNotFoundException.class);
      assertThat(result.cause().getMessage()).isEqualTo("not found msg");
    }

    @Test
    @DisplayName("maps UniqueConstraintViolationException to DxConflictException with correct message")
    void mapsUniqueConstraintToDxConflict() {
      Function<Throwable, Future<String>> fn =
          ServiceErrorHelper.mapNotFoundOrConflict("not found msg", "conflict msg");
      UniqueConstraintViolationException original =
          new UniqueConstraintViolationException("duplicate");

      Future<String> result = fn.apply(original);

      assertThat(result.failed()).isTrue();
      assertThat(result.cause()).isInstanceOf(DxConflictException.class);
      assertThat(result.cause().getMessage()).isEqualTo("conflict msg");
    }

    @Test
    @DisplayName("passes through other BaseDxException unchanged")
    void passesThrough_baseDxException() {
      Function<Throwable, Future<String>> fn =
          ServiceErrorHelper.mapNotFoundOrConflict("not found msg", "conflict msg");
      DxForbiddenException original = new DxForbiddenException("forbidden");

      Future<String> result = fn.apply(original);

      assertThat(result.failed()).isTrue();
      assertThat(result.cause()).isSameAs(original);
    }

    @Test
    @DisplayName("wraps generic RuntimeException via BaseDxException.from and passes through")
    void passesThrough_genericException() {
      Function<Throwable, Future<String>> fn =
          ServiceErrorHelper.mapNotFoundOrConflict("not found msg", "conflict msg");
      RuntimeException original = new RuntimeException("generic");

      Future<String> result = fn.apply(original);

      assertThat(result.failed()).isTrue();
      assertThat(result.cause()).isInstanceOf(BaseDxException.class);
      assertThat(result.cause()).isNotInstanceOf(DxNotFoundException.class);
      assertThat(result.cause()).isNotInstanceOf(DxConflictException.class);
      assertThat(result.cause().getMessage()).isEqualTo("generic");
    }
  }
}
