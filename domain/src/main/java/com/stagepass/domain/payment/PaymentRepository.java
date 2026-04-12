package com.stagepass.domain.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
  Optional<Payment> findByTossOrderId(String tossOrderId);
  Optional<Payment> findByReservationId(Long reservationId);
}