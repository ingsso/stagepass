package com.stagepass.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stagepass.domain.performance.Seat;
import com.stagepass.domain.performance.SeatRepository;
import com.stagepass.domain.reservation.Reservation;
import com.stagepass.domain.reservation.ReservationRepository;
import com.stagepass.domain.reservation.ReservationSeat;
import com.stagepass.domain.reservation.ReservationSeatRepository;
import com.stagepass.infra.kafka.KafkaTopics;
import com.stagepass.infra.redis.SeatRedisRepository;
import com.stagepass.kafka.event.PaymentResultEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
@RequiredArgsConstructor
public class ReservationEventConsumer {

  private final ReservationRepository reservationRepository;
  private final ReservationSeatRepository reservationSeatRepository;
  private final SeatRepository seatRepository;
  private final SeatRedisRepository seatRedisRepository;
  private final ObjectMapper objectMapper;

  // 결제 완료 → 예매 확정
  @Transactional
  @KafkaListener(topics = KafkaTopics.PAYMENT_COMPLETED, groupId = "reservation-group")
  public void handlePaymentCompleted(String message, Acknowledgment ack) {
    try {
      PaymentResultEvent event = objectMapper.readValue(message, PaymentResultEvent.class);
      Reservation reservation = reservationRepository.findById(event.getReservationId())
          .orElseThrow(() -> new RuntimeException("예매 없음: " + event.getReservationId()));

      reservation.confirm();

      // 좌석 DB 상태 RESERVED 로 확정 (JOIN FETCH로 N+1 방지)
      List<ReservationSeat> reservationSeats =
          reservationSeatRepository.findByReservationIdWithSeat(reservation.getId());
      for (ReservationSeat rs : reservationSeats) {
        rs.getSeat().reserve();
      }

      log.info("[Kafka] 예매 확정 reservationId={}", reservation.getId());
      ack.acknowledge();
    } catch (Exception e) {
      log.error("[Kafka] 예매 확정 처리 실패 message={}", message, e);
    }
  }

  // 결제 실패 → 보상 트랜잭션 (예매 만료 + 좌석 Redis 해제)
  @Transactional
  @KafkaListener(topics = KafkaTopics.PAYMENT_FAILED, groupId = "reservation-group")
  public void handlePaymentFailed(String message, Acknowledgment ack) {
    try {
      PaymentResultEvent event = objectMapper.readValue(message, PaymentResultEvent.class);
      Reservation reservation = reservationRepository.findById(event.getReservationId())
          .orElseThrow(() -> new RuntimeException("예매 없음: " + event.getReservationId()));

      reservation.expire();

      // Redis 선점 해제 (보상 트랜잭션, JOIN FETCH로 N+1 방지)
      List<ReservationSeat> reservationSeats =
          reservationSeatRepository.findByReservationIdWithSeat(reservation.getId());
      for (ReservationSeat rs : reservationSeats) {
        seatRedisRepository.release(rs.getSeat().getId(), event.getUserId());
      }

      log.info("[Kafka] 보상 트랜잭션 완료 reservationId={}", reservation.getId());
      ack.acknowledge();
    } catch (Exception e) {
      log.error("[Kafka] 보상 트랜잭션 실패 message={}", message, e);
    }
  }
}