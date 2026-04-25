package com.stagepass.domain.exchange;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SeatExchangeRepository extends JpaRepository<SeatExchange, Long> {

  // 내가 받은 교환 제안 (수락 대기 중)
  List<SeatExchange> findByReceiverIdAndStatus(Long receiverId, SeatExchangeStatus status);

  // 내가 보낸 교환 제안
  List<SeatExchange> findByProposerIdOrderByCreatedAtDesc(Long proposerId);

  // 동일 예매에 이미 PENDING 제안이 있는지
  boolean existsByProposerReservationIdAndStatus(Long reservationId, SeatExchangeStatus status);
  boolean existsByReceiverReservationIdAndStatus(Long reservationId, SeatExchangeStatus status);

  // 24시간 초과된 PENDING 제안 (만료 처리용)
  List<SeatExchange> findByStatusAndExpiresAtBefore(SeatExchangeStatus status, LocalDateTime now);

  @Query("SELECT e FROM SeatExchange e " +
      "JOIN FETCH e.proposer JOIN FETCH e.receiver " +
      "JOIN FETCH e.proposerReservation pr JOIN FETCH pr.show prs JOIN FETCH prs.performance " +
      "JOIN FETCH e.receiverReservation rr JOIN FETCH rr.show rrs JOIN FETCH rrs.performance " +
      "WHERE e.id = :id")
  Optional<SeatExchange> findByIdWithDetails(@Param("id") Long id);
}
