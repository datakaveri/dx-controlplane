package org.cdpg.dx.common.exception;

public class DxInternalServerErrorException extends BaseDxException {
  public DxInternalServerErrorException(String message) {
    super(DxErrorCodes.INTERNAL_ERROR, message);
  }

  public DxInternalServerErrorException(String message, Throwable cause) {
    super(DxErrorCodes.INTERNAL_ERROR, message, cause);
  }
}