package com.inmobiliaria.notification_service.dto.response;

import com.inmobiliaria.notification_service.domain.InteractionType;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record InAppNotificationResponse(
    String id,
    String type,
    InteractionType interactionType,
    List<String> involvedUserIds,
    String subject,
    String content,
    boolean readStatus,
    Instant deliveredAt,
    Instant readAt,
    Map<String, Object> details) {}
