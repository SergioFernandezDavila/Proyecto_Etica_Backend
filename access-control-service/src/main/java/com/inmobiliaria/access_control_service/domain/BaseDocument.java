package com.inmobiliaria.access_control_service.domain;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class BaseDocument {

  private Instant createdAt;
  private Instant updatedAt;
  private String createdBy;
}
