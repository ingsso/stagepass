package com.stagepass.domain.waitlist;

public enum WaitlistStatus {
  WAITING,   // 대기 중
  NOTIFIED,  // 입장 알림 받음 (10분 내 예매해야)
  RESERVED,  // 예매 완료
  EXPIRED,   // 알림 후 시간 초과
  CANCELLED  // 직접 취소
}
