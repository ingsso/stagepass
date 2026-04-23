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
    long total = reservationRepository.count();
    long confirmed = reservationRepository.countByStatus(ReservationStatus.CONFIRMED);
    long cancelled = reservationRepository.countByStatus(ReservationStatus.CANCELLED);
    long revenue = reservationRepository.sumTotalPriceByStatus(ReservationStatus.CONFIRMED);

    LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
    long todayReservations = reservationRepository.countByCreatedAtAfter(todayStart);
    long todayRevenue = reservationRepository.sumTotalPriceByStatusAndCreatedAtAfter(
        ReservationStatus.CONFIRMED, todayStart);

    return new DashboardResponse(total, confirmed, cancelled, revenue,
        todayReservations, todayRevenue);
  }
}