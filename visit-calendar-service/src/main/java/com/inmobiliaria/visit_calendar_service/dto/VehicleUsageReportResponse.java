package com.inmobiliaria.visit_calendar_service.dto;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleUsageReportResponse {
  private Instant from;
  private Instant to;
  private List<VehicleUsageSummaryDTO> vehicles;
}
