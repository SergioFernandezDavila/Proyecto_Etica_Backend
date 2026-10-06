package com.inmobiliaria.property_service.domain;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AssignmentHistory {
  private String agentId;
  private Instant assignedAt;
  private String assignedBy;
}
