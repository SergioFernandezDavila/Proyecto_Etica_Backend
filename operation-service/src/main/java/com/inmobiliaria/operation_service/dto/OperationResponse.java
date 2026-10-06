package com.inmobiliaria.operation_service.dto;

import com.inmobiliaria.operation_service.domain.OperationStatusHistory;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationResponse {
  private String id;
  private String propertyId;
  private String propertyName;
  private String propertyType;
  private String operationType;
  private Double finalPrice;
  private String currency;
  private String clientId;
  private String clientName;
  private String agentId;
  private String agentName;
  private String ownerId;
  private String ownerName;
  private String department;
  private String status;
  private String notes;
  private LocalDateTime closureDate;
  private LocalDateTime createdAt;
  private List<OperationStatusHistory> statusHistory;
}
