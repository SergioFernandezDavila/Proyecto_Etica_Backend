package com.inmobiliaria.access_control_service.dto.request;

import com.inmobiliaria.access_control_service.domain.AccessType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record RoleMatrixBatchUpdateRequest(@NotNull List<RoleMatrixRowUpdate> updates) {

  public record RoleMatrixRowUpdate(
      @NotBlank String roleId, @NotNull List<RoleModuleAccessUpdate> modules) {}

  public record RoleModuleAccessUpdate(
      @NotBlank String resource, AccessType accessType, List<String> actions) {}
}
