package com.stagepass.admin.performance.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ZoneRequest {
  private String name;
  private String grade;
  private Integer price;
  private Integer rowCount;
  private Integer colCount;
}