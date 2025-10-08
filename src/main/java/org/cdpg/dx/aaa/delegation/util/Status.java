package org.cdpg.dx.aaa.delegation.util;
import org.cdpg.dx.common.exception.DxBadRequestException;

public enum Status {
  APPROVED("approved"),
  REJECTED("rejected"),
  PENDING("pending");

  private final String status;

  Status(String status) {
    this.status = status;
  }

  public String getStatus() {
    return status;
  }


  public static Status fromString(String statusStr) {
    for (Status status : Status.values()) {
      if (status.getStatus().equalsIgnoreCase(statusStr))
        return status;
    }

    throw new DxBadRequestException("Invalid status:" + statusStr);

  }

}
