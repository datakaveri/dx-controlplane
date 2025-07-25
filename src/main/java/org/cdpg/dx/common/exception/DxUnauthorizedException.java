package org.cdpg.dx.common.exception;

public class DxUnauthorizedException extends BaseDxException {
    public DxUnauthorizedException(String message) {
        super(DxErrorCodes.UNAUTHORIZED, message);
    }

    public DxUnauthorizedException(String message, Throwable cause) {
        super(DxErrorCodes.UNAUTHORIZED, message, cause);
    }
}
