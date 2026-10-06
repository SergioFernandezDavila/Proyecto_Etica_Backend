package com.inmobiliaria.property_service.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SendPdfEmailRequest(@NotBlank @Email String destinationEmail, String message) {}
