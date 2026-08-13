package com.stagepass.api.queue.service;

import com.stagepass.api.queue.dto.QueueEnterResponse;
import com.stagepass.api.queue.dto.QueueStatusResponse;
import com.stagepass.common.exception.BusinessException;
import com.stagepass.common.exception.ErrorCode;
import com.stagepass.domain.performance.Performance;
import com.stagepass.domain.performance.Show;
import com.stagepass.domain.performance.ShowRepository;
import com.stagepass.domain.performance.ShowStatus;
import com.stagepass.domain.queue.QueueEntry;
import com.stagepass.domain.queue.QueueEntryRepository;
import com.stagepass.domain.queue.QueueStatus;
import com.stagepass.domain.user.User;
import com.stagepass.domain.user.UserRepository;
import com.stagepass.domain.user.UserRole;
import com.stagepass.infra.redis.QueueRedisRepository;
import com.stagepass.kafka.event.QueueEvent;
import com.stagepass.kafka.producer.EventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

  @InjectMocks
  private QueueService queueService;

  @Mock private QueueRedisRepository queueRedisRepository;
  @Mock private QueueEntryRepository queueEntryRepository;
  @Mock private ShowRepository showRepository;
  @Mock private UserRepository userRepository;
  @Mock private EventPublisher eventPublisher;
  @Mock private QueueEntryWriter queueEntryWriter;

  private static final Long SHOW_ID = 1L;
  private static final Long USER_ID = 1L;

  private User user;
  private Show show;

  @BeforeEach
  void setUp() {
    user = User.builder()
        .email("test@test.com")
        .passwordHash("encoded")
        .name("홍길동")
        .role(UserRole.USER)
        .build();

    Performance performance = Performance.builder()
        .title("뮤지컬 테스트")
        .build();

    show = Show.builder()
        .performance(performance)
        .showDatetime(LocalDateTime.now().plusDays(1))
        .totalSeats(100)
        .status(ShowStatus.ON_SALE)
        .build();
  }

  // ──────────────────────────────────────────────
  // enter
  // ──────────────────────────────────────────────

  @Test
  @DisplayName("대기열 진입 성공 - 신규 진입, 입장 허가 불가 순번")
  void enter_성공_신규진입() {
    // given
    given(showRepository.findById(SHOW_ID)).willReturn(Optional.of(show));
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
    given(queueRedisRepository.getRank(SHOW_ID, USER_ID)).willReturn(null, 15L); // 첫 호출: null(신규), 두 번째: rank
    given(queueRedisRepository.getSize(SHOW_ID)).willReturn(20L);
    given(queueEntryWriter.tryInsert(SHOW_ID, USER_ID)).willReturn(true); // INSERT 성공 = 신규 진입

    // when
    QueueEnterResponse response = queueService.enter(SHOW_ID, USER_ID);

    // then
    assertThat(response.getRank()).isEqualTo(15L);
    assertThat(response.getTotal()).isEqualTo(20L);
    assertThat(response.isActivated()).isFalse();
    then(queueRedisRepository).should().enter(SHOW_ID, USER_ID);
    then(queueEntryWriter).should().tryInsert(SHOW_ID, USER_ID);
    then(eventPublisher).should().publishQueueEntered(any(QueueEvent.class));
    then(eventPublisher).should(never()).publishQueueActivated(any());
  }

  @Test
  @DisplayName("대기열 진입 성공 - 순번 10 이내 즉시 입장 허가")
  void enter_성공_즉시입장허가() {
    // given
    given(showRepository.findById(SHOW_ID)).willReturn(Optional.of(show));
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
    given(queueRedisRepository.getRank(SHOW_ID, USER_ID)).willReturn(null, 5L); // rank=5, 즉시 입장
    given(queueRedisRepository.getSize(SHOW_ID)).willReturn(5L);
    given(queueEntryWriter.tryInsert(SHOW_ID, USER_ID)).willReturn(true); // INSERT 성공 = 신규 진입
    // 첫 조회(기존 진입 확인)는 비어 있고, INSERT 후 활성화 시점에 조회되면 엔트리가 있다
    given(queueEntryRepository.findByShowIdAndUserId(SHOW_ID, USER_ID))
        .willReturn(Optional.empty(),
                    Optional.of(QueueEntry.builder().show(show).user(user).rank(5).build()));

    // when
    QueueEnterResponse response = queueService.enter(SHOW_ID, USER_ID);

    // then
    assertThat(response.getRank()).isEqualTo(5L);
    assertThat(response.isActivated()).isTrue();
    then(eventPublisher).should().publishQueueEntered(any(QueueEvent.class));
    then(eventPublisher).should().publishQueueActivated(any(QueueEvent.class));
  }

  @Test
  @DisplayName("대기열 진입 - 이미 대기 중이면 현재 순번 반환 (중복 진입 방지)")
  void enter_이미대기중_현재순번반환() {
    // given
    given(showRepository.findById(SHOW_ID)).willReturn(Optional.of(show));
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
    given(queueRedisRepository.getRank(SHOW_ID, USER_ID)).willReturn(7L); // 이미 대기 중
    given(queueRedisRepository.getSize(SHOW_ID)).willReturn(20L);

    // when
    QueueEnterResponse response = queueService.enter(SHOW_ID, USER_ID);

    // then
    assertThat(response.getRank()).isEqualTo(7L);
    assertThat(response.isActivated()).isFalse();
    then(queueRedisRepository).should(never()).enter(any(), any());
    then(queueEntryRepository).should(never()).save(any());
    then(eventPublisher).should(never()).publishQueueEntered(any());
  }

  @Test
  @DisplayName("대기열 진입 실패 - 존재하지 않는 회차")
  void enter_회차없음_예외() {
    // given
    given(showRepository.findById(SHOW_ID)).willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> queueService.enter(SHOW_ID, USER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.SHOW_NOT_FOUND);

    then(queueRedisRepository).should(never()).enter(any(), any());
  }

  @Test
  @DisplayName("대기열 진입 실패 - 존재하지 않는 유저")
  void enter_유저없음_예외() {
    // given
    given(showRepository.findById(SHOW_ID)).willReturn(Optional.of(show));
    given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> queueService.enter(SHOW_ID, USER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.USER_NOT_FOUND);

    then(queueRedisRepository).should(never()).enter(any(), any());
  }

  // ──────────────────────────────────────────────
  // getStatus
  // ──────────────────────────────────────────────

  @Test
  @DisplayName("대기열 순번 조회 - 대기 중 상태")
  void getStatus_대기중() {
    // given
    // 순번이 활성화 배치(10) 밖이어야 WAITING 으로 남는다.
    // 배치 이내(rank ≤ 10)면 getStatus 가 stuck 복구를 위해 즉시 활성화한다.
    QueueEntry entry = QueueEntry.builder().show(show).user(user).rank(15).build();
    given(queueRedisRepository.getRank(SHOW_ID, USER_ID)).willReturn(15L);
    given(queueRedisRepository.getSize(SHOW_ID)).willReturn(20L);
    given(queueEntryRepository.findByShowIdAndUserId(SHOW_ID, USER_ID))
        .willReturn(Optional.of(entry)); // status = WAITING (기본값)

    // when
    QueueStatusResponse response = queueService.getStatus(SHOW_ID, USER_ID);

    // then
    assertThat(response.getRank()).isEqualTo(15L);
    assertThat(response.getTotal()).isEqualTo(20L);
    assertThat(response.getStatus()).isEqualTo("WAITING");
    assertThat(response.getEstimatedWaitSeconds()).isEqualTo(14 * 30L); // (rank-1) * 30
  }

  @Test
  @DisplayName("대기열 순번 조회 - 입장 허가된 상태")
  void getStatus_입장허가됨() {
    // given
    QueueEntry entry = QueueEntry.builder().show(show).user(user).rank(1).build();
    entry.activate(); // ACTIVATED 상태로 변경
    given(queueRedisRepository.getRank(SHOW_ID, USER_ID)).willReturn(1L);
    given(queueRedisRepository.getSize(SHOW_ID)).willReturn(20L);
    given(queueEntryRepository.findByShowIdAndUserId(SHOW_ID, USER_ID))
        .willReturn(Optional.of(entry));

    // when
    QueueStatusResponse response = queueService.getStatus(SHOW_ID, USER_ID);

    // then
    assertThat(response.getStatus()).isEqualTo("ACTIVATED");
  }

  @Test
  @DisplayName("대기열 순번 조회 - 대기열 미진입 상태")
  void getStatus_대기열미진입() {
    // given
    given(queueRedisRepository.getRank(SHOW_ID, USER_ID)).willReturn(null);
    given(queueRedisRepository.getSize(SHOW_ID)).willReturn(0L);

    // when
    QueueStatusResponse response = queueService.getStatus(SHOW_ID, USER_ID);

    // then
    assertThat(response.getRank()).isNull();
    assertThat(response.getStatus()).isEqualTo("NOT_IN_QUEUE");
    assertThat(response.getEstimatedWaitSeconds()).isNull();
  }

  // ──────────────────────────────────────────────
  // activateNextBatch
  // ──────────────────────────────────────────────

  @Test
  @DisplayName("다음 배치 입장 허가 - 대기 중인 유저에게 Kafka 이벤트 발행")
  void activateNextBatch_성공() {
    // given
    given(queueRedisRepository.getTop(SHOW_ID, 10))
        .willReturn(Set.of("101", "102", "103"));
    given(queueEntryRepository.findByShowIdAndUserIdIn(eq(SHOW_ID), any()))
        .willReturn(List.of()); // DB 엔트리 없어도 이벤트는 발행됨

    // when
    queueService.activateNextBatch(SHOW_ID);

    // then
    then(eventPublisher).should(times(3)).publishQueueActivated(any(QueueEvent.class));
  }

  @Test
  @DisplayName("다음 배치 입장 허가 - 대기열 비어있으면 아무것도 하지 않음")
  void activateNextBatch_대기열비어있음() {
    // given
    given(queueRedisRepository.getTop(SHOW_ID, 10)).willReturn(Set.of());

    // when
    queueService.activateNextBatch(SHOW_ID);

    // then
    then(eventPublisher).should(never()).publishQueueActivated(any());
  }

  // ──────────────────────────────────────────────
  // leave
  // ──────────────────────────────────────────────

  @Test
  @DisplayName("대기열 퇴장 - WAITING 상태면 다음 배치 활성화 없음")
  void leave_대기중상태_퇴장() {
    // given
    QueueEntry entry = QueueEntry.builder().show(show).user(user).rank(5).build(); // WAITING
    given(queueEntryRepository.findByShowIdAndUserId(SHOW_ID, USER_ID))
        .willReturn(Optional.of(entry));

    // when
    queueService.leave(SHOW_ID, USER_ID);

    // then
    then(queueRedisRepository).should().remove(SHOW_ID, USER_ID);
    then(eventPublisher).should(never()).publishQueueActivated(any());
  }

  @Test
  @DisplayName("대기열 퇴장 - ACTIVATED 상태면 다음 배치 입장 허가 실행")
  void leave_활성화상태_퇴장_다음배치활성화() {
    // given
    QueueEntry entry = QueueEntry.builder().show(show).user(user).rank(1).build();
    entry.activate(); // ACTIVATED 상태
    given(queueEntryRepository.findByShowIdAndUserId(SHOW_ID, USER_ID))
        .willReturn(Optional.of(entry));
    given(queueRedisRepository.getTop(SHOW_ID, 10)).willReturn(Set.of("200"));
    given(queueEntryRepository.findByShowIdAndUserIdIn(eq(SHOW_ID), any()))
        .willReturn(List.of());

    // when
    queueService.leave(SHOW_ID, USER_ID);

    // then
    then(queueRedisRepository).should().remove(SHOW_ID, USER_ID);
    then(eventPublisher).should().publishQueueActivated(any(QueueEvent.class));
  }
}
