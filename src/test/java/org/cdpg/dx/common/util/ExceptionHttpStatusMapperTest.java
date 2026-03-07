package org.cdpg.dx.common.util;

import static org.assertj.core.api.Assertions.*;

import org.cdpg.dx.common.HttpStatusCode;
import org.cdpg.dx.common.exception.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExceptionHttpStatusMapperTest {

  @Test
  @DisplayName("NoRowFoundException → NOT_FOUND")
  void noRowFound() {
    assertThat(ExceptionHttpStatusMapper.map(new NoRowFoundException("x")))
        .isEqualTo(HttpStatusCode.NOT_FOUND);
  }

  @Test
  @DisplayName("InvalidColumnNameException → BAD_REQUEST")
  void invalidColumn() {
    assertThat(ExceptionHttpStatusMapper.map(new InvalidColumnNameException("x")))
        .isEqualTo(HttpStatusCode.BAD_REQUEST);
  }

  @Test
  @DisplayName("UniqueConstraintViolationException → CONFLICT")
  void uniqueConstraint() {
    assertThat(ExceptionHttpStatusMapper.map(new UniqueConstraintViolationException("x")))
        .isEqualTo(HttpStatusCode.CONFLICT);
  }

  @Test
  @DisplayName("DxPgException → INTERNAL_SERVER_ERROR")
  void pgError() {
    assertThat(ExceptionHttpStatusMapper.map(new DxPgException("x")))
        .isEqualTo(HttpStatusCode.INTERNAL_SERVER_ERROR);
  }

  @Test
  @DisplayName("DxUnauthorizedException → UNAUTHORIZED")
  void unauthorized() {
    assertThat(ExceptionHttpStatusMapper.map(new DxUnauthorizedException("x")))
        .isEqualTo(HttpStatusCode.UNAUTHORIZED);
  }

  @Test
  @DisplayName("DxForbiddenException → FORBIDDEN")
  void forbidden() {
    assertThat(ExceptionHttpStatusMapper.map(new DxForbiddenException("x")))
        .isEqualTo(HttpStatusCode.FORBIDDEN);
  }

  @Test
  @DisplayName("DxConflictException → CONFLICT")
  void conflict() {
    assertThat(ExceptionHttpStatusMapper.map(new DxConflictException("x")))
        .isEqualTo(HttpStatusCode.CONFLICT);
  }

  @Test
  @DisplayName("DxNotFoundException → NOT_FOUND")
  void notFound() {
    assertThat(ExceptionHttpStatusMapper.map(new DxNotFoundException("x")))
        .isEqualTo(HttpStatusCode.NOT_FOUND);
  }

  @Test
  @DisplayName("DxInternalServerErrorException → INTERNAL_SERVER_ERROR")
  void internalError() {
    assertThat(ExceptionHttpStatusMapper.map(new DxInternalServerErrorException("x")))
        .isEqualTo(HttpStatusCode.INTERNAL_SERVER_ERROR);
  }

  @Test
  @DisplayName("DxForbiddenNoAccessException → FORBIDDEN_NO_ACCESS")
  void forbiddenNoAccess() {
    assertThat(ExceptionHttpStatusMapper.map(new DxForbiddenNoAccessException("x")))
        .isEqualTo(HttpStatusCode.FORBIDDEN_NO_ACCESS);
  }

  @Test
  @DisplayName("DxForbiddenAccessRejectedException → FORBIDDEN_ACCESS_REJECTED")
  void forbiddenAccessRejected() {
    assertThat(ExceptionHttpStatusMapper.map(new DxForbiddenAccessRejectedException("x")))
        .isEqualTo(HttpStatusCode.FORBIDDEN_ACCESS_REJECTED);
  }

  @Test
  @DisplayName("DxForbiddenPendingAccessException → FORBIDDEN_ACCESS_PENDING")
  void forbiddenAccessPending() {
    assertThat(ExceptionHttpStatusMapper.map(new DxForbiddenPendingAccessException("x")))
        .isEqualTo(HttpStatusCode.FORBIDDEN_ACCESS_PENDING);
  }

  @Test
  @DisplayName("DxBadRequestException → BAD_REQUEST")
  void badRequest() {
    assertThat(ExceptionHttpStatusMapper.map(new DxBadRequestException("x")))
        .isEqualTo(HttpStatusCode.BAD_REQUEST);
  }

  @Test
  @DisplayName("DxValidationException → BAD_REQUEST")
  void validation() {
    assertThat(ExceptionHttpStatusMapper.map(new DxValidationException("x")))
        .isEqualTo(HttpStatusCode.BAD_REQUEST);
  }

  @Test
  @DisplayName("DxCreateAccessRequestForbiddenException → FORBIDDEN")
  void createAccessForbidden() {
    assertThat(ExceptionHttpStatusMapper.map(new DxCreateAccessRequestForbiddenException("x")))
        .isEqualTo(HttpStatusCode.FORBIDDEN);
  }

  @Test
  @DisplayName("ExchangeRegistrationException → CONFLICT")
  void exchangeRegistration() {
    assertThat(ExceptionHttpStatusMapper.map(new ExchangeRegistrationException("x")))
        .isEqualTo(HttpStatusCode.CONFLICT);
  }

  @Test
  @DisplayName("ExchangeNotFoundException → NOT_FOUND")
  void exchangeNotFound() {
    assertThat(ExceptionHttpStatusMapper.map(new ExchangeNotFoundException("x")))
        .isEqualTo(HttpStatusCode.NOT_FOUND);
  }

  @Test
  @DisplayName("QueueAlreadyExistsException → CONFLICT")
  void queueAlreadyExists() {
    assertThat(ExceptionHttpStatusMapper.map(new QueueAlreadyExistsException("x")))
        .isEqualTo(HttpStatusCode.CONFLICT);
  }

  @Test
  @DisplayName("QueueDeletionException → BAD_REQUEST")
  void queueDeletion() {
    assertThat(ExceptionHttpStatusMapper.map(new QueueDeletionException("x")))
        .isEqualTo(HttpStatusCode.BAD_REQUEST);
  }

  @Test
  @DisplayName("QueueNotFoundException → NOT_FOUND")
  void queueNotFound() {
    assertThat(ExceptionHttpStatusMapper.map(new QueueNotFoundException("x")))
        .isEqualTo(HttpStatusCode.NOT_FOUND);
  }

  @Test
  @DisplayName("QueueRegistrationFailedException → INTERNAL_SERVER_ERROR")
  void queueRegistrationFailed() {
    assertThat(ExceptionHttpStatusMapper.map(new QueueRegistrationFailedException("x")))
        .isEqualTo(HttpStatusCode.INTERNAL_SERVER_ERROR);
  }

  @Test
  @DisplayName("DxRabbitMqException → INTERNAL_SERVER_ERROR")
  void rabbitMqError() {
    assertThat(ExceptionHttpStatusMapper.map(new DxRabbitMqException("x")))
        .isEqualTo(HttpStatusCode.INTERNAL_SERVER_ERROR);
  }

  @Test
  @DisplayName("DxRabbitMqGeneralException → INTERNAL_SERVER_ERROR")
  void rabbitMqGeneral() {
    assertThat(ExceptionHttpStatusMapper.map(new DxRabbitMqGeneralException("x")))
        .isEqualTo(HttpStatusCode.INTERNAL_SERVER_ERROR);
  }

  @Test
  @DisplayName("DxSubscriptionException → INTERNAL_SERVER_ERROR")
  void subscription() {
    assertThat(ExceptionHttpStatusMapper.map(new DxSubscriptionException("x")))
        .isEqualTo(HttpStatusCode.INTERNAL_SERVER_ERROR);
  }

  @Test
  @DisplayName("BaseDxException (generic) → BAD_REQUEST")
  void baseDxException() {
    assertThat(ExceptionHttpStatusMapper.map(new BaseDxException("x")))
        .isEqualTo(HttpStatusCode.BAD_REQUEST);
  }

  @Test
  @DisplayName("IllegalArgumentException → BAD_REQUEST")
  void illegalArgument() {
    assertThat(ExceptionHttpStatusMapper.map(new IllegalArgumentException("x")))
        .isEqualTo(HttpStatusCode.BAD_REQUEST);
  }

  @Test
  @DisplayName("unknown exception → INTERNAL_SERVER_ERROR")
  void unknownException() {
    assertThat(ExceptionHttpStatusMapper.map(new RuntimeException("unknown")))
        .isEqualTo(HttpStatusCode.INTERNAL_SERVER_ERROR);
  }
}
