package com.stagepass.infra.kafka;

public final class KafkaTopics {

  private KafkaTopics() {}

  // 좌석
  public static final String SEAT_HOLD = "ticket.seat.hold";
  public static final String SEAT_HOLD_EXPIRED = "ticket.seat.hold.expired";
  public static final String SEAT_RELEASED = "ticket.seat.released";

  // 결제
  public static final String PAYMENT_REQUESTED = "payment.requested";
  public static final String PAYMENT_COMPLETED = "payment.completed";
  public static final String PAYMENT_FAILED = "payment.failed";

  // 예매
  public static final String RESERVATION_CONFIRMED = "reservation.confirmed";
  public static final String RESERVATION_CANCELLED = "reservation.cancelled";

  // 알림
  public static final String NOTIFICATION_SEND = "notification.send";

  // 대기열
  public static final String QUEUE_ENTERED = "queue.entered";
  public static final String QUEUE_ACTIVATED = "queue.activated";

  // 양도
  public static final String TRANSFER_CLAIMED = "transfer.claimed";

  // 취소 대기
  public static final String WAITLIST_NOTIFIED = "waitlist.notified";

  // 자리 교환
  public static final String EXCHANGE_COMPLETED = "exchange.completed";
}