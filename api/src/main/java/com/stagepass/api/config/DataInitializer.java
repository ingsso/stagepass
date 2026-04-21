package com.stagepass.api.config;

import com.stagepass.domain.user.User;
import com.stagepass.domain.user.UserRepository;
import com.stagepass.domain.user.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 로컬 개발 환경 초기 데이터 시드
 * - admin@stagepass.test / Admin1234! (ROLE_ADMIN)
 */
@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  @Override
  public void run(String... args) {
    createAdminIfAbsent();
  }

  private void createAdminIfAbsent() {
    String adminEmail = "admin@stagepass.test";
    if (userRepository.findByEmail(adminEmail).isPresent()) {
      log.info("[DataInitializer] 어드민 계정 이미 존재 — 건너뜀");
      return;
    }
    User admin = User.builder()
        .email(adminEmail)
        .passwordHash(passwordEncoder.encode("Admin1234!"))
        .name("시스템관리자")
        .phone("010-0000-0000")
        .role(UserRole.ADMIN)
        .build();
    userRepository.save(admin);
    log.info("[DataInitializer] 어드민 계정 생성 완료: {}", adminEmail);
  }
}
