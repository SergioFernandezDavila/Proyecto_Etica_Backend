// backend/visit-calendar-service/src/main/java/.../dto/response/PropertyResponse.java
package com.inmobiliaria.visit_calendar_service.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record PropertyResponse(
    String id,
    String title,
    String address,
    String zone,
    Double price,
    String type,
    String operationType, // antes OperationType
    Double m2,
    Integer rooms,
    String status,
    String assignedAgentId,
    String agentName,
    String ownerId,
    List<String> imageUrls,
    List<AssignmentHistoryRecord> assignmentHistory, // record local
    List<PriceHistoryRecord> priceHistory, // record local
    List<StatusHistoryRecord> statusHistory, // record local
    Set<String> accessPolicy,
    Double latitude,
    Double longitude,
    String motivoRetiro, // antes RetirementReason
    String detalleRetiro) {}

// Records auxiliares para los históricos (definidos dentro del mismo archivo)
record AssignmentHistoryRecord(String agentId, Instant assignedAt, String assignedBy) {}

record PriceHistoryRecord(Double oldPrice, Double newPrice, Instant changedAt, String changedBy) {}

record StatusHistoryRecord(
    String oldStatus, String newStatus, Instant changedAt, String changedBy) {}
