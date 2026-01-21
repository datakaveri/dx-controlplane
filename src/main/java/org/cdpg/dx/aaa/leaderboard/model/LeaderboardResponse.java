package org.cdpg.dx.aaa.leaderboard.model;

import java.util.List;
import org.cdpg.dx.common.util.PaginationInfo;

public class LeaderboardResponse<T> {
  private List<T> data;
  private PaginationInfo paginationInfo;

  public LeaderboardResponse(List<T> data, PaginationInfo paginationInfo) {
    this.data = data;
    this.paginationInfo = paginationInfo;
  }

  public List<T> getData() {
    return data;
  }

  public void setData(List<T> data) {
    this.data = data;
  }

  public PaginationInfo getPaginationInfo() {
    return paginationInfo;
  }

  public void setPaginationInfo(PaginationInfo paginationInfo) {
    this.paginationInfo = paginationInfo;
  }
}
