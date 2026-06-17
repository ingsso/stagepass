package com.stagepass.api.transfer.service;

import com.stagepass.api.transfer.dto.TransferCreateRequest;
import com.stagepass.api.transfer.dto.TransferResponse;
import com.stagepass.common.exception.BusinessException;
import com.stagepass.common.exception.ErrorCode;
import com.stagepass.domain.reservation.Reservation;
import com.stagepass.domain.reservation.ReservationRepository;
import com.stagepass.domain.reservation.ReservationStatus;
import com.stagepass.domain.transfer.Transfer;
import com.stagepass.domain.transfer.TransferRepository;
import com.stagepass.domain.transfer.TransferStatus;
import com.stagepass.domain.user.User;
import com.stagepass.domain.user.UserRepository;
import com.stagepass.infra.redis.TransferRedisRepository;
import com.stagepass.kafka.event.NotificationEvent;
import com.stagepass.kafka.event.TransferEvent;
import com.stagepass.kafka.producer.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferService {

  private final TransferRepository transferRepository;
  private final ReservationRepository reservationRepository;
  private final UserRepository userRepository;
  private final TransferRedisRepository transferRedisRepository;
  private final EventPublisher eventPublisher;

  // 양도 게시글 등록
  @Transactional
  public TransferResponse create(Long userId, TransferCreateRequest request) {
    Reservation reservation = reservationRepository.findByIdAndUserId(request.getReservationId(), userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));

    if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
      throw new BusinessException(ErrorCode.RESERVATION_NOT_CONFIRMED);
    }

    if (reservation.getShow().getShowDatetime().isBefore(LocalDateTime.now())) {
      throw new BusinessException(ErrorCode.TRANSFER_SHOW_ENDED);
    }

    if (transferRepository.existsByReservationIdAndStatus(reservation.getId(), TransferStatus.OPEN)) {
      throw new BusinessException(ErrorCode.TRANSFER_ALREADY_EXISTS);
    }

    Transfer transfer = Transfer.builder()
        .reservation(reservation)
        .fromUser(reservation.getUser())
        .expiresAt(reservation.getShow().getShowDatetime())
        .build();

    transferRepository.save(transfer);
    log.info("[Transfer] created reservationId={} userId={}", reservation.getId(), userId);
    return new TransferResponse(transfer);
  }

  // 양도 게시판 목록 조회
  @Transactional(readOnly = true)
  public List<TransferResponse> getList(Long showId) {
    List<Transfer> transfers = (showId != null)
        ? transferRepository.findOpenTransfersByShowId(showId)
        : transferRepository.findOpenTransfers();
    return transfers.stream().map(TransferResponse::new).toList();
  }

  // 내가 올린 양도 글 목록
  @Transactional(readOnly = true)
  public List<TransferResponse> getMyTransfers(Long userId) {
    return transferRepository.findByFromUserIdOrderByCreatedAtDesc(userId)
        .stream().map(TransferResponse::new).toList();
  }

  // 양도 수락 — Redis SET NX로 선착순 1명만 성공
  @Transactional
  public TransferResponse claim(Long transferId, Long userId) {
    Transfer transfer = transferRepository.findById(transferId)
        .orElseThrow(() -> new BusinessException(ErrorCode.TRANSFER_NOT_FOUND));

    if (transfer.getStatus() != TransferStatus.OPEN) {
      throw new BusinessException(ErrorCode.TRANSFER_NOT_OPEN);
    }

    if (transfer.getFromUser().getId().equals(userId)) {
      throw new BusinessException(ErrorCode.TRANSFER_SELF_CLAIM);
    }

    if (transfer.getReservation().getShow().getShowDatetime().isBefore(LocalDateTime.now())) {
      throw new BusinessException(ErrorCode.TRANSFER_SHOW_ENDED);
    }

    // Redis SET NX — 동시 수락 방지
    boolean locked = transferRedisRepository.claim(transferId, userId);
    if (!locked) {
      throw new BusinessException(ErrorCode.TRANSFER_ALREADY_CLAIMED);
    }

    try {
      // 락 획득 후 예매 상태 재확인 — 락 대기 중 예매가 취소/만료됐을 수 있음
      if (transfer.getReservation().getStatus() != ReservationStatus.CONFIRMED) {
        throw new BusinessException(ErrorCode.RESERVATION_NOT_CONFIRMED);
      }

      User toUser = userRepository.findById(userId)
          .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

      transfer.claim(toUser);
      transfer.getReservation().transferTo(toUser);

      // 양도자에게 알림
      eventPublisher.publishTransferClaimed(
          new TransferEvent(transferId, transfer.getFromUser().getId(), userId)
      );
      eventPublisher.publishNotification(
          new NotificationEvent(
              transfer.getFromUser().getId(),
              "TRANSFER_CLAIMED",
              "Your ticket has been transferred."
          )
      );

      log.info("[Transfer] claimed transferId={} fromUser={} toUser={}",
          transferId, transfer.getFromUser().getId(), userId);
      return new TransferResponse(transfer);

    } catch (Exception e) {
      transferRedisRepository.release(transferId);
      throw e;
    }
  }

  // 양도 게시글 취소
  @Transactional
  public void cancel(Long transferId, Long userId) {
    Transfer transfer = transferRepository.findByIdAndFromUserId(transferId, userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.TRANSFER_NOT_FOUND));

    if (transfer.getStatus() != TransferStatus.OPEN) {
      throw new BusinessException(ErrorCode.TRANSFER_NOT_OPEN);
    }

    transfer.cancel();
    log.info("[Transfer] cancelled transferId={} userId={}", transferId, userId);
  }
}
