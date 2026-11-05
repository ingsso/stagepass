package com.stagepass.api.common.consumer;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stagepass.api.waitlist.service.WaitlistService;
import com.stagepass.kafka.event.ReservationEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class WaitlistReleaseConsumerTest {

  @InjectMocks private WaitlistReleaseConsumer consumer;

  @Mock private WaitlistService waitlistService;
  @Mock private ObjectMapper objectMapper;
  @Mock private Acknowledgment ack;

  private static final Long RESERVATION_ID = 10L;
  private static final Long USER_ID = 1L;
  private static final Long SHOW_ID = 100L;

  @Test
  @DisplayName("예매 취소/만료 정상 처리 — 이벤트의 showId로 notifyNext 호출 + ack")
  void handleReservationReleased_성공() throws Exception {
    given(objectMapper.readValue(any(String.class), eq(ReservationEvent.class)))
        .willReturn(new ReservationEvent(RESERVATION_ID, USER_ID, SHOW_ID));

    consumer.handleReservationReleased("{}", ack);

    then(waitlistService).should().notifyNext(SHOW_ID);
    then(ack).should().acknowledge();
  }

  @Test
  @DisplayName("포이즌 필 — JSON 파싱 실패 시 ack (재시도 없음)")
  void handleReservationReleased_포이즌필_ack() throws Exception {
    given(objectMapper.readValue(any(String.class), eq(ReservationEvent.class)))
        .willThrow(new JsonParseException(null, "bad json"));

    consumer.handleReservationReleased("{bad}", ack);

    then(ack).should().acknowledge();
    then(waitlistService).should(never()).notifyNext(any());
  }

  @Test
  @DisplayName("notifyNext 실패 — RuntimeException 재시도 위임 (ack 없음)")
  void handleReservationReleased_알림실패_재시도() throws Exception {
    given(objectMapper.readValue(any(String.class), eq(ReservationEvent.class)))
        .willReturn(new ReservationEvent(RESERVATION_ID, USER_ID, SHOW_ID));
    willThrow(new RuntimeException("DB 장애")).given(waitlistService).notifyNext(SHOW_ID);

    assertThatThrownBy(() -> consumer.handleReservationReleased("{}", ack))
        .isInstanceOf(RuntimeException.class);

    then(ack).should(never()).acknowledge();
  }
}
