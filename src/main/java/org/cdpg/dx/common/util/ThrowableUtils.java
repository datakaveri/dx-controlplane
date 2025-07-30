package org.cdpg.dx.common.util;

import io.vertx.ext.web.validation.BodyProcessorException;
import org.cdpg.dx.common.exception.*;

import java.util.Set;

public final class ThrowableUtils {

    private static final Set<Class<? extends Throwable>> SAFE_EXCEPTIONS = Set.of(
            IllegalArgumentException.class,
            DxBadRequestException.class,
            DxUnauthorizedException.class,
            BodyProcessorException.class,
            DxForbiddenException.class,
            DxNotFoundException.class
    );

    // Private constructor to prevent instantiation
    private ThrowableUtils() {}

    public static boolean isSafeToExpose(Throwable throwable) {
        return SAFE_EXCEPTIONS.contains(throwable.getClass());
    }
}

