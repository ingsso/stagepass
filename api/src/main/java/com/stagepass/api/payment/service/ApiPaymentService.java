package com.stagepass.api.payment.service;

import com.stagepass.api.payment.dto.*;
import com.stagepass.common.exception.BusinessException;
import com.stagepass.common.exception.ErrorCode;
import com.stagepass.domain.payment.Payment;
import com.stagepass.domain.payment.PaymentRepository;
import com.stagepass.domain.reservation.Reservation;
import com.stagepass.domain.reservation.ReservationRepository;
import com.stagepass.infra.redis.PendingPaymentRedisRepository;
import com.stagepass.kafka.event.PaymentRequestedEvent;
import com.stagepass.kafka.producer.EventPublisher;
import com.stagepass.payment.dto.PaymentRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiPaymentService {

  private final ReservationRepository reservationRepository;
  private final PaymentRepository paymentRepository;
  private final PendingPaymentRedisRepository pendingPaymentRepository;
  private final EventPublisher eventPublisher;

  // 결제 준비 — 토스 위젯에 넘길 orderId 발급 후 Redis에 임시 저장
  @Transactional(readOnly = true)
  public PaymentInitResponse initPayment(Long userId, PaymentInitRequest request) {
    Reservation reservation = reservationRepository
        .findByIdAndUserId(request.getReservationId(), userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));

    String orderId = UUID.randomUUID().toString();
    pendingPaymentRepository.save(orderId, reservation.getId()); // Redis TTL 10분

    String orderName = reservation.getShow().getPerformance().getTitle()
        + " (" + reservation.getReservationSeats().size() + "석)";

    return new PaymentInitResponse(orderId, reservation.getTotalPrice(), orderName);
  }

  // 결제 승인 요청 → Kafka payment.requested 발행 (Saga 시작)
  @Transactional
  public void confirmPayment(Long userId, PaymentRequest request) {
    // orderId 유효성 검증 (initPayment에서 발급된 것인지 확인)
    Long reservationIdFromRedis = pendingPaymentRepository.get(request.getOrderId());
    if (reservationIdFromRedis == null) {
      throw new BusinessException(ErrorCode.INVALID_TOKEN); // orderId 만료 또는 미발급
    }

    Reservation reservation = reservationRepository
        .findByIdAndUserId(reservationIdFromRedis, userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));

    // 멱등성 체크
    if (paymentRepository.findByTossOrderId(request.getOrderId()).isPresent()) {
      throw new BusinessException(ErrorCode.DUPLICATE_PAYMENT);
    }

    eventPublisher.publishPaymentRequested(
        new PaymentRequestedEvent(
            reservation.getId(),
            userId,
            request.getAmount(),
            request.getOrderId(),
            request.getPaymentKey()
        )
    );

    pendingPaymentRepository.delete(request.getOrderId()); // 사용 완료 후 제거

    log.info("[Payment] 결제 요청 발행 reservationId={} orderId={}",
        reservation.getId(), request.getOrderId());
  }

  // 결제 내역 조회
  @Transactional(readOnly = true)
  public PaymentResponse getPayment(Long reservationId, Long userId) {
    reservationRepository.findByIdAndUserId(reservationId, userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));

    Payment payment = paymentRepository.findByReservationId(reservationId)
        .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_FAILED));

    return new PaymentResponse(payment);
  }
}