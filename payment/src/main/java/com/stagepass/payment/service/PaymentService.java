package com.stagepass.payment.service;

import com.stagepass.domain.payment.Payment;
import com.stagepass.domain.payment.PaymentRepository;
import com.stagepass.domain.payment.PaymentStatus;
import com.stagepass.domain.reservation.Reservation;
import com.stagepass.domain.reservation.ReservationRepository;
import com.stagepass.infra.kafka.KafkaTopics;
import com.stagepass.kafka.event.PaymentResultEvent;
import com.stagepass.kafka.producer.EventPublisher;
import com.stagepass.payment.client.TossPaymentClient;
import com.stagepass.payment.dto.PaymentRequest;
import com.stagepass.payment.dto.TossPaymentConfirmRequest;
import com.stagepass.payment.dto.TossPaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stagepass.kafka.event.PaymentCancelEvent;
import com.stagepass.kafka.event.PaymentRequestedEvent;
import org.springframework.dao.DataIntegrityViolationException;

@Slf4j
@Service
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
@RequiredArgsConstructor
public class PaymentService {

  private final PaymentRepository paymentRepository;
  private final ReservationRepository reservationRepository;
  private final TossPaymentClient tossPaymentClient;
  private final EventPublisher eventPublisher;
  private final ObjectMapper objectMapper;

  // payment.requested Consumer — Saga 시작점
  @KafkaListener(topics = KafkaTopics.PAYMENT_REQUESTED, groupId = "payment-group")
  public void handlePaymentRequested(String message, Acknowledgment ack) {
    try {
      PaymentRequestedEvent event =
          objectMapper.readValue(message, PaymentRequestedEvent.class);
      log.info("[Saga] payment request received reservationId={}", event.getReservationId());

      processPayment(event);
      ack.acknowledge();
    } catch (JsonProcessingException e) {
      log.error("[Saga] payment request deserialization failed (poison pill) message={}", message, e);
      ack.acknowledge();
    } catch (DataIntegrityViolationException e) {
      // 멱등성 체크 통과 후 동시 요청이 unique 위반 — 중복 처리로 간주하고 ack
      log.warn("[Saga] duplicate payment detected (DB unique violation) - idempotent skip message={}", message);
      ack.acknowledge();
    } catch (Exception e) {
      log.error("[Saga] payment request processing failed message={}", message, e);
      throw new RuntimeException(e); // DefaultErrorHandler 재시도 위임
    }
  }

  @Transactional
  public void processPayment(PaymentRequestedEvent event) {
    // 1. 멱등성 체크 — 동일 orderId 중복 결제 방지
    if (paymentRepository.findByTossOrderId(event.getTossOrderId()).isPresent()) {
      log.warn("[Saga] duplicate payment ignored orderId={}", event.getTossOrderId());
      return;
    }

    Reservation reservation = reservationRepository.findById(event.getReservationId())
        .orElseThrow(() -> new RuntimeException("Reservation not found: " + event.getReservationId()));

    // 2. Payment 레코드 생성 (PENDING)
    Payment payment = Payment.builder()
        .reservation(reservation)
        .tossOrderId(event.getTossOrderId())
        .amount(event.getAmount())
        .build();
    paymentRepository.save(payment);

    try {
      // 3. 토스페이먼츠 API 호출
      TossPaymentResponse response = tossPaymentClient.confirm(
          new TossPaymentConfirmRequest(
              event.getPaymentKey(),
              event.getTossOrderId(),
              event.getAmount()
          )
      );

      // 4. 성공 → payment.completed 발행
      payment.complete(response.getPaymentKey(), response.getMethod());
      eventPublisher.publishPaymentCompleted(
          new PaymentResultEvent(
              event.getReservationId(),
              event.getUserId(),
              event.getTossOrderId(),
              null
          )
      );
      log.info("[Saga] payment completed reservationId={}", event.getReservationId());

    } catch (Exception e) {
      // 5. 실패 → payment.failed 발행 (보상 트랜잭션 트리거)
      payment.fail(e.getMessage());
      eventPublisher.publishPaymentFailed(
          new PaymentResultEvent(
              event.getReservationId(),
              event.getUserId(),
              event.getTossOrderId(),
              e.getMessage()
          )
      );
      log.error("[Saga] payment failed reservationId={} reason={}", event.getReservationId(), e.getMessage());
    }
  }

  // payment.cancel.requested Consumer — 예매 취소 환불 처리
  @KafkaListener(topics = KafkaTopics.PAYMENT_CANCEL_REQUESTED, groupId = "payment-cancel-group")
  public void handlePaymentCancelRequested(String message, Acknowledgment ack) {
    try {
      PaymentCancelEvent event = objectMapper.readValue(message, PaymentCancelEvent.class);
      log.info("[Saga] refund request received reservationId={}", event.getReservationId());
      cancelPayment(event.getReservationId());
      ack.acknowledge();
    } catch (JsonProcessingException e) {
      log.error("[Saga] refund request deserialization failed (poison pill) message={}", message, e);
      ack.acknowledge();
    } catch (Exception e) {
      log.error("[Saga] refund processing failed message={}", message, e);
      throw new RuntimeException(e); // DefaultErrorHandler 재시도 위임
    }
  }

  // 예매 취소 → 토스 결제 취소 (멱등: 이미 CANCELLED면 재처리 없이 반환)
  @Transactional
  public void cancelPayment(Long reservationId) {
    Payment payment = paymentRepository.findByReservationId(reservationId)
        .orElseThrow(() -> new RuntimeException("결제 정보 없음"));

    if (payment.getStatus() == PaymentStatus.CANCELLED) {
      log.info("[Payment] already cancelled - idempotent skip reservationId={}", reservationId);
      return;
    }

    if (payment.getTossPaymentKey() != null) {
      try {
        tossPaymentClient.cancel(
            payment.getTossPaymentKey(),
            "사용자 예매 취소",
            payment.getAmount()
        );
      } catch (Exception e) {
        log.error("[Payment] Toss cancel API failed - manual intervention required reservationId={}", reservationId, e);
        throw e; // 재시도 위임 (DB 상태 변경 없이 롤백)
      }
    }

    payment.cancel();
    log.info("[Payment] payment cancelled reservationId={}", reservationId);
  }
}