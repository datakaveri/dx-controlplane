package org.cdpg.dx.common.exception;

public class DxForbiddenPendingAccessException extends BaseDxException {
  public DxForbiddenPendingAccessException(String message) {
    super(DxErrorCodes.FORBIDDEN_ACCESS_PENDING, message);
  }

  public DxForbiddenPendingAccessException(String message, Throwable cause) {
    super(DxErrorCodes.FORBIDDEN_ACCESS_PENDING, message, cause);
  }
}
