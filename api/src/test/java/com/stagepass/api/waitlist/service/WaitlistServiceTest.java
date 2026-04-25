package com.stagepass.api.waitlist.service;

import com.stagepass.api.waitlist.dto.WaitlistStatusResponse;
import com.stagepass.common.exception.BusinessException;
import com.stagepass.common.exception.ErrorCode;
import com.stagepass.domain.performance.Performance;
import com.stagepass.domain.performance.Show;
import com.stagepass.domain.performance.ShowRepository;
import com.stagepass.domain.performance.ShowStatus;
import com.stagepass.domain.user.User;
import com.stagepass.domain.user.UserRepository;
import com.stagepass.domain.user.UserRole;
import com.stagepass.domain.waitlist.WaitlistEntry;
import com.stagepass.domain.waitlist.WaitlistRepository;
import com.stagepass.domain.waitlist.WaitlistStatus;
import com.stagepass.infra.redis.WaitlistRedisRepository;
import com.stagepass.kafka.event.WaitlistEvent;
import com.stagepass.kafka.producer.EventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class WaitlistServiceTest {

  @InjectMocks private WaitlistService waitlistService;

  @Mock private WaitlistRepository waitlistRepository;
  @Mock private ShowRepository showRepository;
  @Mock private UserRepository userRepository;
  @Mock private WaitlistRedisRepository waitlistRedisRepository;
  @Mock private EventPublisher eventPublisher;

  private static final Long SHOW_ID = 1L;
  private static final Long USER_ID = 10L;

  private User user;
  private Show show;

  @BeforeEach
  void setUp() {
    Performance performance = Performance.builder().title("테스트 공연").build();
    ReflectionTestUtils.setField(performance, "id", 1L);

    show = Show.builder()
        .performance(performance)
        .showDatetime(java.time.LocalDateTime.now().plusDays(1))
        .totalSeats(100)
        .status(ShowStatus.ON_SALE)
        .build();
    ReflectionTestUtils.setField(show, "id", SHOW_ID);

    user = User.builder()
        .email("test@test.com")
        .passwordHash("hash")
        .name("테스트유저")
        .role(UserRole.USER)
        .build();
    ReflectionTestUtils.setField(user, "id", USER_ID);
  }

  @Test
  @DisplayName("취소 대기 등록 — 순번과 전체 대기 수를 반환한다")
  void join_성공() {
    given(waitlistRedisRepository.isWaiting(SHOW_ID, USER_ID)).willReturn(false);
    given(showRepository.findById(SHOW_ID)).willReturn(Optional.of(show));
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
    given(waitlistRedisRepository.getRank(SHOW_ID, USER_ID)).willReturn(3L);
    given(waitlistRedisRepository.getSize(SHOW_ID)).willReturn(5L);

    WaitlistStatusResponse response = waitlistService.join(SHOW_ID, USER_ID);

    assertThat(response.getRank()).isEqualTo(3L);
    assertThat(response.getTotal()).isEqualTo(5L);
    assertThat(response.getStatus()).isEqualTo("WAITING");
    then(waitlistRepository).should().save(any(WaitlistEntry.class));
  }

  @Test
  @DisplayName("이미 대기 중이면 WAITLIST_ALREADY_JOINED 예외")
  void join_중복_실패() {
    given(waitlistRedisRepository.isWaiting(SHOW_ID, USER_ID)).willReturn(true);

    assertThatThrownBy(() -> waitlistService.join(SHOW_ID, USER_ID))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining(ErrorCode.WAITLIST_ALREADY_JOINED.getMessage());

    then(waitlistRepository).should(never()).save(any());
  }

  @Test
  @DisplayName("대기 순번 조회 — Redis에 없으면 NOT_IN_WAITLIST 반환")
  void getStatus_대기중_아님() {
    given(waitlistRedisRepository.getRank(SHOW_ID, USER_ID)).willReturn(null);
    given(waitlistRedisRepository.getSize(SHOW_ID)).willReturn(3L);

    WaitlistStatusResponse response = waitlistService.getStatus(SHOW_ID, USER_ID);

    assertThat(response.getRank()).isNull();
    assertThat(response.getStatus()).isEqualTo("NOT_IN_WAITLIST");
  }

  @Test
  @DisplayName("대기 이탈 — Redis에서 제거하고 DB 상태를 CANCELLED로 변경")
  void leave_성공() {
    WaitlistEntry entry = WaitlistEntry.builder().show(show).user(user).build();
    given(waitlistRepository.findByShowIdAndUserId(SHOW_ID, USER_ID)).willReturn(Optional.of(entry));

    waitlistService.leave(SHOW_ID, USER_ID);

    then(waitlistRedisRepository).should().remove(SHOW_ID, USER_ID);
    assertThat(entry.getStatus()).isEqualTo(WaitlistStatus.CANCELLED);
  }

  @Test
  @DisplayName("notifyNext — 대기자가 없으면 아무것도 하지 않는다")
  void notifyNext_대기자없음() {
    given(waitlistRedisRepository.popFirst(SHOW_ID)).willReturn(null);

    waitlistService.notifyNext(SHOW_ID);

    then(eventPublisher).should(never()).publishWaitlistNotified(any());
  }

  @Test
  @DisplayName("notifyNext — 첫 번째 대기자를 NOTIFIED 처리하고 Kafka 이벤트 발행")
  void notifyNext_성공() {
    WaitlistEntry entry = WaitlistEntry.builder().show(show).user(user).build();
    given(waitlistRedisRepository.popFirst(SHOW_ID)).willReturn(USER_ID);
    given(waitlistRepository.findByShowIdAndUserId(SHOW_ID, USER_ID)).willReturn(Optional.of(entry));

    waitlistService.notifyNext(SHOW_ID);

    assertThat(entry.getStatus()).isEqualTo(WaitlistStatus.NOTIFIED);
    assertThat(entry.getNotifyExpiresAt()).isNotNull();

    ArgumentCaptor<WaitlistEvent> captor = ArgumentCaptor.forClass(WaitlistEvent.class);
    then(eventPublisher).should().publishWaitlistNotified(captor.capture());
    assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
    assertThat(captor.getValue().getShowId()).isEqualTo(SHOW_ID);
  }

  @Test
  @DisplayName("notifyNext — DB 실패 시 Redis에 userId 복구")
  void notifyNext_DB실패시_Redis복구() {
    given(waitlistRedisRepository.popFirst(SHOW_ID)).willReturn(USER_ID);
    given(waitlistRepository.findByShowIdAndUserId(SHOW_ID, USER_ID))
        .willThrow(new RuntimeException("DB 장애"));

    waitlistService.notifyNext(SHOW_ID);

    then(waitlistRedisRepository).should().add(SHOW_ID, USER_ID);
    then(eventPublisher).should(never()).publishWaitlistNotified(any());
  }
}
