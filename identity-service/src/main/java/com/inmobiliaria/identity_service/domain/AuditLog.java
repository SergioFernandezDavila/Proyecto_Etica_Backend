package com.inmobiliaria.identity_service.domain;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "identity_audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {
  @Id private String id;
  private String action; // "USER_CREATE", "USER_DEACTIVATE", "USER_REACTIVATE", "USER_DELETE",
  // "USER_ROLE_ASSIGN"
  private String userId; // ID del usuario afectado
  private String userEmail; // Email del usuario afectado
  private String performedBy; // ID del admin/agente que realizó la acción
  private String performedByEmail;
  private String performedByName;
  private Instant timestamp;
  private String details;
  private String ipAddress; // Opcional: para auditoría más completa
}
