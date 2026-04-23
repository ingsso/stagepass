package com.stagepass.domain.exchange;

public enum SeatExchangeStatus {
  PENDING,   // 교환 제안 대기 중
  ACCEPTED,  // 교환 수락 완료
  REJECTED,  // 거절됨
  CANCELLED, // 제안자가 취소
  EXPIRED    // 시간 초과
}
