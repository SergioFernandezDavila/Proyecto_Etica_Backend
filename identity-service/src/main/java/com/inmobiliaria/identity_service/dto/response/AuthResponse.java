package com.inmobiliaria.identity_service.dto.response;

public record AuthResponse(
    String userId,
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresIn,
    boolean mustChangePassword,
    boolean mfaRequired) {

  public AuthResponse(
      String userId,
      String accessToken,
      String refreshToken,
      String tokenType,
      long expiresIn,
      boolean mustChangePassword) {
    this(userId, accessToken, refreshToken, tokenType, expiresIn, mustChangePassword, false);
  }
}
