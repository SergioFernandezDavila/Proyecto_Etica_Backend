package com.inmobiliaria.user_service.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import java.util.List;

public record SearchPreferencesRequest(
    List<String> preferredZones,
    @Min(value = 0, message = "Min rooms must be at least 0") Integer minRooms,
    @Min(value = 0, message = "Max rooms must be at least 0") Integer maxRooms,
    @DecimalMin(value = "0.0", message = "Max price must be at least 0") Double maxPrice,
    String preferredPropertyType) {}
