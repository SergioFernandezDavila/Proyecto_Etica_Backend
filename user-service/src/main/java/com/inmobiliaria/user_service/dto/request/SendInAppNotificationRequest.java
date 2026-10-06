package com.inmobiliaria.user_service.dto.request;

import java.util.List;
import java.util.Map;

public record SendInAppNotificationRequest(
    String recipientId,
    String type,
    String interactionType, // Opcional, pero se puede usar "INTERES"
    List<String> involvedUserIds,
    String subject,
    String content,
    Map<String, Object> details) {}
