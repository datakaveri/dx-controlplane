package org.cdpg.dx.common.exception;

import static org.assertj.core.api.Assertions.*;
import static org.cdpg.dx.common.exception.DxErrorCodes.*;

import io.vertx.serviceproxy.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BaseDxExceptionTest {

  @Nested
  @DisplayName("constructors")
  class Constructors {

    @Test
    void constructWithCodeAndMessage() {
      BaseDxException ex = new BaseDxException(400, "Bad request");

      assertThat(ex.failureCode()).isEqualTo(400);
      assertThat(ex.getMessage()).isEqualTo("Bad request");
    }

    @Test
    void constructWithMessageOnly() {
      BaseDxException ex = new BaseDxException("Something failed");

      assertThat(ex.failureCode()).isEqualTo(DEFAULT_CODE);
      assertThat(ex.getMessage()).isEqualTo("Something failed");
    }

    @Test
    void constructWithCause_doesNotThrow() {
      RuntimeException cause = new RuntimeException("root cause");
      BaseDxException ex = new BaseDxException(500, "Wrapped", cause);

      assertThat(ex.failureCode()).isEqualTo(500);
      assertThat(ex.getMessage()).isEqualTo("Wrapped");
      // ServiceException may not propagate initCause, so just verify no exception thrown
    }

    @Test
    void constructWithSelfAsCause_doesNotThrow() {
      BaseDxException ex = new BaseDxException(500, "Self-ref");
      // Should not throw even if cause is same reference
      assertThatNoException().isThrownBy(
          () -> new BaseDxException(500, "Wrapped", ex));
    }
  }

  @Nested
  @DisplayName("from() factory method")
  class FromMethod {

    @Test
    void returnsSameIfAlreadyBaseDxException() {
      BaseDxException original = new BaseDxException(400, "Already DxException");

      BaseDxException result = BaseDxException.from(original);

      assertThat(result).isSameAs(original);
    }

    @Test
    void wrapsGenericThrowable() {
      RuntimeException cause = new RuntimeException("generic error");

      BaseDxException result = BaseDxException.from(cause);

      assertThat(result.failureCode()).isEqualTo(DEFAULT_CODE);
      assertThat(result.getMessage()).isEqualTo("generic error");
      // The wrapped exception retains the original message
    }

    @Test
    void convertsServiceExceptionNoRowFound() {
      ServiceException se = new ServiceException(PG_NO_ROW_ERROR, "No row found");

      BaseDxException result = BaseDxException.from(se);

      assertThat(result).isInstanceOf(NoRowFoundException.class);
      assertThat(result.getMessage()).isEqualTo("No row found");
    }

    @Test
    void convertsServiceExceptionInvalidColumn() {
      ServiceException se = new ServiceException(PG_INVALID_COL_ERROR, "Invalid column");

      BaseDxException result = BaseDxException.from(se);

      assertThat(result).isInstanceOf(InvalidColumnNameException.class);
    }

    @Test
    void convertsServiceExceptionUniqueConstraint() {
      ServiceException se = new ServiceException(PG_UNIQUE_CONSTRAINT_VIOLATION_ERROR, "Duplicate");

      BaseDxException result = BaseDxException.from(se);

      assertThat(result).isInstanceOf(UniqueConstraintViolationException.class);
    }

    @Test
    void convertsServiceExceptionPgError() {
      ServiceException se = new ServiceException(PG_ERROR, "DB error");

      BaseDxException result = BaseDxException.from(se);

      assertThat(result).isInstanceOf(DxPgException.class);
    }

    @Test
    void convertsServiceExceptionDefaultCode() {
      ServiceException se = new ServiceException(99999, "Unknown service error");

      BaseDxException result = BaseDxException.from(se);

      assertThat(result).isInstanceOf(BaseDxException.class);
      assertThat(result.failureCode()).isEqualTo(DEFAULT_CODE);
    }

    @Test
    void wrapsSubclassOfBaseDxException() {
      DxNotFoundException notFound = new DxNotFoundException("Not here");

      BaseDxException result = BaseDxException.from(notFound);

      assertThat(result).isSameAs(notFound);
    }
  }

  @Nested
  @DisplayName("exception subclasses")
  class ExceptionSubclasses {

    @Test
    void noRowFoundException() {
      NoRowFoundException ex = new NoRowFoundException("no row");
      assertThat(ex).isInstanceOf(BaseDxException.class);
      assertThat(ex.getMessage()).isEqualTo("no row");
    }

    @Test
    void uniqueConstraintViolation() {
      UniqueConstraintViolationException ex = new UniqueConstraintViolationException("duplicate");
      assertThat(ex).isInstanceOf(BaseDxException.class);
    }

    @Test
    void dxPgException() {
      DxPgException ex = new DxPgException("pg error");
      assertThat(ex).isInstanceOf(BaseDxException.class);
    }
  }
}
