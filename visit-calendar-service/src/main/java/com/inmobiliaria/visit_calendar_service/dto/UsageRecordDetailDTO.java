package com.inmobiliaria.visit_calendar_service.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageRecordDetailDTO {
  private String visitId;
  private Instant date;
  private double durationHours;
  private double mileage;
}
