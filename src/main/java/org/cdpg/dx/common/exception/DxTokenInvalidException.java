package org.cdpg.dx.common.exception;


public class DxTokenInvalidException extends BaseDxException {
    public DxTokenInvalidException(String message) {
        super(DxErrorCodes.TOKEN_INVALID, message);
    }

    public DxTokenInvalidException(String message, Throwable cause) {
        super(DxErrorCodes.TOKEN_INVALID, message, cause);
    }
}
