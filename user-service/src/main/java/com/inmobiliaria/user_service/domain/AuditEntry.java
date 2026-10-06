package com.inmobiliaria.user_service.domain;

import java.time.Instant;
import java.util.List;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEntry {
  private Instant changedAt;
  private String changedBy; // authUserId del editor
  private List<FieldChange> changes;

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class FieldChange {
    private String field;
    private String oldValue;
    private String newValue;
  }
}
