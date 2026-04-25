package com.stagepass.admin.dashboard.service;

import com.stagepass.admin.dashboard.dto.DashboardResponse;
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
    Object[] s = reservationRepository.getDashboardStats(
        ReservationStatus.CONFIRMED, ReservationStatus.CANCELLED, todayStart);

    return new DashboardResponse(
        toLong(s[0]),  // total
        toLong(s[1]),  // confirmed
        toLong(s[2]),  // cancelled
        toLong(s[3]),  // revenue
        toLong(s[4]),  // todayCount
        toLong(s[5])   // todayRevenue
    );
  }

  private long toLong(Object value) {
    if (value == null) return 0L;
    return ((Number) value).longValue();
  }
}