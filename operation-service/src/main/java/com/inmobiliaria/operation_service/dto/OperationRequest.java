package com.inmobiliaria.operation_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationRequest {
  @NotBlank(message = "Property ID is required")
  private String propertyId;

  private String propertyName;
  private String propertyType;
  private String operationType;

  @NotNull(message = "Final price is required")
  private Double finalPrice;

  @NotBlank(message = "Currency is required")
  private String currency;

  @NotBlank(message = "Client ID is required")
  private String clientId;

  private String clientName;

  @NotBlank(message = "Agent ID is required")
  private String agentId;

  private String agentName;
  private String department;

  private String ownerId;
  private String ownerName;

  @NotBlank(message = "Status is required")
  private String status;

  private String notes;
  private LocalDateTime closureDate;
}
