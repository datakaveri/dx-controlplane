package org.cdpg.dx.aaa.organization.models;

public enum OrganizationApprovalStage {
  PENDING("pending"),
  REJECTED("rejected"),
  APPROVED("approved");

  private static OrganizationApprovalStage temp;
  private final String status;

  OrganizationApprovalStage(String status) {
    this.status = status;
  }

  public static OrganizationApprovalStage fromString(String statusStr) {
    for (OrganizationApprovalStage status: OrganizationApprovalStage.values()) {
      if (status.getStatus().equalsIgnoreCase(statusStr))
        return status;
    }

    throw new IllegalArgumentException("Invalid status: " + statusStr);

  }

  public String getStatus() {
    return status;
  }

}