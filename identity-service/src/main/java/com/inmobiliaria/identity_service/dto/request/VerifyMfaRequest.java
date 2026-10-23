package com.inmobiliaria.identity_service.dto.request;

import jakarta.validation.constraints.NotBlank;

public record VerifyMfaRequest(@NotBlank String email, @NotBlank String mfaCode) {}
