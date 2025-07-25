package org.cdpg.dx.common.exception;

public class CsvLimitExceedNoRecordFound extends BaseDxException {
  public CsvLimitExceedNoRecordFound(String message) {
    super(DxErrorCodes.CSV_STREAM_ERROR, message);
  }

  public CsvLimitExceedNoRecordFound(String message, Throwable cause) {
    super(DxErrorCodes.CSV_STREAM_ERROR, message, cause);
  }
}