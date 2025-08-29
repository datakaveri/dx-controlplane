package org.cdpg.dx.acl.accessRequest.dao.model;

public enum Status {
  GRANTED("GRANTED"),
  PENDING("PENDING"),
  REJECTED("REJECTED");

  private final String status;

  Status(String status) {
    this.status = status;
  }

  public static Status fromString(String status) {
    for (Status statusEnum : values()) {

      if (statusEnum.getStatus().equalsIgnoreCase(status)) {
        return statusEnum;
      }
    }
    throw new IllegalArgumentException("Invalid status: " + status);
  }

  public String getStatus() {
    return status;
  }
}
