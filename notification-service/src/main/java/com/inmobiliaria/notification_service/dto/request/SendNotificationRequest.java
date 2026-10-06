package com.inmobiliaria.notification_service.dto.request;

import jakarta.validation.constraints.NotBlank;

public record SendNotificationRequest(
    @NotBlank String recipientId,
    @NotBlank String type,
    @NotBlank String subject,
    @NotBlank String content,
    String channel // opcional, por defecto "EMAIL"
    ) {}
