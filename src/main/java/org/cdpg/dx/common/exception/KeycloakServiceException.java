package org.cdpg.dx.common.exception;

/**
 * Exception thrown when Keycloak service operations fail.
 */
public class KeycloakServiceException extends BaseDxException {
    private static final int KEYCLOAK_SERVICE_ERROR = 12100; // Using 121xx range for Keycloak specific errors

    public KeycloakServiceException(String message) {
        super(KEYCLOAK_SERVICE_ERROR, message);
    }

    public KeycloakServiceException(String message, Throwable cause) {
        super(KEYCLOAK_SERVICE_ERROR, message, cause);
    }
}