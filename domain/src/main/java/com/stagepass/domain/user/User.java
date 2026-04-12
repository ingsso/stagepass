package com.stagepass.domain.user;

import com.stagepass.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String email;

  private String passwordHash;

  @Column(nullable = false)
  private String name;

  private String phone;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private UserRole role;

  private String provider;       // kakao
  private String providerId;

  @Builder
  public User(String email, String passwordHash, String name, String phone,
              UserRole role, String provider, String providerId) {
    this.email = email;
    this.passwordHash = passwordHash;
    this.name = name;
    this.phone = phone;
    this.role = role;
    this.provider = provider;
    this.providerId = providerId;
  }
}