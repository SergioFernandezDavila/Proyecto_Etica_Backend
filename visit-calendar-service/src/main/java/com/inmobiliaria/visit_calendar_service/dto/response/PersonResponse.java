// backend/visit-calendar-service/src/main/java/.../dto/response/PersonResponse.java
package com.inmobiliaria.visit_calendar_service.dto.response;

import java.time.LocalDate;
import java.util.List;

public record PersonResponse(
    String id,
    String authUserId,
    String firstName,
    String lastName,
    String fullName,
    LocalDate birthDate,
    String phone,
    String email,
    String personType, // antes era PersonType, ahora String
    List<String> roleIds,
    boolean customRole,

    // Employee-specific
    String department,
    String position,
    LocalDate hireDate,

    // Owner-specific
    String taxId,
    String address,
    List<String> propertyIds,

    // InterestedClient-specific
    String preferredContactMethod,
    String budget,

    // Nuevas preferencias
    String preferredZone,
    String preferredPropertyType,
    Integer preferredRooms) {}
