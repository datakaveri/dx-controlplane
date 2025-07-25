package org.cdpg.dx.common.exception;

public class DxForbiddenAccessRejectedException extends BaseDxException {
  public DxForbiddenAccessRejectedException(String message) {
    super(DxErrorCodes.FORBIDDEN_ACCESS_REJECTED, message);
  }

  public DxForbiddenAccessRejectedException(String message, Throwable cause) {
    super(DxErrorCodes.FORBIDDEN_ACCESS_REJECTED, message, cause);
  }
}