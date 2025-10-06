package org.cdpg.dx.common.exception;

public class DxRuntimeException extends BaseDxException {

  /**
   * Constructs a DxRuntimeException with a specific error code and message
   * @param errorCode the error code (can reuse DxErrorCodes or custom)
   * @param message detailed error message
   */
  public DxRuntimeException(int errorCode, String message) {
    super(errorCode, message);
  }

  /**
   * Constructs a DxRuntimeException with a specific error code, message, and cause
   * @param errorCode the error code
   * @param message detailed error message
   * @param cause the underlying exception
   */
  public DxRuntimeException(int errorCode, String message, Throwable cause) {
    super(errorCode, message, cause);
  }

  /**
   * Convenience constructor with default runtime error code
   * @param message detailed error message
   */
  public DxRuntimeException(String message) {
    super(DxErrorCodes.RUNTIME_ERROR, message);
  }

  /**
   * Convenience constructor with default runtime error code and cause
   * @param message detailed error message
   * @param cause the underlying exception
   */
  public DxRuntimeException(String message, Throwable cause) {
    super(DxErrorCodes.RUNTIME_ERROR, message, cause);
  }
}
