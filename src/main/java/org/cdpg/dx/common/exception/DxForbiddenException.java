package org.cdpg.dx.common.exception;

public class DxForbiddenException extends BaseDxException {
    public DxForbiddenException(String message) {
        super(DxErrorCodes.FORBIDDEN, message);
    }

    public DxForbiddenException(String message, Throwable cause) {
        super(DxErrorCodes.FORBIDDEN, message, cause);
    }
}