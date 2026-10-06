package com.inmobiliaria.operation_service.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptResponse {

  private String id;
  private String operationId;
  private BigDecimal amount;
  private String currency;
  private LocalDateTime paymentDate;
  private String concept;
  private String fileName;
  private String contentType;
  private Long size;
  private String createdBy;
  private LocalDateTime createdAt;

  /** Pre-signed MinIO URL — frontend uses this to download or preview the file. */
  private String downloadUrl;
}
