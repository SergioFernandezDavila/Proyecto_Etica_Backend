package com.inmobiliaria.identity_service.domain;

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
