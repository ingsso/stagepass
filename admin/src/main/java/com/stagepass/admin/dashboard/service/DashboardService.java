package com.stagepass.admin.dashboard.service;

import com.stagepass.admin.dashboard.dto.DashboardResponse;
import com.stagepass.admin.dashboard.dto.DashboardStatsDto;
import com.stagepass.domain.reservation.ReservationRepository;
import com.stagepass.domain.reservation.ReservationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DashboardService {

  private final ReservationRepository reservationRepository;

  @Transactional(readOnly = true)
  public DashboardResponse getDashboard() {
    LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
    Object[] raw = reservationRepository.getDashboardStats(
        ReservationStatus.CONFIRMED, ReservationStatus.CANCELLED, todayStart);

    DashboardStatsDto stats = DashboardStatsDto.from(raw);
    return new DashboardResponse(
        stats.getTotal(),
        stats.getConfirmed(),
        stats.getCancelled(),
        stats.getRevenue(),
        stats.getTodayCount(),
        stats.getTodayRevenue()
    );
  }
}