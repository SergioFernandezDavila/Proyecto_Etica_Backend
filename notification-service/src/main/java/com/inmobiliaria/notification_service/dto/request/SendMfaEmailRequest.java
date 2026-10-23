package com.inmobiliaria.notification_service.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SendMfaEmailRequest(
    @NotBlank @Email String to, String fullName, @NotBlank String code) {}
