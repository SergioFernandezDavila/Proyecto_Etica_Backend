package com.inmobiliaria.visit_calendar_service.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleUsageSummaryDTO {
  private String vehicleId;
  private String licensePlate;
  private String brand;
  private String model;
  private double totalHours;
  private int visitCount;
  private double totalMileage;
  private List<UsageRecordDetailDTO> details;
}
