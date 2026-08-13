package com.stagepass.notification.redis;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationMessage {
  private Long userId;
  private String type;
  private String message;
}
