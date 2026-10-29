package com.inmobiliaria.identity_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TwoFactorVerifyRequest(
    @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "Code must be 6 digits") String code) {}
