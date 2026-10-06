package com.inmobiliaria.visit_calendar_service.dto.request;

import com.inmobiliaria.visit_calendar_service.domain.InteractionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

public record SendInAppNotificationRequest(
    @NotBlank String recipientId,
    @NotBlank String type,
    @NotNull InteractionType interactionType,
    List<String> involvedUserIds,
    String subject,
    @NotBlank String content,
    Map<String, Object> details) {}
