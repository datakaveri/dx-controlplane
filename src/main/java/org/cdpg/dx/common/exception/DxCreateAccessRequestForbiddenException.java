package org.cdpg.dx.common.exception;

public class DxCreateAccessRequestForbiddenException extends BaseDxException {
  public DxCreateAccessRequestForbiddenException(String message) {
    super(message);
  }

  public DxCreateAccessRequestForbiddenException(String message, Throwable cause) {
    super(DxErrorCodes.FORBIDDEN, message, cause);
  }
}
