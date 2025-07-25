package org.cdpg.dx.common.util;

import java.util.UUID;

public class ValidationUtils {

    private ValidationUtils() {
        // Prevent instantiation
    }

    public static String requireNonNullOrBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing or blank field: " + fieldName);
        }
        return value;
    }

    public static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException("Missing required field: " + fieldName);
        }
        return value;
    }

    public static Integer requireNonNull(Integer value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException("Missing required integer: " + fieldName);
        }
        return value;
    }

    public static UUID requireNonNull(UUID value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException("Missing required UUID: " + fieldName);
        }
        return value;
    }

    public static boolean requireTrue(boolean condition, String errorMessage) {
        if (!condition) {
            throw new IllegalArgumentException(errorMessage);
        }
        return true;
    }
}
