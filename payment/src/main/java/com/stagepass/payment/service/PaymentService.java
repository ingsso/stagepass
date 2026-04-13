package com.stagepass.payment.service;

import com.stagepass.domain.payment.Payment;
import com.stagepass.domain.payment.PaymentRepository;
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
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stagepass.kafka.event.PaymentRequestedEvent;

@Slf4j
@Service
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
      log.info("[Saga] 결제 요청 수신 reservationId={}", event.getReservationId());

      processPayment(event);
      ack.acknowledge();
    } catch (Exception e) {
      log.error("[Saga] 결제 요청 처리 실패 message={}", message, e);
    }
  }

  @Transactional
  public void processPayment(PaymentRequestedEvent event) {
    // 1. 멱등성 체크 — 동일 orderId 중복 결제 방지
    if (paymentRepository.findByTossOrderId(event.getTossOrderId()).isPresent()) {
      log.warn("[Saga] 중복 결제 요청 무시 orderId={}", event.getTossOrderId());
      return;
    }

    Reservation reservation = reservationRepository.findById(event.getReservationId())
        .orElseThrow(() -> new RuntimeException("예매 없음: " + event.getReservationId()));

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
      log.info("[Saga] 결제 완료 reservationId={}", event.getReservationId());

    } catch (Exception e) {
      // 5. 실패 → payment.failed 발행 (보상 트랜잭션 트리거)
      payment.fail();
      eventPublisher.publishPaymentFailed(
          new PaymentResultEvent(
              event.getReservationId(),
              event.getUserId(),
              event.getTossOrderId(),
              e.getMessage()
          )
      );
      log.error("[Saga] 결제 실패 reservationId={} reason={}", event.getReservationId(), e.getMessage());
    }
  }

  // 예매 취소 → 토스 결제 취소
  @Transactional
  public void cancelPayment(Long reservationId) {
    Payment payment = paymentRepository.findByReservationId(reservationId)
        .orElseThrow(() -> new RuntimeException("결제 정보 없음"));

    // 토스 취소 API는 별도 구현 (생략 — 실제 연동 시 추가)
    payment.cancel();
    log.info("[Payment] 결제 취소 reservationId={}", reservationId);
  }
}