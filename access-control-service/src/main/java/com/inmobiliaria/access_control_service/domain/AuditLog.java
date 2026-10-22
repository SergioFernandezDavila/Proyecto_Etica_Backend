package com.inmobiliaria.access_control_service.domain;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "access_control_audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {
  @Id private String id;
  private String action; // ROLE_CREATE, ROLE_UPDATE, ROLE_DELETE, ROLE_MATRIX_UPDATE
  private String roleId;
  private String roleCode;
  private String performedBy;
  private String performedByEmail;
  private String performedByName;
  private Instant timestamp;
  private String details;
  private String ipAddress;
}
