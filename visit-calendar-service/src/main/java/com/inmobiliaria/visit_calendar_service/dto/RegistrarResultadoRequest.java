package com.inmobiliaria.visit_calendar_service.dto;

import jakarta.validation.constraints.NotBlank;

public record RegistrarResultadoRequest(
    @NotBlank String resultado, // "INTERESADO", "NO_INTERESADO", "PENDIENTE"
    String observaciones,
    Double mileage) {}
