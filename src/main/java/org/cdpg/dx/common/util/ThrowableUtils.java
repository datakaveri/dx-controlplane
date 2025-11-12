package org.cdpg.dx.common.util;

import io.vertx.ext.web.validation.BodyProcessorException;
import java.util.Set;
import org.cdpg.dx.common.exception.*;

public final class ThrowableUtils {

  private static final Set<Class<? extends Throwable>> SAFE_EXCEPTIONS =
      Set.of(
          IllegalArgumentException.class,
          DxBadRequestException.class,
          DxUnauthorizedException.class,
          BodyProcessorException.class,
          DxForbiddenException.class,
          DxNotFoundException.class,
          ExchangeRegistrationException.class,
          DxAuthException.class,
          DxRabbitMqException.class,
          DxRabbitMqGeneralException.class,
          DxSubscriptionException.class,
          ExchangeNotFoundException.class,
          QueueNotFoundException.class,
          QueueBindingFailedException.class,
          QueueDeletionException.class,
          QueueRegistrationFailedException.class,
          DxConflictException.class,
          UniqueConstraintViolationException.class,
          NoRowFoundException.class,
          DxValidationException.class);

  // Private constructor to prevent instantiation
  private ThrowableUtils() {}

  public static boolean isSafeToExpose(Throwable throwable) {
    return SAFE_EXCEPTIONS.contains(throwable.getClass());
  }
}
