package com.stagepass.admin.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DashboardResponse {
  private long totalReservations;
  private long confirmedReservations;
  private long cancelledReservations;
  private long totalRevenue;
  private long todayReservations;
  private long todayRevenue;
}