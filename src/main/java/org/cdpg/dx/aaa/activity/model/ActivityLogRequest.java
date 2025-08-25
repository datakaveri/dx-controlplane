package org.cdpg.dx.aaa.activity.model;

import java.util.UUID;

public class ActivityLogRequest {
  private UUID userId;
  private String startTime;
  private String endTime;
  private int size;
  private int page;

  public ActivityLogRequest(UUID userId, String startTime, String endTime, int size, int page) {
    this.userId = userId;
    this.startTime = startTime;
    this.endTime = endTime;
    this.size = size;
    this.page = page;
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public String getStartTime() {
    return startTime;
  }

  public void setStartTime(String startTime) {
    this.startTime = startTime;
  }

  public String getEndTime() {
    return endTime;
  }

  public void setEndTime(String endTime) {
    this.endTime = endTime;
  }

  public int getSize() {
    return size;
  }

  public void setSize(int size) {
    this.size = size;
  }

  public int getPage() {
    return page;
  }

  public void setPage(int page) {
    this.page = page;
  }
}
