package org.cdpg.dx.common.util;

public class PaginationInfo {
  private int page;
  private int size;
  private long totalCount;
  private int totalPages;
  private boolean hasNext;
  private boolean hasPrevious;

  public PaginationInfo(
      int page, int size, long totalCount, int totalPages, boolean hasNext, boolean hasPrevious) {
    this.page = page;
    this.size = size;
    this.totalCount = totalCount;
    this.totalPages = totalPages;
    this.hasNext = hasNext;
    this.hasPrevious = hasPrevious;
  }

  public int getPage() {
    return page;
  }

  public void setPage(int page) {
    this.page = page;
  }

  public int getSize() {
    return size;
  }

  public void setSize(int size) {
    this.size = size;
  }

  public long getTotalCount() {
    return totalCount;
  }

  public void setTotalCount(long totalCount) {
    this.totalCount = totalCount;
  }

  public int getTotalPages() {
    return totalPages;
  }

  public void setTotalPages(int totalPages) {
    this.totalPages = totalPages;
  }

  public boolean isHasNext() {
    return hasNext;
  }

  public void setHasNext(boolean hasNext) {
    this.hasNext = hasNext;
  }

  public boolean isHasPrevious() {
    return hasPrevious;
  }

  public void setHasPrevious(boolean hasPrevious) {
    this.hasPrevious = hasPrevious;
  }
}
