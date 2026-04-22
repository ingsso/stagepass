package com.stagepass.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

  // 인증
  INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
  EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
  FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),

  // 유저
  USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
  DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
  INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "비밀번호가 일치하지 않습니다."),

  // 공연 / 좌석
  PERFORMANCE_NOT_FOUND(HttpStatus.NOT_FOUND, "공연을 찾을 수 없습니다."),
  SHOW_NOT_FOUND(HttpStatus.NOT_FOUND, "회차를 찾을 수 없습니다."),
  SEAT_NOT_FOUND(HttpStatus.NOT_FOUND, "좌석을 찾을 수 없습니다."),
  SEAT_ALREADY_HELD(HttpStatus.CONFLICT, "이미 선점된 좌석입니다."),

  // 예매 / 결제
  RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "예매 정보를 찾을 수 없습니다."),
  RESERVATION_NOT_CONFIRMED(HttpStatus.BAD_REQUEST, "확정된 예매만 양도할 수 있습니다."),
  PAYMENT_FAILED(HttpStatus.BAD_REQUEST, "결제에 실패했습니다."),
  DUPLICATE_PAYMENT(HttpStatus.CONFLICT, "이미 처리된 결제입니다."),

  // 양도
  TRANSFER_NOT_FOUND(HttpStatus.NOT_FOUND, "양도 게시글을 찾을 수 없습니다."),
  TRANSFER_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 양도 게시글이 존재합니다."),
  TRANSFER_ALREADY_CLAIMED(HttpStatus.CONFLICT, "이미 양도된 티켓입니다."),
  TRANSFER_NOT_OPEN(HttpStatus.BAD_REQUEST, "양도 가능한 상태가 아닙니다."),
  TRANSFER_SHOW_ENDED(HttpStatus.BAD_REQUEST, "공연이 이미 시작되어 양도할 수 없습니다."),
  TRANSFER_SELF_CLAIM(HttpStatus.BAD_REQUEST, "본인 양도 글은 수락할 수 없습니다.");

  private final HttpStatus status;
  private final String message;
}