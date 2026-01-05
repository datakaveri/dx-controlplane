package org.cdpg.dx.common.util;

import javassist.NotFoundException;
import org.cdpg.dx.auth.authentication.exception.AuthenticationException;
import org.cdpg.dx.common.HttpStatusCode;
import org.cdpg.dx.common.exception.*;

public class ExceptionHttpStatusMapper {

  public static HttpStatusCode map(Throwable throwable) {
    return switch (throwable) {
      case NoRowFoundException e -> HttpStatusCode.NOT_FOUND;
      case InvalidColumnNameException e -> HttpStatusCode.BAD_REQUEST;
      case UniqueConstraintViolationException e -> HttpStatusCode.CONFLICT;
      case DxPgException e -> HttpStatusCode.INTERNAL_SERVER_ERROR;
      case AuthenticationException e -> HttpStatusCode.UNAUTHORIZED;
      case DxUnauthorizedException e -> HttpStatusCode.UNAUTHORIZED;
      case DxForbiddenException e -> HttpStatusCode.FORBIDDEN;
      case DxConflictException e -> HttpStatusCode.CONFLICT;
      case DxNotFoundException e -> HttpStatusCode.NOT_FOUND;
      case DxInternalServerErrorException e -> HttpStatusCode.INTERNAL_SERVER_ERROR;
      case DxForbiddenNoAccessException e -> HttpStatusCode.FORBIDDEN_NO_ACCESS;
      case DxForbiddenAccessRejectedException e -> HttpStatusCode.FORBIDDEN_ACCESS_REJECTED;
      case DxForbiddenPendingAccessException e -> HttpStatusCode.FORBIDDEN_ACCESS_PENDING;
      case DxBadRequestException e -> HttpStatusCode.BAD_REQUEST;
      case DxValidationException e -> HttpStatusCode.BAD_REQUEST;
      case DxCreateAccessRequestForbiddenException e -> HttpStatusCode.FORBIDDEN;
      case ExchangeRegistrationException e -> HttpStatusCode.CONFLICT;
      case ExchangeNotFoundException e -> HttpStatusCode.NOT_FOUND;
      case QueueAlreadyExistsException e -> HttpStatusCode.CONFLICT;
      case QueueDeletionException e -> HttpStatusCode.BAD_REQUEST;
      case QueueNotFoundException e -> HttpStatusCode.NOT_FOUND;
      case QueueRegistrationFailedException e -> HttpStatusCode.INTERNAL_SERVER_ERROR;
      case DxRabbitMqException e -> HttpStatusCode.INTERNAL_SERVER_ERROR;
      case DxRabbitMqGeneralException e -> HttpStatusCode.INTERNAL_SERVER_ERROR;
      case DxSubscriptionException e -> HttpStatusCode.INTERNAL_SERVER_ERROR;

      case BaseDxException e -> HttpStatusCode.BAD_REQUEST;
      case NotFoundException e -> HttpStatusCode.NOT_FOUND;
      case IllegalArgumentException e -> HttpStatusCode.BAD_REQUEST;
      default -> HttpStatusCode.INTERNAL_SERVER_ERROR;
    };
  }
}
