package org.cdpg.dx.common.util;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PaginationInfoTest {

  @Test
  @DisplayName("from() calculates totalPages correctly")
  void calculatesPages() {
    PaginationInfo info = PaginationInfo.from(1, 10, 25);

    assertThat(info.getTotalPages()).isEqualTo(3); // ceil(25/10) = 3
    assertThat(info.getPage()).isEqualTo(1);
    assertThat(info.getSize()).isEqualTo(10);
    assertThat(info.getTotalCount()).isEqualTo(25);
  }

  @Test
  @DisplayName("from() sets hasNext on first page")
  void hasNextOnFirstPage() {
    PaginationInfo info = PaginationInfo.from(1, 10, 25);

    assertThat(info.isHasNext()).isTrue();
    assertThat(info.isHasPrevious()).isFalse();
  }

  @Test
  @DisplayName("from() sets hasPrevious on middle page")
  void hasBothOnMiddlePage() {
    PaginationInfo info = PaginationInfo.from(2, 10, 25);

    assertThat(info.isHasNext()).isTrue();
    assertThat(info.isHasPrevious()).isTrue();
  }

  @Test
  @DisplayName("from() no hasNext on last page")
  void noHasNextOnLastPage() {
    PaginationInfo info = PaginationInfo.from(3, 10, 25);

    assertThat(info.isHasNext()).isFalse();
    assertThat(info.isHasPrevious()).isTrue();
  }

  @Test
  @DisplayName("from() handles single page")
  void singlePage() {
    PaginationInfo info = PaginationInfo.from(1, 10, 5);

    assertThat(info.getTotalPages()).isEqualTo(1);
    assertThat(info.isHasNext()).isFalse();
    assertThat(info.isHasPrevious()).isFalse();
  }

  @Test
  @DisplayName("from() handles zero totalCount")
  void zeroCount() {
    PaginationInfo info = PaginationInfo.from(1, 10, 0);

    assertThat(info.getTotalPages()).isEqualTo(0);
    assertThat(info.isHasNext()).isFalse();
    assertThat(info.isHasPrevious()).isFalse();
  }

  @Test
  @DisplayName("from() defaults size to 10 when <= 0")
  void defaultsSize() {
    PaginationInfo info = PaginationInfo.from(1, 0, 25);

    assertThat(info.getSize()).isEqualTo(10);
    assertThat(info.getTotalPages()).isEqualTo(3);
  }

  @Test
  @DisplayName("from() defaults page to 1 when <= 0")
  void defaultsPage() {
    PaginationInfo info = PaginationInfo.from(0, 10, 25);

    assertThat(info.getPage()).isEqualTo(1);
    assertThat(info.isHasPrevious()).isFalse();
  }

  @Test
  @DisplayName("from() with negative size defaults to 10")
  void negativeSizeDefaults() {
    PaginationInfo info = PaginationInfo.from(1, -5, 25);

    assertThat(info.getSize()).isEqualTo(10);
  }

  @Test
  @DisplayName("from() exact multiple page count")
  void exactMultiple() {
    PaginationInfo info = PaginationInfo.from(1, 10, 30);

    assertThat(info.getTotalPages()).isEqualTo(3);
  }

  @Test
  @DisplayName("setters update fields")
  void settersWork() {
    PaginationInfo info = PaginationInfo.from(1, 10, 100);
    info.setPage(5);
    info.setSize(20);
    info.setTotalCount(200);
    info.setTotalPages(10);
    info.setHasNext(true);
    info.setHasPrevious(true);

    assertThat(info.getPage()).isEqualTo(5);
    assertThat(info.getSize()).isEqualTo(20);
    assertThat(info.getTotalCount()).isEqualTo(200);
    assertThat(info.getTotalPages()).isEqualTo(10);
    assertThat(info.isHasNext()).isTrue();
    assertThat(info.isHasPrevious()).isTrue();
  }
}
