package com.inmobiliaria.access_control_service.dto.request;

import com.inmobiliaria.access_control_service.domain.AccessType;
import com.inmobiliaria.access_control_service.domain.ScopeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PermissionRequest(
    @NotBlank String resource,
    @NotBlank String action,
    @NotNull ScopeType scope,
    AccessType accessType) {

  public PermissionRequest(String resource, String action, ScopeType scope) {
    this(resource, action, scope, AccessType.RBAC);
  }
}
