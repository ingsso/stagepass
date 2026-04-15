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
    var all = reservationRepository.findAll();

    long total = all.size();
    long confirmed = all.stream()
        .filter(r -> r.getStatus() == ReservationStatus.CONFIRMED).count();
    long cancelled = all.stream()
        .filter(r -> r.getStatus() == ReservationStatus.CANCELLED).count();
    long revenue = all.stream()
        .filter(r -> r.getStatus() == ReservationStatus.CONFIRMED)
        .mapToLong(r -> r.getTotalPrice()).sum();

    LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
    long todayReservations = all.stream()
        .filter(r -> r.getCreatedAt() != null && r.getCreatedAt().isAfter(todayStart))
        .count();
    long todayRevenue = all.stream()
        .filter(r -> r.getStatus() == ReservationStatus.CONFIRMED
            && r.getCreatedAt() != null
            && r.getCreatedAt().isAfter(todayStart))
        .mapToLong(r -> r.getTotalPrice()).sum();

    return new DashboardResponse(total, confirmed, cancelled, revenue,
        todayReservations, todayRevenue);
  }
}