package com.inmobiliaria.identity_service.dto.response;

public record AuthResponse(
    String userId,
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresIn,
    boolean mustChangePassword) {}
