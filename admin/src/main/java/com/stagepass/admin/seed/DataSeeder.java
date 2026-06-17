package com.stagepass.admin.seed;

import com.stagepass.domain.performance.*;
import com.stagepass.domain.user.User;
import com.stagepass.domain.user.UserRepository;
import com.stagepass.domain.user.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

  private final PerformanceRepository performanceRepository;
  private final ShowRepository showRepository;
  private final ZoneRepository zoneRepository;
  private final SeatRepository seatRepository;
  private final UserRepository userRepository;

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (performanceRepository.count() > 0) {
      log.info("[Seed] data already exists, skipping");
      return;
    }

    seedUsers();
    seedPerformances();
    log.info("[Seed] initial data created");
  }

  private void seedUsers() {
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    // 테스트 일반 유저
    if (!userRepository.existsByEmail("user@test.com")) {
      userRepository.save(User.builder()
          .email("user@test.com")
          .passwordHash(encoder.encode("test1234"))
          .name("테스트유저")
          .phone("010-1234-5678")
          .role(UserRole.USER)
          .build());
    }

    // 테스트 관리자
    if (!userRepository.existsByEmail("admin@test.com")) {
      userRepository.save(User.builder()
          .email("admin@test.com")
          .passwordHash(encoder.encode("admin1234"))
          .name("관리자")
          .phone("010-0000-0000")
          .role(UserRole.ADMIN)
          .build());
    }

    log.info("[Seed] users created");
  }

  private void seedPerformances() {
    List<PerformanceSeed> seeds = List.of(
        new PerformanceSeed(
            "뮤지컬 레미제라블",
            "뮤지컬",
            "빅토르 위고의 동명 소설을 원작으로 한 세계적인 뮤지컬.",
            "https://example.com/poster/les-mis.jpg",
            "LG아트센터 서울",
            "서울시 강서구 마곡중앙로 136",
            165,
            List.of(
                LocalDateTime.now().plusDays(7).withHour(14).withMinute(0).withSecond(0).withNano(0),
                LocalDateTime.now().plusDays(7).withHour(19).withMinute(0).withSecond(0).withNano(0),
                LocalDateTime.now().plusDays(8).withHour(15).withMinute(0).withSecond(0).withNano(0)
            )
        ),
        new PerformanceSeed(
            "콘서트 아이유 HEREH",
            "콘서트",
            "아이유 단독 콘서트 HEREH 월드투어 서울.",
            "https://example.com/poster/iu-hereh.jpg",
            "KSPO DOME",
            "서울시 송파구 올림픽로 424",
            120,
            List.of(
                LocalDateTime.now().plusDays(14).withHour(18).withMinute(0).withSecond(0).withNano(0),
                LocalDateTime.now().plusDays(15).withHour(18).withMinute(0).withSecond(0).withNano(0)
            )
        ),
        new PerformanceSeed(
            "연극 햄릿",
            "연극",
            "셰익스피어 4대 비극 중 하나. 국립극단 정통 연극.",
            "https://example.com/poster/hamlet.jpg",
            "명동예술극장",
            "서울시 중구 명동길 35",
            150,
            List.of(
                LocalDateTime.now().plusDays(3).withHour(19).withMinute(30).withSecond(0).withNano(0),
                LocalDateTime.now().plusDays(4).withHour(15).withMinute(0).withSecond(0).withNano(0),
                LocalDateTime.now().plusDays(5).withHour(19).withMinute(30).withSecond(0).withNano(0)
            )
        )
    );

    for (PerformanceSeed seed : seeds) {
      Performance performance = performanceRepository.save(
          Performance.builder()
              .title(seed.title())
              .genre(seed.genre())
              .description(seed.description())
              .posterUrl(seed.posterUrl())
              .venueName(seed.venueName())
              .venueAddress(seed.venueAddress())
              .runningTime(seed.runningTime())
              .build()
      );

      for (LocalDateTime showDatetime : seed.showDatetimes()) {
        int totalSeats = 150; // VIP(2x5=10) + R(5x10=50) + S(6x15=90)
        Show show = showRepository.save(
            Show.builder()
                .performance(performance)
                .showDatetime(showDatetime)
                .totalSeats(totalSeats)
                .status(ShowStatus.ON_SALE)
                .build()
        );
        createZonesAndSeats(show);
      }
      log.info("[Seed] performance created '{}' - {} shows", seed.title(), seed.showDatetimes().size());
    }
  }

  private void createZonesAndSeats(Show show) {
    record ZoneDef(String name, String grade, int price, int rows, int cols) {}

    List<ZoneDef> zoneDefs = List.of(
        new ZoneDef("VIP석", "VIP", 150000, 2, 5),
        new ZoneDef("R석",   "R",   110000, 5, 10),
        new ZoneDef("S석",   "S",    80000, 6, 15)
    );

    List<Seat> batch = new ArrayList<>();
    for (ZoneDef def : zoneDefs) {
      Zone zone = zoneRepository.save(
          Zone.builder()
              .show(show)
              .name(def.name())
              .grade(def.grade())
              .price(def.price())
              .rowCount(def.rows())
              .colCount(def.cols())
              .build()
      );

      for (int row = 1; row <= def.rows(); row++) {
        for (int col = 1; col <= def.cols(); col++) {
          batch.add(Seat.builder()
              .zone(zone)
              .seatCode(String.format("%s-%02d-%02d", def.grade(), row, col))
              .rowNum(row)
              .colNum(col)
              .build());
        }
      }
    }
    seatRepository.saveAll(batch);
  }

  private record PerformanceSeed(
      String title,
      String genre,
      String description,
      String posterUrl,
      String venueName,
      String venueAddress,
      Integer runningTime,
      List<LocalDateTime> showDatetimes
  ) {}
}
