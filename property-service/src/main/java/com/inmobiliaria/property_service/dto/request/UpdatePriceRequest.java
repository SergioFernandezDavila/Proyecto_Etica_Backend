package com.inmobiliaria.property_service.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record UpdatePriceRequest(
    @NotNull @Positive(message = "El precio debe ser mayor a cero") Double newPrice) {}
