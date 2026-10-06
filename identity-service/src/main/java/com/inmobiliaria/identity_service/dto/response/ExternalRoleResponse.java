package com.inmobiliaria.identity_service.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ExternalRoleResponse(String id, String code, Boolean active) {}
