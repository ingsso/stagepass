package com.stagepass.admin.dashboard.dto;

import lombok.Getter;

@Getter
public class DashboardStatsDto {

  private final long total;
  private final long confirmed;
  private final long cancelled;
  private final long revenue;
  private final long todayCount;
  private final long todayRevenue;

  private DashboardStatsDto(long total, long confirmed, long cancelled,
                             long revenue, long todayCount, long todayRevenue) {
    this.total = total;
    this.confirmed = confirmed;
    this.cancelled = cancelled;
    this.revenue = revenue;
    this.todayCount = todayCount;
    this.todayRevenue = todayRevenue;
  }

  // Object[] 인덱스 접근을 한 곳으로 집중 — 쿼리 컬럼 순서 변경 시 여기만 수정
  public static DashboardStatsDto from(Object[] row) {
    return new DashboardStatsDto(
        toLong(row[0]),  // COUNT(r) — total
        toLong(row[1]),  // confirmed count
        toLong(row[2]),  // cancelled count
        toLong(row[3]),  // revenue
        toLong(row[4]),  // todayCount
        toLong(row[5])   // todayRevenue
    );
  }

  private static long toLong(Object value) {
    if (value == null) return 0L;
    return ((Number) value).longValue();
  }
}
