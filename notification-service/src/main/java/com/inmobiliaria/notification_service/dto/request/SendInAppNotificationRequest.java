package com.inmobiliaria.notification_service.dto.request;

import com.inmobiliaria.notification_service.domain.InteractionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

public record SendInAppNotificationRequest(
    @NotBlank String recipientId, // ID del usuario destino
    @NotBlank String type, // tipo de notificación (ej. "VISIT_SCHEDULED")
    @NotNull InteractionType interactionType,
    List<String> involvedUserIds, // opcional, otros usuarios implicados
    String subject, // opcional, título
    @NotBlank String content,
    Map<String, Object> details // opcional, datos adicionales
    ) {}
