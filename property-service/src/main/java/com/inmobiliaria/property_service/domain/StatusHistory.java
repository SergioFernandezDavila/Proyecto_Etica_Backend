package com.inmobiliaria.property_service.domain;

import java.time.Instant;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StatusHistory {
  private String oldStatus;
  private String newStatus;
  private Instant changedAt;
  private String changedBy;
}
