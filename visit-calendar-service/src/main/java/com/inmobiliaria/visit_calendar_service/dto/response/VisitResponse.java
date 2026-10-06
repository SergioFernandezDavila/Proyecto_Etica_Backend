// VisitResponse.java
package com.inmobiliaria.visit_calendar_service.dto.response;

import com.inmobiliaria.visit_calendar_service.model.Visit.EventStatus;
import com.inmobiliaria.visit_calendar_service.model.Visit.ResultadoVisita;
import java.time.Instant;

public record VisitResponse(
    String id,
    String propertyId,
    String propertyName,
    String clientId,
    String clientName,
    String agentId,
    String agentName,
    Instant startTime,
    Instant endTime,
    EventStatus status,
    ResultadoVisita resultado,
    String observaciones,
    Instant fechaRegistroResultado) {}
