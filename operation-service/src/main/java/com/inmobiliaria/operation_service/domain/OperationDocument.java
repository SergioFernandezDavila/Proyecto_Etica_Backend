package com.inmobiliaria.operation_service.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "operations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OperationDocument extends BaseDocument {
  @Id private String id;

  // Property Snapshot
  private String propertyId;
  private String propertyName;
  private String propertyType;
  private String operationType;

  // Financial Snapshot
  private Double finalPrice;
  private String currency;

  // Participants Snapshot
  private String clientId;
  private String clientName;
  private String agentId;
  private String agentName;
  private String ownerId;
  private String ownerName;

  // Business Context
  private String department;
  private String status;
  private String notes;
  private LocalDateTime closureDate;

  @Builder.Default private List<OperationStatusHistory> statusHistory = new ArrayList<>();
}
