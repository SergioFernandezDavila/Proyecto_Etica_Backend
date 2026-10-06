package com.inmobiliaria.visit_calendar_service.dto;

import jakarta.validation.constraints.NotBlank;

public record SendNotificationRequest(
    @NotBlank String recipientId,
    @NotBlank String type,
    @NotBlank String subject,
    @NotBlank String content,
    String channel // opcional, por defecto "EMAIL"
    ) {}
