package com.inmobiliaria.identity_service.dto.response;

import com.inmobiliaria.identity_service.domain.UserStatus;
import com.inmobiliaria.identity_service.domain.UserType;
import java.time.Instant;
import java.util.List;

public record UserResponse(
    String id,
    String firstName,
    String lastName,
    String fullName,
    String email,
    UserType userType,
    UserStatus status,
    Boolean temporaryPassword,
    Instant temporaryPasswordExpiresAt,
    Boolean mustChangePassword,
    List<String> primaryRoleIds,
    String assignedAgentId,
    Boolean mfaEnabled,
    Integer failedLoginAttempts,
    Instant lockedUntil,
    Instant passwordExpiresAt) {

  public UserResponse(
      String id,
      String firstName,
      String lastName,
      String fullName,
      String email,
      UserType userType,
      UserStatus status,
      Boolean temporaryPassword,
      Instant temporaryPasswordExpiresAt,
      Boolean mustChangePassword,
      List<String> primaryRoleIds,
      String assignedAgentId) {
    this(
        id,
        firstName,
        lastName,
        fullName,
        email,
        userType,
        status,
        temporaryPassword,
        temporaryPasswordExpiresAt,
        mustChangePassword,
        primaryRoleIds,
        assignedAgentId,
        false,
        0,
        null,
        null);
  }
}
