package com.stagepass.domain.transfer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

  // 양도 게시판 목록 — OPEN 상태만, 공연 시작 전
  @Query("""
      SELECT t FROM Transfer t
      JOIN FETCH t.reservation r
      JOIN FETCH r.show s
      JOIN FETCH s.performance
      JOIN FETCH t.fromUser
      WHERE t.status = 'OPEN'
      AND s.showDatetime > CURRENT_TIMESTAMP
      ORDER BY t.createdAt DESC
      """)
  List<Transfer> findOpenTransfers();

  // 특정 회차의 양도 목록
  @Query("""
      SELECT t FROM Transfer t
      JOIN FETCH t.reservation r
      JOIN FETCH r.show s
      JOIN FETCH s.performance
      JOIN FETCH t.fromUser
      WHERE t.status = 'OPEN'
      AND s.id = :showId
      AND s.showDatetime > CURRENT_TIMESTAMP
      ORDER BY t.createdAt DESC
      """)
  List<Transfer> findOpenTransfersByShowId(@Param("showId") Long showId);

  // 동일 예매에 이미 OPEN 양도 글이 있는지 확인
  boolean existsByReservationIdAndStatus(Long reservationId, TransferStatus status);

  // 내가 올린 양도 글 — JOIN FETCH로 N+1 방지
  @Query("""
      SELECT t FROM Transfer t
      JOIN FETCH t.reservation r
      JOIN FETCH r.show s
      JOIN FETCH s.performance
      WHERE t.fromUser.id = :userId
      ORDER BY t.createdAt DESC
      """)
  List<Transfer> findByFromUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);

  Optional<Transfer> findByIdAndFromUserId(Long id, Long fromUserId);

  // 공연 시작 시각이 지난 OPEN 양도 글 (만료 처리용)
  List<Transfer> findByStatusAndExpiresAtBefore(TransferStatus status, LocalDateTime now);
}
