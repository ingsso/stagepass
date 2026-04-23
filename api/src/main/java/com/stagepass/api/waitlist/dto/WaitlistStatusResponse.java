package com.stagepass.api.waitlist.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class WaitlistStatusResponse {
  private final Long rank;   // 현재 순번 (1-based)
  private final Long total;  // 전체 대기 인원
  private final String status; // WAITING, NOTIFIED, NOT_IN_WAITLIST
}
