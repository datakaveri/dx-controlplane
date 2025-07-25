package org.cdpg.dx.common.exception;

public class DxEsException extends BaseDxException {
  public DxEsException(String message) {
    super(DxErrorCodes.ES_ERROR, message);
  }

  public DxEsException(String message, Throwable cause) {
    super(DxErrorCodes.ES_ERROR, message, cause);
  }
}
