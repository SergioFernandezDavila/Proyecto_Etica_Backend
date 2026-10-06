package com.inmobiliaria.property_service.domain;

import java.time.Instant;
import java.util.List;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "property_audit_logs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {
  @Id private String id;
  private String userId;
  private String action;
  private String propertyId;
  private String previousValue;
  private String newValue;
  private List<FieldChange> changes;
  private Instant timestamp;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class FieldChange {
    private String field;
    private String oldValue;
    private String newValue;
  }
}
