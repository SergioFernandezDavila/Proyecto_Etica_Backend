package com.inmobiliaria.identity_service.dto.response;

public record AuthResponse(
    String userId,
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresIn,
    boolean mustChangePassword,
    boolean requiresTwoFactor,
    String preAuthToken) {

  public AuthResponse(
      String userId,
      String accessToken,
      String refreshToken,
      String tokenType,
      long expiresIn,
      boolean mustChangePassword) {
    this(userId, accessToken, refreshToken, tokenType, expiresIn, mustChangePassword, false, null);
  }

  public static AuthResponse requires2fa(String preAuthToken) {
    return new AuthResponse(null, null, null, null, 0, false, true, preAuthToken);
  }
}
