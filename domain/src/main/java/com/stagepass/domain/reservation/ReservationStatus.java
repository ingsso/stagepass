package com.stagepass.domain.reservation;

public enum ReservationStatus {
  PENDING,     // 결제 대기 (선점 중)
  CONFIRMED,   // 결제 완료
  CANCELLED,   // 취소
  EXPIRED      // 선점 만료
}