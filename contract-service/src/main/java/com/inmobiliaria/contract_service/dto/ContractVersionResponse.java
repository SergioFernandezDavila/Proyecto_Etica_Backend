package com.inmobiliaria.contract_service.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractVersionResponse {

  private String id;
  private String operationId;
  private Integer versionNumber;
  private String content;
  private String title;
  private String changeDescription;
  private String authorId;
  private String authorName;
  private String authorRole;
  private LocalDateTime createdAt;
  private String status;
}
