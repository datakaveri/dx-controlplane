package org.cdpg.dx.common.exception;

public class DxForbiddenNoAccessException extends BaseDxException {
  public DxForbiddenNoAccessException(String message) {
    super(DxErrorCodes.FORBIDDEN_NO_ACCESS, message);
  }

  public DxForbiddenNoAccessException(String message, Throwable cause) {
    super(DxErrorCodes.FORBIDDEN_NO_ACCESS, message, cause);
  }
}
