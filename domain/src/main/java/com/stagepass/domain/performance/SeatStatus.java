package com.stagepass.domain.performance;

public enum SeatStatus {
  AVAILABLE,
  HOLDING,    // Redis TTL 선점 중 (참고용, DB엔 잘 안 씀)
  RESERVED,
  BLOCKED
}