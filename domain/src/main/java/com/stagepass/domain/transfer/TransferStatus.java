package com.stagepass.domain.transfer;

public enum TransferStatus {
  OPEN,       // 양도 게시 중
  CLAIMED,    // 양도 완료
  CANCELLED   // 게시자 취소
}
