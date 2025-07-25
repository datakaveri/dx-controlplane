package org.cdpg.dx.common.exception;

public class DxConflictException extends BaseDxException {
  public DxConflictException(String message) {
    super(DxErrorCodes.CONFLICT, message);
  }

  public DxConflictException(String message, Throwable cause) {
    super(DxErrorCodes.CONFLICT, message, cause);
  }
}
