package com.inmobiliaria.operation_service.domain;

import java.time.LocalDateTime;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "receipts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReceiptDocument extends BaseDocument {
  @Id private String id;
  private String operationId;
  private String fileName;
  private String contentType;
  private Long size;
  private String minioObjectPath;
  private Double amount;
  private String currency;
  private String notes;
  private LocalDateTime paymentDate;
}
