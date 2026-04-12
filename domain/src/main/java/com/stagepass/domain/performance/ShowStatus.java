package com.stagepass.domain.performance;

public enum ShowStatus {
  SCHEDULED,   // 예매 대기
  ON_SALE,     // 예매 중
  SOLD_OUT,    // 매진
  CANCELLED,   // 취소
  COMPLETED    // 공연 완료
}