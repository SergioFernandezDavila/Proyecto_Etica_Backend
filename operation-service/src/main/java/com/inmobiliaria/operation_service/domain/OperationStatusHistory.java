package com.inmobiliaria.operation_service.domain;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationStatusHistory {
  private String oldStatus;
  private String newStatus;
  private Instant changedAt;
  private String changedBy;
  private String notes;
}
