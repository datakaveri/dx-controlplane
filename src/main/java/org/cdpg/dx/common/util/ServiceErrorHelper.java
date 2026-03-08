package org.cdpg.dx.common.util;

import io.vertx.core.Future;
import java.util.function.Function;
import org.cdpg.dx.common.exception.BaseDxException;
import org.cdpg.dx.common.exception.DxConflictException;
import org.cdpg.dx.common.exception.DxNotFoundException;
import org.cdpg.dx.common.exception.NoRowFoundException;
import org.cdpg.dx.common.exception.UniqueConstraintViolationException;

/**
 * Reusable error recovery functions for service-layer Future composition.
 *
 * <p>Eliminates the repetitive {@code .recover(err -> { BaseDxException.from(err); if
 * (NoRowFoundException) ... })} pattern that appears 60+ times across service implementations.
 *
 * <h3>Usage:</h3>
 * <pre>{@code
 * // Before (6 lines):
 * .recover(err -> {
 *     BaseDxException dxEx = BaseDxException.from(err);
 *     if (dxEx instanceof NoRowFoundException) {
 *         return Future.failedFuture(new DxNotFoundException("msg", dxEx));
 *     }
 *     return Future.failedFuture(dxEx);
 * });
 *
 * // After (1 line):
 * .recover(ServiceErrorHelper.mapNotFound("msg"));
 * }</pre>
 */
public final class ServiceErrorHelper {

  private ServiceErrorHelper() {}

  /**
   * Returns a recovery function that maps {@link NoRowFoundException} to {@link
   * DxNotFoundException} with the given message, and re-throws all other exceptions.
   *
   * @param message the not-found error message
   * @param <T> the future result type
   * @return a function suitable for use with {@code Future.recover()}
   */
  public static <T> Function<Throwable, Future<T>> mapNotFound(String message) {
    return err -> {
      BaseDxException dxEx = BaseDxException.from(err);
      if (dxEx instanceof NoRowFoundException) {
        return Future.failedFuture(new DxNotFoundException(message, dxEx));
      }
      return Future.failedFuture(dxEx);
    };
  }

  /**
   * Returns a recovery function that maps {@link UniqueConstraintViolationException} to {@link
   * DxConflictException} with the given message, and re-throws all other exceptions.
   *
   * @param message the conflict error message
   * @param <T> the future result type
   * @return a function suitable for use with {@code Future.recover()}
   */
  public static <T> Function<Throwable, Future<T>> mapConflict(String message) {
    return err -> {
      BaseDxException dxEx = BaseDxException.from(err);
      if (dxEx instanceof UniqueConstraintViolationException) {
        return Future.failedFuture(new DxConflictException(message, dxEx));
      }
      return Future.failedFuture(dxEx);
    };
  }

  /**
   * Returns a recovery function that maps {@link NoRowFoundException} to {@link
   * DxNotFoundException} and {@link UniqueConstraintViolationException} to {@link
   * DxConflictException}.
   *
   * @param notFoundMessage the not-found error message
   * @param conflictMessage the conflict error message
   * @param <T> the future result type
   * @return a function suitable for use with {@code Future.recover()}
   */
  public static <T> Function<Throwable, Future<T>> mapNotFoundOrConflict(
      String notFoundMessage, String conflictMessage) {
    return err -> {
      BaseDxException dxEx = BaseDxException.from(err);
      if (dxEx instanceof NoRowFoundException) {
        return Future.failedFuture(new DxNotFoundException(notFoundMessage, dxEx));
      }
      if (dxEx instanceof UniqueConstraintViolationException) {
        return Future.failedFuture(new DxConflictException(conflictMessage, dxEx));
      }
      return Future.failedFuture(dxEx);
    };
  }
}
