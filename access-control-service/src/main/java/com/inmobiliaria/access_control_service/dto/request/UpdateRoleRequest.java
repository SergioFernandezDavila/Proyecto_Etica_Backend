package com.inmobiliaria.access_control_service.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UpdateRoleRequest(
    @NotBlank String name,
    @NotBlank String description,
    @NotNull List<@Valid PermissionRequest> permissions,
    Boolean active) {}
