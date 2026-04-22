package com.stagepass.api.seat.service;

import com.stagepass.api.seat.dto.SeatHoldRequest;
import com.stagepass.api.seat.dto.SeatHoldResponse;
import com.stagepass.common.exception.BusinessException;
import com.stagepass.common.exception.ErrorCode;
import com.stagepass.domain.performance.*;
import com.stagepass.domain.reservation.Reservation;
import com.stagepass.domain.reservation.ReservationRepository;
import com.stagepass.domain.reservation.ReservationSeat;
import com.stagepass.domain.reservation.ReservationSeatRepository;
import com.stagepass.domain.user.User;
import com.stagepass.domain.user.UserRepository;
import com.stagepass.domain.user.UserRole;
import com.stagepass.infra.redis.SeatRedisRepository;
import com.stagepass.kafka.producer.EventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class SeatServiceTest {

  @InjectMocks
  private SeatService seatService;

  @Mock private SeatRepository seatRepository;
  @Mock private ZoneRepository zoneRepository;
  @Mock private ShowRepository showRepository;
  @Mock private ReservationRepository reservationRepository;
  @Mock private ReservationSeatRepository reservationSeatRepository;
  @Mock private UserRepository userRepository;
  @Mock private SeatRedisRepository seatRedisRepository;
  @Mock private EventPublisher eventPublisher;

  private User user;
  private Show show;
  private Zone zone;
  private Seat seat1;
  private Seat seat2;

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
        .showDatetime(java.time.LocalDateTime.now().plusDays(1))
        .totalSeats(100)
        .status(ShowStatus.ON_SALE)
        .build();

    zone = Zone.builder()
        .show(show)
        .name("VIP")
        .grade("VIP")
        .price(100000)
        .rowCount(5)
        .colCount(10)
        .build();

    seat1 = Seat.builder().zone(zone).seatCode("A-01").rowNum(1).colNum(1).build();
    seat2 = Seat.builder().zone(zone).seatCode("A-02").rowNum(1).colNum(2).build();
  }

  @Test
  @DisplayName("좌석 선점 성공 - 예매 생성 및 Kafka 이벤트 발행")
  void holdSeats_성공() {
    // given
    Long userId = 1L;
    Long showId = 1L;
    SeatHoldRequest request = new SeatHoldRequest();
    ReflectionTestUtils.setField(request, "seatIds", List.of(10L, 11L));

    given(userRepository.findById(userId)).willReturn(Optional.of(user));
    given(showRepository.findById(showId)).willReturn(Optional.of(show));
    given(seatRedisRepository.hold(10L, userId)).willReturn(true);
    given(seatRedisRepository.hold(11L, userId)).willReturn(true);
    given(seatRepository.findAllByIdWithZone(List.of(10L, 11L))).willReturn(List.of(seat1, seat2));
    given(reservationRepository.save(any())).willReturn(
        Reservation.builder().user(user).show(show).totalPrice(200000).build()
    );

    // when
    SeatHoldResponse response = seatService.holdSeats(showId, userId, request);

    // then
    assertThat(response.getHeldSeatIds()).containsExactlyInAnyOrder(10L, 11L);
    assertThat(response.getFailedSeatIds()).isEmpty();
    then(reservationRepository).should().save(any());
    then(eventPublisher).should(times(2)).publishSeatHold(any());
  }

  @Test
  @DisplayName("좌석 선점 실패 - 이미 선점된 좌석이 포함되면 전체 롤백")
  void holdSeats_이미선점된좌석포함_전체롤백() {
    // given
    Long userId = 1L;
    Long showId = 1L;
    SeatHoldRequest request = new SeatHoldRequest();
    ReflectionTestUtils.setField(request, "seatIds", List.of(10L, 11L));

    given(userRepository.findById(userId)).willReturn(Optional.of(user));
    given(showRepository.findById(showId)).willReturn(Optional.of(show));
    given(seatRedisRepository.hold(10L, userId)).willReturn(true);   // 10번 선점 성공
    given(seatRedisRepository.hold(11L, userId)).willReturn(false);  // 11번 이미 선점됨

    // when & then
    assertThatThrownBy(() -> seatService.holdSeats(showId, userId, request))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.SEAT_ALREADY_HELD);

    // 10번 좌석도 롤백됐는지 확인
    then(seatRedisRepository).should().release(10L, userId);
    then(reservationRepository).should(times(0)).save(any());
  }

  @Test
  @DisplayName("좌석 선점 실패 - 존재하지 않는 유저")
  void holdSeats_유저없음_예외() {
    // given
    Long userId = 999L;
    Long showId = 1L;
    given(userRepository.findById(userId)).willReturn(Optional.empty());

    // when & then
    SeatHoldRequest request = new SeatHoldRequest();
    ReflectionTestUtils.setField(request, "seatIds", List.of(10L));
    assertThatThrownBy(() -> seatService.holdSeats(showId, userId, request))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.USER_NOT_FOUND);
  }

  @Test
  @DisplayName("좌석 선점 실패 - 존재하지 않는 회차")
  void holdSeats_회차없음_예외() {
    // given
    Long userId = 1L;
    Long showId = 999L;
    given(userRepository.findById(userId)).willReturn(Optional.of(user));
    given(showRepository.findById(showId)).willReturn(Optional.empty());

    // when & then
    SeatHoldRequest request = new SeatHoldRequest();
    ReflectionTestUtils.setField(request, "seatIds", List.of(10L));
    assertThatThrownBy(() -> seatService.holdSeats(showId, userId, request))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.SHOW_NOT_FOUND);
  }
}
