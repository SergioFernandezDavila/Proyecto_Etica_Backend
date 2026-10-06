package com.inmobiliaria.user_service.domain;

import java.time.Instant;
import java.util.List;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "person_audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogDocument {
  @Id private String id;
  private Instant timestamp;
  private String changedBy;
  private String action; // "CREATED" | "UPDATED"
  private String personId;
  private String personName;
  private String personType;
  private List<AuditEntry.FieldChange> changes;
}
